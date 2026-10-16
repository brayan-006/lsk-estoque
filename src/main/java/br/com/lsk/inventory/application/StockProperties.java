package br.com.lsk.inventory.application;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

import java.math.BigDecimal;
import java.time.Duration;

@ConfigurationProperties("lsk.inventory")
public record StockProperties(
        @DefaultValue("90") int abcWindowDays,
        @DefaultValue("0.80") BigDecimal classALimit,
        @DefaultValue("0.95") BigDecimal classBLimit,
        @DefaultValue("5") int sapMaxAttempts,
        @DefaultValue("10m") Duration sapSendTimeout) {
}
