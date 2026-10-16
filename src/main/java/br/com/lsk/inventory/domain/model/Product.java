package br.com.lsk.inventory.domain.model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.OffsetDateTime;

@Entity
@Table(name = "products")
@Getter
@Setter
@NoArgsConstructor
public class Product {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true, length = 60)
    private String sku;

    @Column(nullable = false, length = 200)
    private String description;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "category_id", nullable = false)
    private Category category;

    @Column(nullable = false, length = 10)
    private String unit = "UN";

    @Column(name = "minimum_stock", nullable = false, precision = 14, scale = 3)
    private BigDecimal minimumStock = BigDecimal.ZERO;

    @Column(name = "controls_expiry", nullable = false)
    private boolean controlsExpiry = true;

    @Column(name = "average_cost", nullable = false, precision = 14, scale = 4)
    private BigDecimal averageCost = BigDecimal.ZERO;

    @Column(name = "sale_price", nullable = false, precision = 14, scale = 4)
    private BigDecimal salePrice = BigDecimal.ZERO;

    @Enumerated(EnumType.STRING)
    @Column(name = "abc_class", nullable = false)
    private AbcClass abcClass = AbcClass.C;

    @Column(name = "sap_item_code", length = 50)
    private String sapItemCode;

    @Column(nullable = false)
    private boolean active = true;

    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt;

    @PrePersist
    void aoCreate() {
        createdAt = OffsetDateTime.now();
        updatedAt = createdAt;
    }

    @PreUpdate
    void onUpdate() {
        updatedAt = OffsetDateTime.now();
    }
}
