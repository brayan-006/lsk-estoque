package br.com.lsk.inventory.infrastructure.persistence;

import br.com.lsk.inventory.application.dto.DeductedBatch;
import br.com.lsk.inventory.domain.exception.InsufficientStockException;
import br.com.lsk.inventory.domain.exception.ResourceNotFoundException;
import br.com.lsk.inventory.domain.exception.BusinessRuleException;
import br.com.lsk.inventory.domain.model.DeductionCriterion;
import br.com.lsk.inventory.domain.model.Product;
import br.com.lsk.inventory.domain.repository.FefoDeductionRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.core.NestedExceptionUtils;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.sql.SQLException;
import java.util.List;

/** Calls fn_deduct_stock_fefo on the caller's connection, so it joins the current transaction. */
@Repository
@RequiredArgsConstructor
public class FefoDeductionJdbcRepository implements FefoDeductionRepository {

    private static final String SQL = """
            SELECT * FROM fn_deduct_stock_fefo(?::bigint, ?::numeric, ?::varchar, ?::varchar, ?::numeric, ?::varchar)
            """;

    private final JdbcTemplate jdbc;

    @Override
    public List<DeductedBatch> execute(Product product, BigDecimal quantity, String reference,
                                      String source, BigDecimal salePrice, String sourceDocument) {
        try {
            return jdbc.query(SQL,
                    (rs, i) -> new DeductedBatch(
                            rs.getLong("r_movement_id"),
                            rs.getLong("r_batch_id"),
                            rs.getString("r_batch_code"),
                            rs.getBigDecimal("r_quantity"),
                            rs.getBigDecimal("r_unit_cost"),
                            DeductionCriterion.valueOf(rs.getString("r_criterion"))),
                    product.getId(), quantity, reference, source, salePrice, sourceDocument);
        } catch (DataAccessException e) {
            Throwable cause = NestedExceptionUtils.getMostSpecificCause(e);
            if (cause instanceof SQLException sql) {
                String state = sql.getSQLState();
                if ("LSK01".equals(state)) {
                    throw new InsufficientStockException(product.getSku(), quantity);
                }
                if ("LSK02".equals(state)) {
                    throw new BusinessRuleException("Invalid quantity for SKU " + product.getSku());
                }
                if ("LSK03".equals(state)) {
                    throw new ResourceNotFoundException("Product not found or inactive: " + product.getSku());
                }
            }
            throw e;
        }
    }
}
