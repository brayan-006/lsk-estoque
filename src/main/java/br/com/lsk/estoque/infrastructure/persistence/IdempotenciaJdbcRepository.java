package br.com.lsk.estoque.infrastructure.persistence;

import br.com.lsk.estoque.domain.repository.IdempotenciaRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
@RequiredArgsConstructor
public class IdempotenciaJdbcRepository implements IdempotenciaRepository {

    private final JdbcTemplate jdbc;

    /**
     * INSERT ... ON CONFLICT DO NOTHING: com duas transações concorrentes na mesma chave, a segunda
     * espera o commit da primeira e então não insere nada (retorna false). Como roda na transação
     * do processamento, se a baixa falhar a chave também é desfeita e a mensagem pode ser reprocessada.
     */
    @Override
    public boolean registrar(String chave, String tipo) {
        return jdbc.update("""
                INSERT INTO eventos_processados (chave, tipo) VALUES (?, ?)
                ON CONFLICT (chave) DO NOTHING
                """, chave, tipo) == 1;
    }
}
