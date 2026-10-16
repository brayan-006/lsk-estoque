-- =====================================================================
-- fn_deduct_stock_fefo
--   FEFO stock deduction (first-expired, first-out) with FIFO fallback.
--
--   Consumption order:
--     1) batches WITH expiry, earliest expiry first (tie: received first);
--     2) batches WITHOUT expiry, by receipt order (FIFO).
--   Expired, blocked or depleted batches are never consumed.
--
--   Note: in PostgreSQL a PROCEDURE cannot return a result set, so this is a PL/pgSQL
--   FUNCTION that returns the consumed batches: SELECT * FROM fn_deduct_stock_fefo(...).
--
--   Atomicity: product and batches are locked (FOR UPDATE) and every effect
--   (UPDATE batches + INSERT movements) happens in the caller's transaction.
--   If the balance is short, nothing is written.
--
--   Errors (SQLSTATE): LSK01 insufficient stock | LSK02 invalid quantity
--                      LSK03 product not found or inactive
-- =====================================================================
CREATE OR REPLACE FUNCTION fn_deduct_stock_fefo(
    p_product_id BIGINT,
    p_quantity   NUMERIC,
    p_reference  VARCHAR,
    p_source     VARCHAR DEFAULT 'SALE',
    p_sale_price NUMERIC DEFAULT NULL,
    p_document   VARCHAR DEFAULT NULL
)
RETURNS TABLE (
    r_movement_id BIGINT,
    r_batch_id    BIGINT,
    r_batch_code  VARCHAR,
    r_quantity    NUMERIC,
    r_unit_cost   NUMERIC,
    r_criterion   VARCHAR
)
LANGUAGE plpgsql
AS $$
DECLARE
    v_remaining NUMERIC := p_quantity;
    v_available NUMERIC;
    v_take      NUMERIC;
    v_batch     RECORD;
    v_mov_id    BIGINT;
    v_criterion VARCHAR;
BEGIN
    IF p_quantity IS NULL OR p_quantity <= 0 THEN
        RAISE EXCEPTION 'Invalid quantity: %', p_quantity USING ERRCODE = 'LSK02';
    END IF;

    -- serialize concurrent deductions of the same product
    PERFORM 1 FROM products p WHERE p.id = p_product_id AND p.active FOR UPDATE;
    IF NOT FOUND THEN
        RAISE EXCEPTION 'Product % not found or inactive', p_product_id USING ERRCODE = 'LSK03';
    END IF;

    SELECT COALESCE(SUM(l.current_quantity), 0)
      INTO v_available
      FROM batches l
     WHERE l.product_id = p_product_id
       AND l.status = 'ACTIVE'
       AND l.current_quantity > 0
       AND (l.expiry_date IS NULL OR l.expiry_date >= CURRENT_DATE);

    IF v_available < p_quantity THEN
        RAISE EXCEPTION 'Insufficient stock (product %, requested %, available %)',
            p_product_id, p_quantity, v_available USING ERRCODE = 'LSK01';
    END IF;

    FOR v_batch IN
        SELECT l.id, l.batch_code, l.current_quantity, l.unit_cost, l.expiry_date
          FROM batches l
         WHERE l.product_id = p_product_id
           AND l.status = 'ACTIVE'
           AND l.current_quantity > 0
           AND (l.expiry_date IS NULL OR l.expiry_date >= CURRENT_DATE)
         ORDER BY (l.expiry_date IS NULL), l.expiry_date ASC, l.received_at ASC, l.id ASC
           FOR UPDATE OF l
    LOOP
        EXIT WHEN v_remaining <= 0;

        v_take  := LEAST(v_remaining, v_batch.current_quantity);
        v_criterion := CASE WHEN v_batch.expiry_date IS NULL THEN 'FIFO' ELSE 'FEFO' END;

        UPDATE batches
           SET current_quantity = current_quantity - v_take,
               status = CASE WHEN current_quantity - v_take = 0 THEN 'DEPLETED' ELSE status END
         WHERE id = v_batch.id;

        INSERT INTO movements (product_id, batch_id, type, quantity, unit_cost,
                                unit_price, reference, source, source_document, criterion)
        VALUES (p_product_id, v_batch.id, 'OUTBOUND', v_take, v_batch.unit_cost,
                p_sale_price, p_reference, p_source, p_document, v_criterion)
        RETURNING id INTO v_mov_id;

        v_remaining := v_remaining - v_take;

        r_movement_id := v_mov_id;
        r_batch_id         := v_batch.id;
        r_batch_code     := v_batch.batch_code;
        r_quantity      := v_take;
        r_unit_cost  := v_batch.unit_cost;
        r_criterion        := v_criterion;
        RETURN NEXT;
    END LOOP;
END;
$$;

COMMENT ON FUNCTION fn_deduct_stock_fefo IS
    'FEFO deduction with FIFO fallback. Returns the consumed batches. Errors: LSK01 balance, LSK02 quantity, LSK03 product.';
