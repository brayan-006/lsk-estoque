package br.com.lsk.estoque.infrastructure.persistence;

import br.com.lsk.estoque.web.dto.DashboardViews.AlertView;
import br.com.lsk.estoque.web.dto.DashboardViews.MovementView;
import br.com.lsk.estoque.web.dto.DashboardViews.ProductView;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;

/** Read-only queries for the dashboard. */
@Repository
@RequiredArgsConstructor
public class DashboardReadRepository {

    private final JdbcTemplate jdbc;

    public List<AlertView> openAlerts() {
        return jdbc.query("""
                select a.id, p.sku, p.descricao, a.tipo, a.saldo_no_momento, a.estoque_minimo, a.mensagem, a.criado_em
                  from alertas_estoque a
                  join produtos p on p.id = a.produto_id
                 where not a.resolvido
                 order by a.criado_em desc
                """, (rs, i) -> new AlertView(
                rs.getLong("id"),
                rs.getString("sku"),
                rs.getString("descricao"),
                rs.getString("tipo"),
                rs.getBigDecimal("saldo_no_momento"),
                rs.getBigDecimal("estoque_minimo"),
                rs.getString("mensagem"),
                rs.getObject("criado_em", OffsetDateTime.class)));
    }

    public List<MovementView> recentMovements(int limit) {
        return jdbc.query("""
                select m.id, m.tipo, p.sku, l.codigo_lote, m.quantidade, m.referencia, m.sap_status,
                       m.sap_tentativas, m.sap_doc_entry, m.sap_erro, m.criado_em
                  from movimentacoes m
                  join produtos p on p.id = m.produto_id
                  join lotes l on l.id = m.lote_id
                 order by m.id desc
                 limit ?
                """, (rs, i) -> new MovementView(
                rs.getLong("id"),
                rs.getString("tipo"),
                rs.getString("sku"),
                rs.getString("codigo_lote"),
                rs.getBigDecimal("quantidade"),
                rs.getString("referencia"),
                rs.getString("sap_status"),
                rs.getInt("sap_tentativas"),
                rs.getObject("sap_doc_entry", Long.class),
                rs.getString("sap_erro"),
                rs.getObject("criado_em", OffsetDateTime.class)), limit);
    }

    public List<ProductView> products() {
        return jdbc.query("""
                select p.sku, p.descricao, p.unidade, p.classe_abc, p.estoque_minimo, p.preco_venda,
                       coalesce(sum(l.quantidade_atual) filter (
                           where l.status = 'ATIVO' and (l.data_validade is null or l.data_validade >= current_date)
                       ), 0) as balance
                  from produtos p
                  left join lotes l on l.produto_id = p.id
                 where p.ativo
                 group by p.id
                 order by p.sku
                """, (rs, i) -> {
            BigDecimal balance = rs.getBigDecimal("balance");
            BigDecimal minimum = rs.getBigDecimal("estoque_minimo");
            return new ProductView(
                    rs.getString("sku"),
                    rs.getString("descricao"),
                    rs.getString("unidade"),
                    rs.getString("classe_abc").trim(),
                    balance,
                    minimum,
                    rs.getBigDecimal("preco_venda"),
                    minimum.signum() > 0 && balance.compareTo(minimum) <= 0);
        });
    }
}