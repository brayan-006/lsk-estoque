-- =====================================================================
-- LSK Technologies - Gestão de Estoque | Schema inicial (PostgreSQL 14+)
-- =====================================================================

CREATE TABLE categorias (
    id          BIGSERIAL    PRIMARY KEY,
    nome        VARCHAR(80)  NOT NULL,
    descricao   VARCHAR(255),
    criado_em   TIMESTAMPTZ  NOT NULL DEFAULT now(),
    CONSTRAINT uk_categorias_nome UNIQUE (nome)
);

CREATE TABLE produtos (
    id                BIGSERIAL     PRIMARY KEY,
    sku               VARCHAR(60)   NOT NULL,
    descricao         VARCHAR(200)  NOT NULL,
    categoria_id      BIGINT        NOT NULL REFERENCES categorias (id),
    unidade           VARCHAR(10)   NOT NULL DEFAULT 'UN',
    estoque_minimo    NUMERIC(14,3) NOT NULL DEFAULT 0,
    controla_validade BOOLEAN       NOT NULL DEFAULT TRUE,
    custo_medio       NUMERIC(14,4) NOT NULL DEFAULT 0,
    preco_venda       NUMERIC(14,4) NOT NULL DEFAULT 0,
    classe_abc        VARCHAR(1)    NOT NULL DEFAULT 'C',
    sap_item_code     VARCHAR(50),
    ativo             BOOLEAN       NOT NULL DEFAULT TRUE,
    criado_em         TIMESTAMPTZ   NOT NULL DEFAULT now(),
    atualizado_em     TIMESTAMPTZ   NOT NULL DEFAULT now(),
    CONSTRAINT uk_produtos_sku UNIQUE (sku),
    CONSTRAINT ck_produtos_minimo CHECK (estoque_minimo >= 0),
    CONSTRAINT ck_produtos_classe CHECK (classe_abc IN ('A', 'B', 'C'))
);
CREATE INDEX ix_produtos_classe ON produtos (classe_abc) WHERE ativo;
CREATE INDEX ix_produtos_categoria ON produtos (categoria_id);

CREATE TABLE lotes (
    id                 BIGSERIAL     PRIMARY KEY,
    produto_id         BIGINT        NOT NULL REFERENCES produtos (id),
    codigo_lote        VARCHAR(60)   NOT NULL,
    data_fabricacao    DATE,
    data_validade      DATE,
    data_entrada       TIMESTAMPTZ   NOT NULL DEFAULT now(),
    quantidade_inicial NUMERIC(14,3) NOT NULL,
    quantidade_atual   NUMERIC(14,3) NOT NULL,
    custo_unitario     NUMERIC(14,4) NOT NULL DEFAULT 0,
    nfe_chave          VARCHAR(44),
    status             VARCHAR(10)   NOT NULL DEFAULT 'ATIVO',
    CONSTRAINT uk_lotes_produto_codigo UNIQUE (produto_id, codigo_lote),
    CONSTRAINT ck_lotes_qtd_inicial CHECK (quantidade_inicial > 0),
    CONSTRAINT ck_lotes_qtd_atual CHECK (quantidade_atual >= 0),
    CONSTRAINT ck_lotes_status CHECK (status IN ('ATIVO', 'ESGOTADO', 'VENCIDO', 'BLOQUEADO')),
    CONSTRAINT ck_lotes_datas CHECK (data_fabricacao IS NULL OR data_validade IS NULL OR data_validade >= data_fabricacao)
);
-- Índice que sustenta a ordenação FEFO / PEPS
CREATE INDEX ix_lotes_fefo ON lotes (produto_id, data_validade NULLS LAST, data_entrada, id)
    WHERE status = 'ATIVO' AND quantidade_atual > 0;

CREATE TABLE movimentacoes (
    id                BIGSERIAL     PRIMARY KEY,
    produto_id        BIGINT        NOT NULL REFERENCES produtos (id),
    lote_id           BIGINT        NOT NULL REFERENCES lotes (id),
    tipo              VARCHAR(10)   NOT NULL,
    quantidade        NUMERIC(14,3) NOT NULL,
    custo_unitario    NUMERIC(14,4) NOT NULL DEFAULT 0,
    preco_unitario    NUMERIC(14,4),
    referencia        VARCHAR(100)  NOT NULL,
    origem            VARCHAR(30)   NOT NULL,
    documento_origem  VARCHAR(60),
    criterio          VARCHAR(10),
    sap_status        VARCHAR(10)   NOT NULL DEFAULT 'PENDENTE',
    sap_doc_entry     BIGINT,
    sap_erro          VARCHAR(500),
    sap_tentativas    INTEGER       NOT NULL DEFAULT 0,
    sap_atualizado_em TIMESTAMPTZ,
    criado_em         TIMESTAMPTZ   NOT NULL DEFAULT now(),
    CONSTRAINT ck_mov_tipo CHECK (tipo IN ('ENTRADA', 'SAIDA', 'AJUSTE')),
    CONSTRAINT ck_mov_qtd CHECK (quantidade > 0),
    CONSTRAINT ck_mov_criterio CHECK (criterio IS NULL OR criterio IN ('FEFO', 'PEPS')),
    CONSTRAINT ck_mov_sap CHECK (sap_status IN ('PENDENTE', 'ENVIANDO', 'ENVIADO', 'ERRO'))
);
CREATE INDEX ix_mov_produto_data ON movimentacoes (produto_id, criado_em);
CREATE INDEX ix_mov_referencia ON movimentacoes (referencia, tipo);
CREATE INDEX ix_mov_sap_pendente ON movimentacoes (sap_status) WHERE sap_status <> 'ENVIADO';
CREATE INDEX ix_mov_vendas_abc ON movimentacoes (criado_em)
    WHERE tipo = 'SAIDA' AND origem = 'VENDA';

CREATE TABLE alertas_estoque (
    id               BIGSERIAL     PRIMARY KEY,
    produto_id       BIGINT        NOT NULL REFERENCES produtos (id),
    tipo             VARCHAR(30)   NOT NULL,
    saldo_no_momento NUMERIC(14,3) NOT NULL,
    estoque_minimo   NUMERIC(14,3) NOT NULL,
    mensagem         VARCHAR(300)  NOT NULL,
    resolvido        BOOLEAN       NOT NULL DEFAULT FALSE,
    criado_em        TIMESTAMPTZ   NOT NULL DEFAULT now(),
    resolvido_em     TIMESTAMPTZ,
    CONSTRAINT ck_alerta_tipo CHECK (tipo IN ('ESTOQUE_MINIMO'))
);
-- No máximo um alerta aberto por produto/tipo
CREATE UNIQUE INDEX uk_alerta_aberto ON alertas_estoque (produto_id, tipo) WHERE NOT resolvido;

-- Controle de idempotência (eventos de venda e NF-e)
CREATE TABLE eventos_processados (
    chave         VARCHAR(100) PRIMARY KEY,
    tipo          VARCHAR(20)  NOT NULL,
    processado_em TIMESTAMPTZ  NOT NULL DEFAULT now()
);
