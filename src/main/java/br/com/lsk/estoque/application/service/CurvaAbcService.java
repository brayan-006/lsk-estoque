package br.com.lsk.estoque.application.service;

import br.com.lsk.estoque.application.EstoqueProperties;
import br.com.lsk.estoque.application.dto.ResultadoCurvaAbc;
import br.com.lsk.estoque.domain.repository.ProdutoRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;

@Slf4j
@Service
@RequiredArgsConstructor
public class CurvaAbcService {

    private final ProdutoRepository produtos;
    private final EstoqueProperties props;

    /**
     * Recalcula a Curva ABC (faturamento da janela configurada, giro como desempate).
     * A: até 80% do faturamento acumulado | B: até 95% | C: o restante e quem não vendeu na janela.
     */
    @Transactional
    public ResultadoCurvaAbc recalcular() {
        OffsetDateTime agora = OffsetDateTime.now();
        OffsetDateTime desde = agora.minusDays(props.janelaAbcDias());

        int reclassificados = produtos.atualizarClassesComVendas(desde, props.limiteClasseA(), props.limiteClasseB());
        int rebaixados = produtos.rebaixarSemVendas(desde);

        log.info("Curva ABC recalculada (janela {} dias): {} reclassificados, {} rebaixados para C",
                props.janelaAbcDias(), reclassificados, rebaixados);
        return new ResultadoCurvaAbc(reclassificados, rebaixados, props.janelaAbcDias(), agora);
    }
}
