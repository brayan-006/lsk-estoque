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
     * ON CONFLICT DO NOTHING makes a concurrent duplicate wait for the first commit and insert nothing.
     * It runs inside the processing transaction, so a failed deduction also rolls the key back.
     */
    @Override
    public boolean registrar(String chave, String tipo) {
        return jdbc.update("""
                INSERT INTO eventos_processados (chave, tipo) VALUES (?, ?)
                ON CONFLICT (chave) DO NOTHING
                """, chave, tipo) == 1;
    }
}
