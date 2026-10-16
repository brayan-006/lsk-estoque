package br.com.lsk.inventory.infrastructure.persistence;

import br.com.lsk.inventory.web.dto.DashboardViews.AlertView;
import br.com.lsk.inventory.web.dto.DashboardViews.MovementView;
import br.com.lsk.inventory.web.dto.DashboardViews.ProductView;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;

/** Read-only queries for the dashboard. */
@Repository
@RequiredArgsConstructor
public class DashboardReadRepository {

    private static final String MOVEMENT_SELECT = """
            select m.id, m.type, p.sku, l.batch_code, m.quantity, m.reference, m.sap_status,
                   m.sap_attempts, m.sap_doc_entry, m.sap_error, m.created_at
              from movements m
              join products p on p.id = m.product_id
              join batches l on l.id = m.batch_id
            """;

    private static final RowMapper<MovementView> MOVEMENT_MAPPER = (rs, i) -> new MovementView(
            rs.getLong("id"),
            rs.getString("type"),
            rs.getString("sku"),
            rs.getString("batch_code"),
            rs.getBigDecimal("quantity"),
            rs.getString("reference"),
            rs.getString("sap_status"),
            rs.getInt("sap_attempts"),
            rs.getObject("sap_doc_entry", Long.class),
            rs.getString("sap_error"),
            rs.getObject("created_at", OffsetDateTime.class));

    private final JdbcTemplate jdbc;

    public List<AlertView> openAlerts() {
        return jdbc.query("""
                select a.id, p.sku, p.description, a.type, a.balance_at_alert, a.minimum_stock, a.message, a.created_at
                  from stock_alerts a
                  join products p on p.id = a.product_id
                 where not a.resolved and p.active
                 order by a.created_at desc
                """, (rs, i) -> new AlertView(
                rs.getLong("id"),
                rs.getString("sku"),
                rs.getString("description"),
                rs.getString("type"),
                rs.getBigDecimal("balance_at_alert"),
                rs.getBigDecimal("minimum_stock"),
                rs.getString("message"),
                rs.getObject("created_at", OffsetDateTime.class)));
    }

    public List<MovementView> recentMovements(int limit) {
        return jdbc.query(MOVEMENT_SELECT + " order by m.id desc limit ?", MOVEMENT_MAPPER, limit);
    }

    public List<MovementView> movementsByReference(String reference) {
        return jdbc.query(MOVEMENT_SELECT + " where m.reference = ? order by m.id", MOVEMENT_MAPPER, reference);
    }

    public List<String> categories() {
        return jdbc.queryForList("select name from categories order by name", String.class);
    }

    public List<ProductView> products() {
        return jdbc.query("""
                select p.sku, p.description, p.unit, p.abc_class, p.minimum_stock, p.sale_price,
                       coalesce(sum(l.current_quantity) filter (
                           where l.status = 'ACTIVE' and (l.expiry_date is null or l.expiry_date >= current_date)
                       ), 0) as balance
                  from products p
                  left join batches l on l.product_id = p.id
                 where p.active
                 group by p.id
                 order by p.sku
                """, (rs, i) -> {
            BigDecimal balance = rs.getBigDecimal("balance");
            BigDecimal minimum = rs.getBigDecimal("minimum_stock");
            return new ProductView(
                    rs.getString("sku"),
                    rs.getString("description"),
                    rs.getString("unit"),
                    rs.getString("abc_class").trim(),
                    balance,
                    minimum,
                    rs.getBigDecimal("sale_price"),
                    minimum.signum() > 0 && balance.compareTo(minimum) <= 0);
        });
    }
}
