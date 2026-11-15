package io.github.peacedayou.rtrlab.app;

import io.github.peacedayou.rtrlab.engine.Ledger;
import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

/** Registre PostgreSQL. Le débit conditionnel (solde suffisant) rend tout découvert impossible, même en parallèle. */
@Repository
public class JdbcLedger implements Ledger {

    private final JdbcTemplate jdbc;

    public JdbcLedger(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public Optional<Account> find(String accountId) {
        return jdbc.query("SELECT id, agent, currency, balance, status FROM accounts WHERE id = ?",
                (rs, i) -> new Account(rs.getString(1), rs.getString(2), rs.getString(3).trim(), rs.getBigDecimal(4),
                        Status.valueOf(rs.getString(5))), accountId).stream().findFirst();
    }

    @Override
    @Transactional
    public void transfer(String debtorAccount, String creditorAccount, BigDecimal amount) {
        int debited = jdbc.update("UPDATE accounts SET balance = balance - ? WHERE id = ? AND status = 'OPEN' AND balance >= ?",
                amount, debtorAccount, amount);
        if (debited != 1) {
            throw new IllegalStateException("Débit impossible sur " + debtorAccount);
        }
        int credited = jdbc.update("UPDATE accounts SET balance = balance + ? WHERE id = ? AND status = 'OPEN'", amount, creditorAccount);
        if (credited != 1) {
            throw new IllegalStateException("Crédit impossible sur " + creditorAccount); // annule le débit (rollback)
        }
    }

    public List<Account> all() {
        return jdbc.query("SELECT id, agent, currency, balance, status FROM accounts ORDER BY id",
                (rs, i) -> new Account(rs.getString(1), rs.getString(2), rs.getString(3).trim(), rs.getBigDecimal(4),
                        Status.valueOf(rs.getString(5))));
    }
}
