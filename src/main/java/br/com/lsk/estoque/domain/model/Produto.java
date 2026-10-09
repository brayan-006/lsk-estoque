package br.com.lsk.estoque.domain.model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.OffsetDateTime;

@Entity
@Table(name = "produtos")
@Getter
@Setter
@NoArgsConstructor
public class Produto {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true, length = 60)
    private String sku;

    @Column(nullable = false, length = 200)
    private String descricao;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "categoria_id", nullable = false)
    private Categoria categoria;

    @Column(nullable = false, length = 10)
    private String unidade = "UN";

    @Column(name = "estoque_minimo", nullable = false, precision = 14, scale = 3)
    private BigDecimal estoqueMinimo = BigDecimal.ZERO;

    @Column(name = "controla_validade", nullable = false)
    private boolean controlaValidade = true;

    @Column(name = "custo_medio", nullable = false, precision = 14, scale = 4)
    private BigDecimal custoMedio = BigDecimal.ZERO;

    @Column(name = "preco_venda", nullable = false, precision = 14, scale = 4)
    private BigDecimal precoVenda = BigDecimal.ZERO;

    @Enumerated(EnumType.STRING)
    @Column(name = "classe_abc", nullable = false)
    private ClasseAbc classeAbc = ClasseAbc.C;

    @Column(name = "sap_item_code", length = 50)
    private String sapItemCode;

    @Column(nullable = false)
    private boolean ativo = true;

    @Column(name = "criado_em", nullable = false, updatable = false)
    private OffsetDateTime criadoEm;

    @Column(name = "atualizado_em", nullable = false)
    private OffsetDateTime atualizadoEm;

    @PrePersist
    void aoCriar() {
        criadoEm = OffsetDateTime.now();
        atualizadoEm = criadoEm;
    }

    @PreUpdate
    void aoAtualizar() {
        atualizadoEm = OffsetDateTime.now();
    }
}
