package br.com.lsk.estoque.application;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

import java.math.BigDecimal;
import java.time.Duration;

@ConfigurationProperties("lsk.estoque")
public record EstoqueProperties(
        @DefaultValue("90") int janelaAbcDias,
        @DefaultValue("0.80") BigDecimal limiteClasseA,
        @DefaultValue("0.95") BigDecimal limiteClasseB,
        @DefaultValue("5") int sapMaxTentativas,
        @DefaultValue("10m") Duration sapTimeoutEnvio) {
}
