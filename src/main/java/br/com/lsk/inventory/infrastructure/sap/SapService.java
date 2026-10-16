package br.com.lsk.inventory.infrastructure.sap;

import br.com.lsk.inventory.application.port.StockDocument;
import br.com.lsk.inventory.application.port.ErpGateway;
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

/** Client for the SAP Business One Service Layer: session handling, goods issue/receipt and idempotency by Reference2. */
@Slf4j
@Service
public class SapService implements ErpGateway {

    private static final String LOGIN = "/b1s/v1/Login";
    private static final String LOGOUT = "/b1s/v1/Logout";
    private static final String GOODS_ISSUE = "/b1s/v1/InventoryGenExits";
    private static final String GOODS_RECEIPT = "/b1s/v1/InventoryGenEntries";

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
            log.warn("lsk.sap.insecure-ssl=true: certificate validation is OFF (development only)");
            client.sslContext(sslContextInsecure());
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
    public boolean enabled() {
        return props.enabled();
    }

    @Override
    public Long registerIssue(StockDocument document) {
        return register(GOODS_ISSUE, document, false);
    }

    @Override
    public Long registerReceipt(StockDocument document) {
        return register(GOODS_RECEIPT, document, true);
    }

    private Long register(String endpoint, StockDocument document, boolean receipt) {
        Document payload = toPayload(document, receipt);
        return withSession(cookie -> {
            Long existing = findByReference(cookie, endpoint, document.reference());
            if (existing != null) {
                log.info("SAP B1: document with Reference2={} already exists (DocEntry {}); reusing it",
                        document.reference(), existing);
                return existing;
            }
            JsonNode reply = http.post()
                    .uri(endpoint)
                    .header(HttpHeaders.COOKIE, cookie)
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(payload)
                    .retrieve()
                    .body(JsonNode.class);
            if (reply == null || !reply.hasNonNull("DocEntry")) {
                throw new SapException("SAP response without DocEntry when creating a document at " + endpoint);
            }
            return reply.get("DocEntry").asLong();
        });
    }

    private Long findByReference(String cookie, String endpoint, String reference) {
        String filter = "Reference2 eq '" + reference.replace("'", "''") + "'";
        JsonNode reply = http.get()
                .uri(uri -> uri.path(endpoint)
                        .queryParam("$filter", "{filter}")
                        .queryParam("$select", "DocEntry")
                        .build(filter))
                .header(HttpHeaders.COOKIE, cookie)
                .retrieve()
                .body(JsonNode.class);
        if (reply != null && reply.path("value").isArray() && !reply.path("value").isEmpty()) {
            return reply.path("value").get(0).path("DocEntry").asLong();
        }
        return null;
    }

    private Document toPayload(StockDocument doc, boolean receipt) {
        List<Line> rows = doc.rows().stream().map(l -> {
            List<Batch> batches = null;
            if (props.sendBatches() && !l.batches().isEmpty()) {
                batches = l.batches().stream()
                        .map(b -> new Batch(b.number(), b.quantity(),
                                b.manufacturing() == null ? null : b.manufacturing().toString(),
                                b.expiry() == null ? null : b.expiry().toString()))
                        .toList();
            }
            return new Line(l.itemCode(), l.quantity(), props.warehouseCode(),
                    receipt ? l.unitCost() : null, batches);
        }).toList();

        String comment = doc.comment() != null && doc.comment().length() > 254
                ? doc.comment().substring(0, 254) : doc.comment();
        return new Document(doc.data().toString(), doc.reference(), comment, rows);
    }

    private <T> T withSession(Function<String, T> call) {
        try {
            try {
                return call.apply(currentCookie());
            } catch (HttpClientErrorException.Unauthorized e) {
                log.info("SAP B1 session expired; logging in again");
                invalidateSession();
                return call.apply(currentCookie());
            }
        } catch (SapException e) {
            throw e;
        } catch (RestClientResponseException e) {
            throw new SapException("SAP B1 answered HTTP %d: %s".formatted(e.getStatusCode().value(),
                    e.getResponseBodyAsString()), e);
        } catch (RestClientException e) {
            throw new SapException("Communication failure with SAP B1: " + e.getMessage(), e);
        }
    }

    private String currentCookie() {
        Session current = session;
        if (current != null && current.valid()) {
            return current.cookie();
        }
        sessionLock.lock();
        try {
            current = session;
            if (current == null || !current.valid()) {
                current = login();
                session = current;
            }
            return current.cookie();
        } finally {
            sessionLock.unlock();
        }
    }

    private void invalidateSession() {
        sessionLock.lock();
        try {
            session = null;
        } finally {
            sessionLock.unlock();
        }
    }

    private Session login() {
        Map<String, String> credentials = Map.of(
                "CompanyDB", props.companyDb(),
                "UserName", props.username(),
                "Password", props.password());

        ResponseEntity<JsonNode> reply = http.post()
                .uri(LOGIN)
                .contentType(MediaType.APPLICATION_JSON)
                .body(credentials)
                .retrieve()
                .toEntity(JsonNode.class);

        List<String> setCookies = reply.getHeaders().getOrEmpty(HttpHeaders.SET_COOKIE);
        String cookie = setCookies.stream()
                .map(c -> c.split(";", 2)[0].trim())
                .collect(Collectors.joining("; "));
        if (!cookie.contains("B1SESSION=")) {
            throw new SapException("SAP B1 login did not return the B1SESSION cookie");
        }

        int timeoutMinutes = reply.getBody() != null ? reply.getBody().path("SessionTimeout").asInt(30) : 30;
        // renew 2 minutes before the timeout reported by the server
        Instant expires = Instant.now().plus(Duration.ofMinutes(Math.max(1, timeoutMinutes - 2)));
        log.info("SAP B1 session opened (valid for ~{} min)", Math.max(1, timeoutMinutes - 2));
        return new Session(cookie, expires);
    }

    @PreDestroy
    void logout() {
        Session current = session;
        if (current == null) {
            return;
        }
        try {
            http.post().uri(LOGOUT).header(HttpHeaders.COOKIE, current.cookie()).retrieve().toBodilessEntity();
        } catch (Exception e) {
            log.debug("SAP B1 logout failed (ignored): {}", e.getMessage());
        }
    }

    private static SSLContext sslContextInsecure() {
        try {
            TrustManager[] trustAll = {new X509ExtendedTrustManager() {
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
            SSLContext context = SSLContext.getInstance("TLS");
            context.init(null, trustAll, new SecureRandom());
            return context;
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("Could not create the development SSLContext", e);
        }
    }
}
