-- Sample data (DEV). Run manually:
--   psql -h localhost -U lsk -d lsk_inventory -f src/main/resources/db/dev/dev-seed.sql
INSERT INTO categories (name, description) VALUES
  ('IT Supplies', 'Toners, cartridges, media'),
  ('Infrastructure', 'Cables, connectors, racks');

INSERT INTO products (sku, description, category_id, unit, minimum_stock, controls_expiry, average_cost, sale_price, sap_item_code)
SELECT 'TONER-HP-85A', 'Toner HP 85A', id, 'UN', 5, TRUE, 120.00, 189.90, 'TONER-HP-85A' FROM categories WHERE name = 'IT Supplies';
INSERT INTO products (sku, description, category_id, unit, minimum_stock, controls_expiry, average_cost, sale_price, sap_item_code)
SELECT 'CABLE-UTP-CAT6', 'UTP Cat6 Cable (305 m box)', id, 'CX', 2, FALSE, 450.00, 690.00, 'CABLE-UTP-CAT6' FROM categories WHERE name = 'Infrastructure';

INSERT INTO batches (product_id, batch_code, expiry_date, received_at, initial_quantity, current_quantity, unit_cost)
SELECT id, 'L-2026-A', CURRENT_DATE + 90, now() - interval '20 days', 10, 10, 118.00 FROM products WHERE sku = 'TONER-HP-85A';
INSERT INTO batches (product_id, batch_code, expiry_date, received_at, initial_quantity, current_quantity, unit_cost)
SELECT id, 'L-2026-B', CURRENT_DATE + 30, now() - interval '5 days', 6, 6, 122.00 FROM products WHERE sku = 'TONER-HP-85A';
INSERT INTO batches (product_id, batch_code, received_at, initial_quantity, current_quantity, unit_cost)
SELECT id, 'CX-001', now() - interval '40 days', 8, 8, 450.00 FROM products WHERE sku = 'CABLE-UTP-CAT6';
