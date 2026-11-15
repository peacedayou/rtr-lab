package io.github.peacedayou.rtrlab.engine;

import java.math.BigDecimal;
import java.util.Optional;

/** Registre des comptes. Deux implémentations : en mémoire (tests, démo) et PostgreSQL (application). */
public interface Ledger {

    enum Status { OPEN, CLOSED, BLOCKED }

    record Account(String id, String agent, String currency, BigDecimal balance, Status status) {}

    Optional<Account> find(String accountId);

    /** Débite et crédite d'un seul tenant : tout ou rien. */
    void transfer(String debtorAccount, String creditorAccount, BigDecimal amount);
}
