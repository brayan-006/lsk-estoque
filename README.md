# LSK Estoque — Backend (Java 17 / Spring Boot 3.5)

Gestão de estoque com baixa **FEFO (fallback PEPS)**, **Curva ABC**, **estoque mínimo**, mensageria **RabbitMQ**
e integração com **SAP Business One (Service Layer)**.

## Subindo o ambiente

```bash
docker compose up -d                       # PostgreSQL 16 + RabbitMQ (painel em :15672, guest/guest)
mvn spring-boot:run                        # Flyway cria o schema e a função pr_dar_baixa_fefo
psql -h localhost -U lsk -d lsk_estoque -f src/main/resources/db/dev/dev-seed.sql   # dados de exemplo (senha: lsk)
mvn test                                   # teste de integração da baixa FEFO (Testcontainers, requer Docker)
```

## Endpoints

```bash
# Posição em tempo real (saldo + lotes na ordem em que serão consumidos)
curl http://localhost:8080/api/v1/estoque/TONER-HP-85A

# Entrada de NF-e (idempotente pela chave de acesso; reenvio => 409)
curl -X POST http://localhost:8080/api/v1/estoque/entradas/nfe -H 'Content-Type: application/json' -d '{
  "chaveAcesso": "35260112345678000190550010000012341000012345",
  "numero": "1234",
  "itens": [{ "sku": "TONER-HP-85A", "codigoLote": "L-2026-C", "quantidade": 20,
              "custoUnitario": 119.50, "dataFabricacao": "2026-09-01", "dataValidade": "2027-09-01" }]
}'

# Produtos por classe da Curva ABC
curl 'http://localhost:8080/api/v1/produtos?classe=A'

# Recalcular a Curva ABC sob demanda (o job noturno já faz)
curl -X POST http://localhost:8080/api/v1/estoque/curva-abc/recalcular
```

## Publicando um evento de venda

No painel do RabbitMQ (Exchanges → `lsk.estoque` → Publish message), routing key `venda.confirmada`,
header `content_type = application/json`:

```json
{
  "eventId": "7b0c7a62-5d6a-4b0e-9a55-2f1d1d3f6a10",
  "pedido": "PED-1001",
  "ocorridoEm": "2026-10-08T14:30:00Z",
  "itens": [{ "sku": "TONER-HP-85A", "quantidade": 7, "precoUnitario": 189.90 }]
}
```

Publicar duas vezes o mesmo `eventId` baixa o estoque **uma vez só**. Erros de negócio (SKU inexistente, saldo
insuficiente) e payload inválido vão para `lsk.estoque.vendas.dlq`.

## Integração SAP B1

Desligada por padrão (`lsk.sap.enabled=false`; as movimentações ficam `PENDENTE`). Para ligar:

```bash
SAP_ENABLED=true SAP_URL=https://servidor:50000 SAP_COMPANY_DB=SBO_LSK SAP_USER=... SAP_PASSWORD=... \
SAP_WAREHOUSE=01 mvn spring-boot:run
```

- `GoodsIssue` → `POST /b1s/v1/InventoryGenExits`; `GoodsReceipt` → `POST /b1s/v1/InventoryGenEntries`.
- Os lotes são enviados em `BatchNumbers` (desligue com `lsk.sap.send-batches=false` se o item não tem gestão por lote).
- `ItemCode` = `produtos.sap_item_code` (ou o SKU, se nulo).
- Idempotência no SAP: o documento leva `Reference2 = VENDA-<eventId>` / `NFE-<chave>` e, antes de criar, consulta se já existe.
- Certificado autoassinado em DEV: `SAP_INSECURE_SSL=true` (ou importe o certificado no truststore da JVM).

## Decisões de projeto

| Tema | Decisão |
|---|---|
| FEFO | Fonte única da regra: função PL/pgSQL `pr_dar_baixa_fefo` (trava produto e lotes, baixa e grava movimentações atomicamente). Ordem: lotes com validade (vence primeiro sai primeiro; empate por entrada) → lotes sem validade por entrada (PEPS). Vencidos/bloqueados nunca saem. |
| Idempotência | Tabela `eventos_processados` com `INSERT ... ON CONFLICT DO NOTHING` na **mesma transação** da baixa. |
| SAP | Outbox na própria `movimentacoes` (`sap_status`): grava → commit → envia assíncrono → job reenvia `PENDENTE/ERRO` (até 5 tentativas). Uma falha do SAP nunca desfaz a baixa. |
| Concorrência | `FOR UPDATE` no produto e nos lotes; SKUs processados em ordem alfabética para evitar deadlock. |
| Curva ABC | Faturamento (preço × qtd das saídas `VENDA`) na janela de 90 dias, giro como desempate. A ≤ 80 %, B ≤ 95 %, C restante e sem vendas. Job às 02:30. |
| Estoque mínimo | Avaliado a cada entrada/baixa e por varredura a cada 15 min. Um alerta aberto por produto; vira `resolvido` quando o saldo se recupera. Também publicado em `lsk.estoque.alertas.q`. |

## Estrutura

```
domain/        entidades JPA, enums, exceções e portas de repositório
application/   serviços de caso de uso, DTOs, eventos, porta ErpGateway
infrastructure/ adaptadores: JDBC (procedure), SAP B1, RabbitMQ, jobs
web/           StockController + tratamento de erros (RFC 9457)
```
