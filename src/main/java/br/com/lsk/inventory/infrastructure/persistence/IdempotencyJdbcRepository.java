package br.com.lsk.inventory.infrastructure.persistence;

import br.com.lsk.inventory.domain.repository.IdempotencyRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
@RequiredArgsConstructor
public class IdempotencyJdbcRepository implements IdempotencyRepository {

    private final JdbcTemplate jdbc;

    /**
     * ON CONFLICT DO NOTHING makes a concurrent duplicate wait for the first commit and insert nothing.
     * It runs inside the processing transaction, so a failed deduction also rolls the key back.
     */
    @Override
    public boolean register(String key, String type) {
        return jdbc.update("""
                INSERT INTO processed_events (key, type) VALUES (?, ?)
                ON CONFLICT (key) DO NOTHING
                """, key, type) == 1;
    }
}
