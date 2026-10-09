package br.com.lsk.estoque.domain.model;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.OffsetDateTime;

@Entity
@Table(name = "movimentacoes")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Movimentacao {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "produto_id", nullable = false)
    private Produto produto;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "lote_id", nullable = false)
    private Lote lote;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    private TipoMovimentacao tipo;

    @Column(nullable = false, precision = 14, scale = 3)
    private BigDecimal quantidade;

    @Builder.Default
    @Column(name = "custo_unitario", nullable = false, precision = 14, scale = 4)
    private BigDecimal custoUnitario = BigDecimal.ZERO;

    @Column(name = "preco_unitario", precision = 14, scale = 4)
    private BigDecimal precoUnitario;

    @Column(nullable = false, length = 100)
    private String referencia;

    @Column(nullable = false, length = 30)
    private String origem;

    @Column(name = "documento_origem", length = 60)
    private String documentoOrigem;

    @Enumerated(EnumType.STRING)
    @Column(length = 10)
    private CriterioBaixa criterio;

    @Builder.Default
    @Enumerated(EnumType.STRING)
    @Column(name = "sap_status", nullable = false, length = 10)
    private StatusSap sapStatus = StatusSap.PENDENTE;

    @Column(name = "sap_doc_entry")
    private Long sapDocEntry;

    @Column(name = "sap_erro", length = 500)
    private String sapErro;

    @Builder.Default
    @Column(name = "sap_tentativas", nullable = false)
    private int sapTentativas = 0;

    @Column(name = "sap_atualizado_em")
    private OffsetDateTime sapAtualizadoEm;

    @Builder.Default
    @Column(name = "criado_em", nullable = false, updatable = false)
    private OffsetDateTime criadoEm = OffsetDateTime.now();
}
