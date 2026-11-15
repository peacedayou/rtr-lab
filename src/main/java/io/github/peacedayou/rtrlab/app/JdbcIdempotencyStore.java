package io.github.peacedayou.rtrlab.app;

import io.github.peacedayou.rtrlab.engine.IdempotencyStore;
import java.util.Optional;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

/** Idempotence en base : la clé primaire garantit qu'une seule requête réserve une transaction. */
@Repository
public class JdbcIdempotencyStore implements IdempotencyStore {

    private final JdbcTemplate jdbc;

    public JdbcIdempotencyStore(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public Optional<Outcome> find(String key) {
        return jdbc.query("SELECT fingerprint, status, reason_code FROM payment_outcomes WHERE idem_key = ?",
                (rs, i) -> new Outcome(rs.getString(1), rs.getString(2), rs.getString(3)), key).stream().findFirst();
    }

    @Override
    public boolean putIfAbsent(String key, Outcome outcome) {
        return jdbc.update("INSERT INTO payment_outcomes (idem_key, fingerprint, status, reason_code) VALUES (?, ?, ?, ?) "
                + "ON CONFLICT (idem_key) DO NOTHING", key, outcome.fingerprint(), outcome.status(), outcome.reasonCode()) == 1;
    }

    @Override
    public void complete(String key, Outcome outcome) {
        jdbc.update("UPDATE payment_outcomes SET status = ?, reason_code = ?, updated_at = now() WHERE idem_key = ?",
                outcome.status(), outcome.reasonCode(), key);
    }
}
