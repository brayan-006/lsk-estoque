package br.com.lsk.inventory.domain.model;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.OffsetDateTime;

@Entity
@Table(name = "movements")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Movement {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "product_id", nullable = false)
    private Product product;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "batch_id", nullable = false)
    private Batch batch;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    private MovementType type;

    @Column(nullable = false, precision = 14, scale = 3)
    private BigDecimal quantity;

    @Builder.Default
    @Column(name = "unit_cost", nullable = false, precision = 14, scale = 4)
    private BigDecimal unitCost = BigDecimal.ZERO;

    @Column(name = "unit_price", precision = 14, scale = 4)
    private BigDecimal unitPrice;

    @Column(nullable = false, length = 100)
    private String reference;

    @Column(nullable = false, length = 30)
    private String source;

    @Column(name = "source_document", length = 60)
    private String sourceDocument;

    @Enumerated(EnumType.STRING)
    @Column(length = 10)
    private DeductionCriterion criterion;

    @Builder.Default
    @Enumerated(EnumType.STRING)
    @Column(name = "sap_status", nullable = false, length = 10)
    private StatusSap sapStatus = StatusSap.PENDING;

    @Column(name = "sap_doc_entry")
    private Long sapDocEntry;

    @Column(name = "sap_error", length = 500)
    private String sapError;

    @Builder.Default
    @Column(name = "sap_attempts", nullable = false)
    private int sapAttempts = 0;

    @Column(name = "sap_updated_at")
    private OffsetDateTime sapUpdatedAt;

    @Builder.Default
    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt = OffsetDateTime.now();
}
