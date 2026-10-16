package br.com.lsk.inventory.application.dto;

import br.com.lsk.inventory.domain.model.AbcClass;

import java.math.BigDecimal;

public record ProductAbcView(String sku, String description, AbcClass abcClass, BigDecimal salePrice,
                             BigDecimal availableBalance) {
}
