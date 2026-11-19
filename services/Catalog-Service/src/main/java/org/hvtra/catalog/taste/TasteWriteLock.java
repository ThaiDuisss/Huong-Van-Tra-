package org.hvtra.catalog.taste;

import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Component
@RequiredArgsConstructor
class TasteWriteLock {
    private final JdbcTemplate jdbc;

    // The existing sequence table provides a transaction-scoped row lock, including the first publish.
    @Transactional(propagation = Propagation.MANDATORY)
    public void acquire() {
        jdbc.update("""
                insert into business_sequences (sequence_key, current_value) values (?, 0)
                on duplicate key update current_value = business_sequences.current_value
                """, "CATALOG_TASTE_WRITE");
    }
}
