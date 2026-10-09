package br.com.lsk.estoque.infrastructure.sap;

import br.com.lsk.estoque.application.port.DocumentoEstoque;
import br.com.lsk.estoque.application.port.ErpGateway;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.JsonNode;
import jakarta.annotation.PreDestroy;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;

import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLEngine;
import javax.net.ssl.TrustManager;
import javax.net.ssl.X509ExtendedTrustManager;
import java.math.BigDecimal;
import java.net.Socket;
import java.net.http.HttpClient;
import java.security.GeneralSecurityException;
import java.security.SecureRandom;
import java.security.cert.X509Certificate;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.concurrent.locks.ReentrantLock;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Cliente do SAP Business One Service Layer.
 *
 * <ul>
 *   <li>Autentica em POST /b1s/v1/Login e mantém o cookie B1SESSION (+ ROUTEID, se houver balanceador);</li>
 *   <li>renova a sessão antes de expirar e, em caso de 401, refaz o login e repete a chamada uma vez;</li>
 *   <li>GoodsIssue  -> entidade InventoryGenExits  (Saída de Mercadorias, OIGE);</li>
 *   <li>GoodsReceipt -> entidade InventoryGenEntries (Entrada de Mercadorias, OIGN);</li>
 *   <li>idempotência: antes de criar, procura documento com o mesmo Reference2 (= referência do evento).</li>
 * </ul>
 */
@Slf4j
@Service
public class SapService implements ErpGateway {

    private static final String LOGIN = "/b1s/v1/Login";
    private static final String LOGOUT = "/b1s/v1/Logout";
    private static final String GOODS_ISSUE = "/b1s/v1/InventoryGenExits";
    private static final String GOODS_RECEIPT = "/b1s/v1/InventoryGenEntries";

    // ---- payloads do Service Layer ----
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record Document(@JsonProperty("DocDate") String docDate,
                           @JsonProperty("Reference2") String reference2,
                           @JsonProperty("Comments") String comments,
                           @JsonProperty("DocumentLines") List<Line> lines) {
    }

    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record Line(@JsonProperty("ItemCode") String itemCode,
                       @JsonProperty("Quantity") BigDecimal quantity,
                       @JsonProperty("WarehouseCode") String warehouseCode,
                       @JsonProperty("UnitPrice") BigDecimal unitPrice,
                       @JsonProperty("BatchNumbers") List<Batch> batches) {
    }

    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record Batch(@JsonProperty("BatchNumber") String batchNumber,
                        @JsonProperty("Quantity") BigDecimal quantity,
                        @JsonProperty("ManufacturingDate") String manufacturingDate,
                        @JsonProperty("ExpiryDate") String expiryDate) {
    }

    private record Session(String cookie, Instant expiresAt) {
        boolean valid() {
            return Instant.now().isBefore(expiresAt);
        }
    }

    private final SapProperties props;
    private final RestClient http;
    private final ReentrantLock sessionLock = new ReentrantLock();
    private volatile Session session;

    public SapService(SapProperties props) {
        this.props = props;

        HttpClient.Builder client = HttpClient.newBuilder()
                .version(HttpClient.Version.HTTP_1_1)
                .connectTimeout(props.connectTimeout());
        if (props.insecureSsl()) {
            log.warn("lsk.sap.insecure-ssl=true: validação de certificado DESLIGADA (use apenas em desenvolvimento)");
            client.sslContext(sslContextInseguro());
        }
        JdkClientHttpRequestFactory factory = new JdkClientHttpRequestFactory(client.build());
        factory.setReadTimeout(props.readTimeout());

        this.http = RestClient.builder()
                .baseUrl(props.baseUrl())
                .requestFactory(factory)
                .defaultHeader(HttpHeaders.ACCEPT, MediaType.APPLICATION_JSON_VALUE)
                .build();
    }

    @Override
    public boolean habilitado() {
        return props.enabled();
    }

    @Override
    public Long registrarSaida(DocumentoEstoque documento) {
        return registrar(GOODS_ISSUE, documento, false);
    }

    @Override
    public Long registrarEntrada(DocumentoEstoque documento) {
        return registrar(GOODS_RECEIPT, documento, true);
    }

    // ------------------------------------------------------------------ documentos

    private Long registrar(String endpoint, DocumentoEstoque documento, boolean entrada) {
        Document payload = toPayload(documento, entrada);
        return comSessao(cookie -> {
            Long existente = buscarPorReferencia(cookie, endpoint, documento.referencia());
            if (existente != null) {
                log.info("SAP B1: documento com Reference2={} já existe (DocEntry {}); reaproveitando",
                        documento.referencia(), existente);
                return existente;
            }
            JsonNode resposta = http.post()
                    .uri(endpoint)
                    .header(HttpHeaders.COOKIE, cookie)
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(payload)
                    .retrieve()
                    .body(JsonNode.class);
            if (resposta == null || !resposta.hasNonNull("DocEntry")) {
                throw new SapException("Resposta do SAP sem DocEntry ao criar documento em " + endpoint);
            }
            return resposta.get("DocEntry").asLong();
        });
    }

    private Long buscarPorReferencia(String cookie, String endpoint, String referencia) {
        String filtro = "Reference2 eq '" + referencia.replace("'", "''") + "'";
        JsonNode resposta = http.get()
                .uri(uri -> uri.path(endpoint)
                        .queryParam("$filter", "{filtro}")
                        .queryParam("$select", "DocEntry")
                        .build(filtro))
                .header(HttpHeaders.COOKIE, cookie)
                .retrieve()
                .body(JsonNode.class);
        if (resposta != null && resposta.path("value").isArray() && !resposta.path("value").isEmpty()) {
            return resposta.path("value").get(0).path("DocEntry").asLong();
        }
        return null;
    }

    private Document toPayload(DocumentoEstoque doc, boolean entrada) {
        List<Line> linhas = doc.linhas().stream().map(l -> {
            List<Batch> lotes = null;
            if (props.sendBatches() && !l.lotes().isEmpty()) {
                lotes = l.lotes().stream()
                        .map(b -> new Batch(b.numero(), b.quantidade(),
                                b.fabricacao() == null ? null : b.fabricacao().toString(),
                                b.validade() == null ? null : b.validade().toString()))
                        .toList();
            }
            return new Line(l.itemCode(), l.quantidade(), props.warehouseCode(),
                    entrada ? l.custoUnitario() : null, lotes);
        }).toList();

        String comentario = doc.comentario() != null && doc.comentario().length() > 254
                ? doc.comentario().substring(0, 254) : doc.comentario();
        return new Document(doc.data().toString(), doc.referencia(), comentario, linhas);
    }

    // ------------------------------------------------------------------ sessão (B1SESSION)

    private <T> T comSessao(Function<String, T> chamada) {
        try {
            try {
                return chamada.apply(cookieAtual());
            } catch (HttpClientErrorException.Unauthorized e) {
                log.info("Sessão do SAP B1 expirada; refazendo login");
                invalidarSessao();
                return chamada.apply(cookieAtual());
            }
        } catch (SapException e) {
            throw e;
        } catch (RestClientResponseException e) {
            throw new SapException("SAP B1 respondeu HTTP %d: %s".formatted(e.getStatusCode().value(),
                    e.getResponseBodyAsString()), e);
        } catch (RestClientException e) {
            throw new SapException("Falha de comunicação com o SAP B1: " + e.getMessage(), e);
        }
    }

    private String cookieAtual() {
        Session atual = session;
        if (atual != null && atual.valid()) {
            return atual.cookie();
        }
        sessionLock.lock();
        try {
            atual = session;
            if (atual == null || !atual.valid()) {
                atual = login();
                session = atual;
            }
            return atual.cookie();
        } finally {
            sessionLock.unlock();
        }
    }

    private void invalidarSessao() {
        sessionLock.lock();
        try {
            session = null;
        } finally {
            sessionLock.unlock();
        }
    }

    private Session login() {
        Map<String, String> credenciais = Map.of(
                "CompanyDB", props.companyDb(),
                "UserName", props.username(),
                "Password", props.password());

        ResponseEntity<JsonNode> resposta = http.post()
                .uri(LOGIN)
                .contentType(MediaType.APPLICATION_JSON)
                .body(credenciais)
                .retrieve()
                .toEntity(JsonNode.class);

        List<String> setCookies = resposta.getHeaders().getOrEmpty(HttpHeaders.SET_COOKIE);
        String cookie = setCookies.stream()
                .map(c -> c.split(";", 2)[0].trim())
                .collect(Collectors.joining("; "));
        if (!cookie.contains("B1SESSION=")) {
            throw new SapException("Login no SAP B1 não devolveu o cookie B1SESSION");
        }

        int timeoutMinutos = resposta.getBody() != null ? resposta.getBody().path("SessionTimeout").asInt(30) : 30;
        // renova 2 minutos antes do timeout informado pelo servidor
        Instant expira = Instant.now().plus(Duration.ofMinutes(Math.max(1, timeoutMinutos - 2)));
        log.info("Sessão SAP B1 aberta (válida por ~{} min)", Math.max(1, timeoutMinutos - 2));
        return new Session(cookie, expira);
    }

    @PreDestroy
    void logout() {
        Session atual = session;
        if (atual == null) {
            return;
        }
        try {
            http.post().uri(LOGOUT).header(HttpHeaders.COOKIE, atual.cookie()).retrieve().toBodilessEntity();
        } catch (Exception e) {
            log.debug("Logout no SAP B1 falhou (ignorado): {}", e.getMessage());
        }
    }

    // ------------------------------------------------------------------ SSL (somente DEV)

    private static SSLContext sslContextInseguro() {
        try {
            TrustManager[] confiaEmTudo = {new X509ExtendedTrustManager() {
                @Override
                public void checkClientTrusted(X509Certificate[] chain, String authType) {
                }

                @Override
                public void checkServerTrusted(X509Certificate[] chain, String authType) {
                }

                @Override
                public void checkClientTrusted(X509Certificate[] chain, String authType, Socket socket) {
                }

                @Override
                public void checkServerTrusted(X509Certificate[] chain, String authType, Socket socket) {
                }

                @Override
                public void checkClientTrusted(X509Certificate[] chain, String authType, SSLEngine engine) {
                }

                @Override
                public void checkServerTrusted(X509Certificate[] chain, String authType, SSLEngine engine) {
                }

                @Override
                public X509Certificate[] getAcceptedIssuers() {
                    return new X509Certificate[0];
                }
            }};
            SSLContext contexto = SSLContext.getInstance("TLS");
            contexto.init(null, confiaEmTudo, new SecureRandom());
            return contexto;
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("Não foi possível criar o SSLContext de desenvolvimento", e);
        }
    }
}
