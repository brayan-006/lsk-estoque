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
@Table(name = "alertas_estoque")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AlertaEstoque {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "produto_id", nullable = false)
    private Produto produto;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private TipoAlerta tipo;

    @Column(name = "saldo_no_momento", nullable = false, precision = 14, scale = 3)
    private BigDecimal saldoNoMomento;

    @Column(name = "estoque_minimo", nullable = false, precision = 14, scale = 3)
    private BigDecimal estoqueMinimo;

    @Column(nullable = false, length = 300)
    private String mensagem;

    @Builder.Default
    @Column(nullable = false)
    private boolean resolvido = false;

    @Builder.Default
    @Column(name = "criado_em", nullable = false, updatable = false)
    private OffsetDateTime criadoEm = OffsetDateTime.now();

    @Column(name = "resolvido_em")
    private OffsetDateTime resolvidoEm;
}
