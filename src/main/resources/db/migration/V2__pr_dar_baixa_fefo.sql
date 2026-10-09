-- =====================================================================
-- pr_dar_baixa_fefo
--   Baixa FEFO (primeiro a vencer, primeiro a sair) com fallback PEPS.
--
--   Ordem de consumo:
--     1) lotes COM validade, do que vence primeiro ao que vence por último
--        (empate: o que entrou antes);
--     2) lotes SEM validade, por ordem de entrada (PEPS).
--   Lotes vencidos, bloqueados ou esgotados nunca são consumidos.
--
--   Nota: no PostgreSQL, PROCEDURE não devolve result set; por isso a
--   rotina é uma FUNCTION PL/pgSQL (mesmo nome) que devolve os lotes
--   consumidos e é chamada com SELECT * FROM pr_dar_baixa_fefo(...).
--
--   Atomicidade: o produto e os lotes são travados (FOR UPDATE) e todo o
--   efeito (UPDATE lotes + INSERT movimentacoes) acontece na transação do
--   chamador. Se faltar saldo, nada é gravado.
--
--   Erros (SQLSTATE): LSK01 saldo insuficiente | LSK02 quantidade inválida
--                     LSK03 produto inexistente/inativo
-- =====================================================================
CREATE OR REPLACE FUNCTION pr_dar_baixa_fefo(
    p_produto_id  BIGINT,
    p_quantidade  NUMERIC,
    p_referencia  VARCHAR,
    p_origem      VARCHAR DEFAULT 'VENDA',
    p_preco_venda NUMERIC DEFAULT NULL,
    p_documento   VARCHAR DEFAULT NULL
)
RETURNS TABLE (
    r_movimentacao_id BIGINT,
    r_lote_id         BIGINT,
    r_codigo_lote     VARCHAR,
    r_quantidade      NUMERIC,
    r_custo_unitario  NUMERIC,
    r_criterio        VARCHAR
)
LANGUAGE plpgsql
AS $$
DECLARE
    v_restante   NUMERIC := p_quantidade;
    v_disponivel NUMERIC;
    v_retirar    NUMERIC;
    v_lote       RECORD;
    v_mov_id     BIGINT;
    v_criterio   VARCHAR;
BEGIN
    IF p_quantidade IS NULL OR p_quantidade <= 0 THEN
        RAISE EXCEPTION 'Quantidade inválida: %', p_quantidade USING ERRCODE = 'LSK02';
    END IF;

    -- Serializa baixas concorrentes do mesmo produto
    PERFORM 1 FROM produtos p WHERE p.id = p_produto_id AND p.ativo FOR UPDATE;
    IF NOT FOUND THEN
        RAISE EXCEPTION 'Produto % inexistente ou inativo', p_produto_id USING ERRCODE = 'LSK03';
    END IF;

    SELECT COALESCE(SUM(l.quantidade_atual), 0)
      INTO v_disponivel
      FROM lotes l
     WHERE l.produto_id = p_produto_id
       AND l.status = 'ATIVO'
       AND l.quantidade_atual > 0
       AND (l.data_validade IS NULL OR l.data_validade >= CURRENT_DATE);

    IF v_disponivel < p_quantidade THEN
        RAISE EXCEPTION 'Estoque insuficiente (produto %, solicitado %, disponível %)',
            p_produto_id, p_quantidade, v_disponivel USING ERRCODE = 'LSK01';
    END IF;

    FOR v_lote IN
        SELECT l.id, l.codigo_lote, l.quantidade_atual, l.custo_unitario, l.data_validade
          FROM lotes l
         WHERE l.produto_id = p_produto_id
           AND l.status = 'ATIVO'
           AND l.quantidade_atual > 0
           AND (l.data_validade IS NULL OR l.data_validade >= CURRENT_DATE)
         ORDER BY (l.data_validade IS NULL), l.data_validade ASC, l.data_entrada ASC, l.id ASC
           FOR UPDATE OF l
    LOOP
        EXIT WHEN v_restante <= 0;

        v_retirar  := LEAST(v_restante, v_lote.quantidade_atual);
        v_criterio := CASE WHEN v_lote.data_validade IS NULL THEN 'PEPS' ELSE 'FEFO' END;

        UPDATE lotes
           SET quantidade_atual = quantidade_atual - v_retirar,
               status = CASE WHEN quantidade_atual - v_retirar = 0 THEN 'ESGOTADO' ELSE status END
         WHERE id = v_lote.id;

        INSERT INTO movimentacoes (produto_id, lote_id, tipo, quantidade, custo_unitario,
                                   preco_unitario, referencia, origem, documento_origem, criterio)
        VALUES (p_produto_id, v_lote.id, 'SAIDA', v_retirar, v_lote.custo_unitario,
                p_preco_venda, p_referencia, p_origem, p_documento, v_criterio)
        RETURNING id INTO v_mov_id;

        v_restante := v_restante - v_retirar;

        r_movimentacao_id := v_mov_id;
        r_lote_id         := v_lote.id;
        r_codigo_lote     := v_lote.codigo_lote;
        r_quantidade      := v_retirar;
        r_custo_unitario  := v_lote.custo_unitario;
        r_criterio        := v_criterio;
        RETURN NEXT;
    END LOOP;
END;
$$;

COMMENT ON FUNCTION pr_dar_baixa_fefo IS
    'Baixa FEFO com fallback PEPS. Retorna os lotes consumidos. Erros: LSK01 saldo, LSK02 quantidade, LSK03 produto.';
