package br.com.lsk.estoque.infrastructure.persistence;

import br.com.lsk.estoque.application.dto.LoteBaixado;
import br.com.lsk.estoque.domain.exception.EstoqueInsuficienteException;
import br.com.lsk.estoque.domain.exception.RecursoNaoEncontradoException;
import br.com.lsk.estoque.domain.exception.RegraDeNegocioException;
import br.com.lsk.estoque.domain.model.CriterioBaixa;
import br.com.lsk.estoque.domain.model.Produto;
import br.com.lsk.estoque.domain.repository.BaixaFefoRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.core.NestedExceptionUtils;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.sql.SQLException;
import java.util.List;

/** Chama pr_dar_baixa_fefo na MESMA conexão/transação do chamador (JpaTransactionManager). */
@Repository
@RequiredArgsConstructor
public class BaixaFefoJdbcRepository implements BaixaFefoRepository {

    private static final String SQL = """
            SELECT * FROM pr_dar_baixa_fefo(?::bigint, ?::numeric, ?::varchar, ?::varchar, ?::numeric, ?::varchar)
            """;

    private final JdbcTemplate jdbc;

    @Override
    public List<LoteBaixado> executar(Produto produto, BigDecimal quantidade, String referencia,
                                      String origem, BigDecimal precoVenda, String documentoOrigem) {
        try {
            return jdbc.query(SQL,
                    (rs, i) -> new LoteBaixado(
                            rs.getLong("r_movimentacao_id"),
                            rs.getLong("r_lote_id"),
                            rs.getString("r_codigo_lote"),
                            rs.getBigDecimal("r_quantidade"),
                            rs.getBigDecimal("r_custo_unitario"),
                            CriterioBaixa.valueOf(rs.getString("r_criterio"))),
                    produto.getId(), quantidade, referencia, origem, precoVenda, documentoOrigem);
        } catch (DataAccessException e) {
            Throwable causa = NestedExceptionUtils.getMostSpecificCause(e);
            if (causa instanceof SQLException sql) {
                String estado = sql.getSQLState();
                if ("LSK01".equals(estado)) {
                    throw new EstoqueInsuficienteException(produto.getSku(), quantidade);
                }
                if ("LSK02".equals(estado)) {
                    throw new RegraDeNegocioException("Quantidade inválida para o SKU " + produto.getSku());
                }
                if ("LSK03".equals(estado)) {
                    throw new RecursoNaoEncontradoException("Produto inexistente ou inativo: " + produto.getSku());
                }
            }
            throw e;
        }
    }
}
