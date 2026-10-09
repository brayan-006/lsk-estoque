package br.com.lsk.estoque.infrastructure.sap;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

import java.time.Duration;

@ConfigurationProperties("lsk.sap")
public record SapProperties(
        @DefaultValue("false") boolean enabled,
        @DefaultValue("https://localhost:50000") String baseUrl,
        String companyDb,
        String username,
        String password,
        @DefaultValue("01") String warehouseCode,
        @DefaultValue("false") boolean insecureSsl,
        @DefaultValue("true") boolean sendBatches,
        @DefaultValue("5s") Duration connectTimeout,
        @DefaultValue("30s") Duration readTimeout) {
}
