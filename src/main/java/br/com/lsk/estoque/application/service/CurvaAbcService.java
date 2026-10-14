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

    /** ABC by revenue over the configured window, with turnover as the tiebreaker. */
    @Transactional
    public ResultadoCurvaAbc recalcular() {
        OffsetDateTime agora = OffsetDateTime.now();
        OffsetDateTime desde = agora.minusDays(props.janelaAbcDias());

        int reclassificados = produtos.atualizarClassesComVendas(desde, props.limiteClasseA(), props.limiteClasseB());
        int rebaixados = produtos.rebaixarSemVendas(desde);

        log.info("ABC analysis recalculated (window {} days): {} reclassified, {} downgraded to C",
                props.janelaAbcDias(), reclassificados, rebaixados);
        return new ResultadoCurvaAbc(reclassificados, rebaixados, props.janelaAbcDias(), agora);
    }
}
