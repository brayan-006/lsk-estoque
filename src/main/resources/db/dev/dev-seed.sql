-- Sample data (DEV). Run manually:
--   psql -h localhost -U lsk -d lsk_estoque -f src/main/resources/db/dev/dev-seed.sql
INSERT INTO categorias (nome, descricao) VALUES
  ('Suprimentos de TI', 'Toners, cartuchos, mídias'),
  ('Infraestrutura', 'Cabos, conectores, racks');

INSERT INTO produtos (sku, descricao, categoria_id, unidade, estoque_minimo, controla_validade, custo_medio, preco_venda, sap_item_code)
SELECT 'TONER-HP-85A', 'Toner HP 85A', id, 'UN', 5, TRUE, 120.00, 189.90, 'TONER-HP-85A' FROM categorias WHERE nome = 'Suprimentos de TI';
INSERT INTO produtos (sku, descricao, categoria_id, unidade, estoque_minimo, controla_validade, custo_medio, preco_venda, sap_item_code)
SELECT 'CABO-UTP-CAT6', 'Cabo UTP Cat6 (caixa 305m)', id, 'CX', 2, FALSE, 450.00, 690.00, 'CABO-UTP-CAT6' FROM categorias WHERE nome = 'Infraestrutura';

INSERT INTO lotes (produto_id, codigo_lote, data_validade, data_entrada, quantidade_inicial, quantidade_atual, custo_unitario)
SELECT id, 'L-2026-A', CURRENT_DATE + 90, now() - interval '20 days', 10, 10, 118.00 FROM produtos WHERE sku = 'TONER-HP-85A';
INSERT INTO lotes (produto_id, codigo_lote, data_validade, data_entrada, quantidade_inicial, quantidade_atual, custo_unitario)
SELECT id, 'L-2026-B', CURRENT_DATE + 30, now() - interval '5 days', 6, 6, 122.00 FROM produtos WHERE sku = 'TONER-HP-85A';
INSERT INTO lotes (produto_id, codigo_lote, data_entrada, quantidade_inicial, quantidade_atual, custo_unitario)
SELECT id, 'CX-001', now() - interval '40 days', 8, 8, 450.00 FROM produtos WHERE sku = 'CABO-UTP-CAT6';
