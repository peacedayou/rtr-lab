package io.github.peacedayou.rtrlab.engine;

import java.math.BigDecimal;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

public class InMemoryLedger implements Ledger {

    private final Map<String, Account> accounts = new ConcurrentHashMap<>();

    public InMemoryLedger open(String id, String agent, String balance) {
        accounts.put(id, new Account(id, agent, "CAD", new BigDecimal(balance), Status.OPEN));
        return this;
    }

    public InMemoryLedger withStatus(String id, Status status) {
        accounts.computeIfPresent(id, (k, a) -> new Account(a.id(), a.agent(), a.currency(), a.balance(), status));
        return this;
    }

    @Override
    public Optional<Account> find(String accountId) {
        return Optional.ofNullable(accounts.get(accountId));
    }

    @Override
    public synchronized void transfer(String debtorAccount, String creditorAccount, BigDecimal amount) {
        Account d = accounts.get(debtorAccount);
        Account c = accounts.get(creditorAccount);
        if (d == null || c == null || d.balance().compareTo(amount) < 0) {
            throw new IllegalStateException("Virement impossible");
        }
        accounts.put(d.id(), new Account(d.id(), d.agent(), d.currency(), d.balance().subtract(amount), d.status()));
        accounts.put(c.id(), new Account(c.id(), c.agent(), c.currency(), c.balance().add(amount), c.status()));
    }

    public BigDecimal balance(String id) {
        return accounts.get(id).balance();
    }
}
