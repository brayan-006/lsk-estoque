package br.com.lsk.estoque.domain.model;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;

@Entity
@Table(name = "lotes")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Lote {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "produto_id", nullable = false)
    private Produto produto;

    @Column(name = "codigo_lote", nullable = false, length = 60)
    private String codigoLote;

    @Column(name = "data_fabricacao")
    private LocalDate dataFabricacao;

    @Column(name = "data_validade")
    private LocalDate dataValidade;

    @Builder.Default
    @Column(name = "data_entrada", nullable = false)
    private OffsetDateTime dataEntrada = OffsetDateTime.now();

    @Column(name = "quantidade_inicial", nullable = false, precision = 14, scale = 3)
    private BigDecimal quantidadeInicial;

    @Column(name = "quantidade_atual", nullable = false, precision = 14, scale = 3)
    private BigDecimal quantidadeAtual;

    @Builder.Default
    @Column(name = "custo_unitario", nullable = false, precision = 14, scale = 4)
    private BigDecimal custoUnitario = BigDecimal.ZERO;

    @Column(name = "nfe_chave", length = 44)
    private String nfeChave;

    @Builder.Default
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    private StatusLote status = StatusLote.ATIVO;
}
