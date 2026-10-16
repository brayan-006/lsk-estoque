-- =====================================================================
-- LSK Technologies - Inventory Management | Initial schema (PostgreSQL 14+)
-- =====================================================================

CREATE TABLE categories (
    id          BIGSERIAL    PRIMARY KEY,
    name        VARCHAR(80)  NOT NULL,
    description VARCHAR(255),
    created_at  TIMESTAMPTZ  NOT NULL DEFAULT now(),
    CONSTRAINT uk_categories_name UNIQUE (name)
);

CREATE TABLE products (
    id              BIGSERIAL     PRIMARY KEY,
    sku             VARCHAR(60)   NOT NULL,
    description     VARCHAR(200)  NOT NULL,
    category_id     BIGINT        NOT NULL REFERENCES categories (id),
    unit            VARCHAR(10)   NOT NULL DEFAULT 'UN',
    minimum_stock   NUMERIC(14,3) NOT NULL DEFAULT 0,
    controls_expiry BOOLEAN       NOT NULL DEFAULT TRUE,
    average_cost    NUMERIC(14,4) NOT NULL DEFAULT 0,
    sale_price      NUMERIC(14,4) NOT NULL DEFAULT 0,
    abc_class       VARCHAR(1)    NOT NULL DEFAULT 'C',
    sap_item_code   VARCHAR(50),
    active          BOOLEAN       NOT NULL DEFAULT TRUE,
    created_at      TIMESTAMPTZ   NOT NULL DEFAULT now(),
    updated_at      TIMESTAMPTZ   NOT NULL DEFAULT now(),
    CONSTRAINT uk_products_sku UNIQUE (sku),
    CONSTRAINT ck_products_minimum CHECK (minimum_stock >= 0),
    CONSTRAINT ck_products_class CHECK (abc_class IN ('A', 'B', 'C'))
);
CREATE INDEX ix_products_class ON products (abc_class) WHERE active;
CREATE INDEX ix_products_category ON products (category_id);

CREATE TABLE batches (
    id                 BIGSERIAL     PRIMARY KEY,
    product_id         BIGINT        NOT NULL REFERENCES products (id),
    batch_code         VARCHAR(60)   NOT NULL,
    manufacturing_date DATE,
    expiry_date        DATE,
    received_at        TIMESTAMPTZ   NOT NULL DEFAULT now(),
    initial_quantity   NUMERIC(14,3) NOT NULL,
    current_quantity   NUMERIC(14,3) NOT NULL,
    unit_cost          NUMERIC(14,4) NOT NULL DEFAULT 0,
    nfe_key            VARCHAR(44),
    status             VARCHAR(10)   NOT NULL DEFAULT 'ACTIVE',
    CONSTRAINT uk_batches_product_code UNIQUE (product_id, batch_code),
    CONSTRAINT ck_batches_qty_initial CHECK (initial_quantity > 0),
    CONSTRAINT ck_batches_qty_current CHECK (current_quantity >= 0),
    CONSTRAINT ck_batches_status CHECK (status IN ('ACTIVE', 'DEPLETED', 'EXPIRED', 'BLOCKED')),
    CONSTRAINT ck_batches_dates CHECK (manufacturing_date IS NULL OR expiry_date IS NULL OR expiry_date >= manufacturing_date)
);
-- Index that backs the FEFO / FIFO ordering
CREATE INDEX ix_batches_fefo ON batches (product_id, expiry_date NULLS LAST, received_at, id)
    WHERE status = 'ACTIVE' AND current_quantity > 0;

CREATE TABLE movements (
    id              BIGSERIAL     PRIMARY KEY,
    product_id      BIGINT        NOT NULL REFERENCES products (id),
    batch_id        BIGINT        NOT NULL REFERENCES batches (id),
    type            VARCHAR(10)   NOT NULL,
    quantity        NUMERIC(14,3) NOT NULL,
    unit_cost       NUMERIC(14,4) NOT NULL DEFAULT 0,
    unit_price      NUMERIC(14,4),
    reference       VARCHAR(100)  NOT NULL,
    source          VARCHAR(30)   NOT NULL,
    source_document VARCHAR(60),
    criterion       VARCHAR(10),
    sap_status      VARCHAR(10)   NOT NULL DEFAULT 'PENDING',
    sap_doc_entry   BIGINT,
    sap_error       VARCHAR(500),
    sap_attempts    INTEGER       NOT NULL DEFAULT 0,
    sap_updated_at  TIMESTAMPTZ,
    created_at      TIMESTAMPTZ   NOT NULL DEFAULT now(),
    CONSTRAINT ck_mov_type CHECK (type IN ('INBOUND', 'OUTBOUND', 'ADJUSTMENT')),
    CONSTRAINT ck_mov_qty CHECK (quantity > 0),
    CONSTRAINT ck_mov_criterion CHECK (criterion IS NULL OR criterion IN ('FEFO', 'FIFO')),
    CONSTRAINT ck_mov_sap CHECK (sap_status IN ('PENDING', 'SENDING', 'SENT', 'ERROR'))
);
CREATE INDEX ix_mov_product_data ON movements (product_id, created_at);
CREATE INDEX ix_mov_reference ON movements (reference, type);
CREATE INDEX ix_mov_sap_pending ON movements (sap_status) WHERE sap_status <> 'SENT';
CREATE INDEX ix_mov_sales_abc ON movements (created_at)
    WHERE type = 'OUTBOUND' AND source = 'SALE';

CREATE TABLE stock_alerts (
    id               BIGSERIAL     PRIMARY KEY,
    product_id       BIGINT        NOT NULL REFERENCES products (id),
    type             VARCHAR(30)   NOT NULL,
    balance_at_alert NUMERIC(14,3) NOT NULL,
    minimum_stock    NUMERIC(14,3) NOT NULL,
    message          VARCHAR(300)  NOT NULL,
    resolved         BOOLEAN       NOT NULL DEFAULT FALSE,
    created_at       TIMESTAMPTZ   NOT NULL DEFAULT now(),
    resolved_at      TIMESTAMPTZ,
    CONSTRAINT ck_alert_type CHECK (type IN ('MINIMUM_STOCK'))
);
-- At most one open alert per product/type
CREATE UNIQUE INDEX uk_alert_open ON stock_alerts (product_id, type) WHERE NOT resolved;

-- Idempotency control (sale events and NF-e)
CREATE TABLE processed_events (
    key          VARCHAR(100) PRIMARY KEY,
    type         VARCHAR(20)  NOT NULL,
    processed_at TIMESTAMPTZ  NOT NULL DEFAULT now()
);
