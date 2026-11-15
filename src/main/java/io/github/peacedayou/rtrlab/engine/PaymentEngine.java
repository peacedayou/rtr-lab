package io.github.peacedayou.rtrlab.engine;

import io.github.peacedayou.rtrlab.iso.Camt054;
import io.github.peacedayou.rtrlab.iso.CreditTransfer;
import io.github.peacedayou.rtrlab.iso.InvalidMessageException;
import io.github.peacedayou.rtrlab.iso.Pacs002;
import io.github.peacedayou.rtrlab.iso.Pacs008;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Cœur du simulateur : reçoit un pacs.008, contrôle chaque transaction, règle celles qui passent,
 * et produit un pacs.002 (état) et un camt.054 (avis aux titulaires des comptes).
 *
 * <p>Idempotence : une transaction déjà traitée n'est jamais réglée deux fois. Un renvoi identique
 * retrouve son état d'origine ; une même référence avec un contenu différent est rejetée (AM05).
 */
public class PaymentEngine {

    public static final String CURRENCY = "CAD";

    /** Limite par transaction du simulateur (paramétrable). */
    private final BigDecimal maxAmount;
    private final Ledger ledger;
    private final IdempotencyStore store;
    private final Clock clock;
    private final AtomicLong seq = new AtomicLong();

    public record TxResult(CreditTransfer transfer, String status, ReasonCode reason, boolean replay) {
        public boolean settled() {
            return "ACSC".equals(status);
        }
    }

    public record Result(String originalMsgId, List<TxResult> transactions, String pacs002, Optional<String> camt054,
                         List<Camt054.Entry> entries) {}

    public PaymentEngine(Ledger ledger, IdempotencyStore store, BigDecimal maxAmount, Clock clock) {
        this.ledger = ledger;
        this.store = store;
        this.maxAmount = maxAmount;
        this.clock = clock;
    }

    /** Traite un pacs.008 brut. Un message illisible ou non conforme lève InvalidMessageException. */
    public Result process(String pacs008Xml) {
        Pacs008.Message msg = Pacs008.parse(pacs008Xml);
        if (msg.declaredCount() != msg.transfers().size()) {
            throw new InvalidMessageException("NbOfTxs (" + msg.declaredCount() + ") ne correspond pas au nombre de transactions ("
                    + msg.transfers().size() + ")");
        }
        OffsetDateTime now = OffsetDateTime.now(clock);
        List<TxResult> results = new ArrayList<>();
        List<Camt054.Entry> entries = new ArrayList<>();
        for (CreditTransfer t : msg.transfers()) {
            TxResult r = processOne(t);
            results.add(r);
            if (r.settled() && !r.replay()) {
                LocalDate day = now.toLocalDate();
                entries.add(new Camt054.Entry(t.debtorAccount(), t.amount(), t.currency(), "DBIT", day, t.msgId(), t.endToEndId(), t.uetr()));
                entries.add(new Camt054.Entry(t.creditorAccount(), t.amount(), t.currency(), "CRDT", day, t.msgId(), t.endToEndId(), t.uetr()));
            }
        }
        List<Pacs002.TxStatus> statuses = results.stream()
                .map(r -> new Pacs002.TxStatus(r.transfer().endToEndId(), r.transfer().uetr(), r.status(),
                        r.reason() == null ? null : r.reason().name(), r.reason() == null ? null : r.reason().label()))
                .toList();
        String groupStatus = groupStatus(results);
        String pacs002 = Pacs002.write(nextId("STS", now), now, msg.msgId(), groupStatus, null, statuses);
        Optional<String> camt = entries.isEmpty() ? Optional.empty() : Optional.of(Camt054.write(nextId("NTF", now), now, entries));
        return new Result(msg.msgId(), results, pacs002, camt, entries);
    }

    /** pacs.002 de rejet global pour un message refusé avant lecture (code FF01). */
    public String rejectMessage(String originalMsgId, String reason) {
        OffsetDateTime now = OffsetDateTime.now(clock);
        return Pacs002.write(nextId("STS", now), now, originalMsgId == null ? "INCONNU" : originalMsgId, "RJCT",
                ReasonCode.FF01.name(), List.of());
    }

    private TxResult processOne(CreditTransfer t) {
        String key = t.uetr() != null ? "UETR:" + t.uetr() : "E2E:" + t.debtorAgent() + ":" + t.endToEndId();
        Optional<IdempotencyStore.Outcome> previous = store.find(key);
        if (previous.isPresent()) {
            return replayOrConflict(t, previous.get());
        }
        // On réserve la clé (PDNG) avant tout contrôle : deux requêtes simultanées ne peuvent pas régler deux fois.
        if (!store.putIfAbsent(key, new IdempotencyStore.Outcome(t.fingerprint(), "PDNG", null))) {
            return replayOrConflict(t, store.find(key).orElseThrow());
        }
        ReasonCode reason = check(t);
        if (reason == null) {
            try {
                ledger.transfer(t.debtorAccount(), t.creditorAccount(), t.amount());
            } catch (IllegalStateException e) {
                reason = ReasonCode.AM04; // le solde a changé entre le contrôle et le règlement
            }
        }
        String status = reason == null ? "ACSC" : "RJCT";
        store.complete(key, new IdempotencyStore.Outcome(t.fingerprint(), status, reason == null ? null : reason.name()));
        return new TxResult(t, status, reason, false);
    }

    private TxResult replayOrConflict(CreditTransfer t, IdempotencyStore.Outcome previous) {
        if (previous.fingerprint().equals(t.fingerprint())) {
            ReasonCode r = previous.reasonCode() == null ? null : ReasonCode.valueOf(previous.reasonCode());
            return new TxResult(t, previous.status(), r, true);
        }
        return new TxResult(t, "RJCT", ReasonCode.AM05, false);
    }

    private ReasonCode check(CreditTransfer t) {
        if (t.amount().signum() <= 0) {
            return ReasonCode.AM09;
        }
        if (!CURRENCY.equals(t.currency())) {
            return ReasonCode.AM03;
        }
        if (t.amount().compareTo(maxAmount) > 0) {
            return ReasonCode.AM02;
        }
        var debtor = ledger.find(t.debtorAccount());
        if (debtor.isEmpty()) {
            return ReasonCode.AC01;
        }
        if (!debtor.get().agent().equals(t.debtorAgent())) {
            return ReasonCode.RC01;
        }
        var creditor = ledger.find(t.creditorAccount());
        if (creditor.isEmpty()) {
            return ReasonCode.AC03;
        }
        if (!creditor.get().agent().equals(t.creditorAgent())) {
            return ReasonCode.RC01;
        }
        for (var acct : List.of(debtor.get(), creditor.get())) {
            if (acct.status() == Ledger.Status.CLOSED) {
                return ReasonCode.AC04;
            }
            if (acct.status() == Ledger.Status.BLOCKED) {
                return ReasonCode.AC06;
            }
        }
        if (debtor.get().balance().compareTo(t.amount()) < 0) {
            return ReasonCode.AM04;
        }
        return null;
    }

    private static String groupStatus(List<TxResult> results) {
        long ok = results.stream().filter(TxResult::settled).count();
        if (ok == results.size()) {
            return "ACSC";
        }
        return ok == 0 ? "RJCT" : "PART";
    }

    private String nextId(String prefix, OffsetDateTime now) {
        return prefix + now.toLocalDate().toString().replace("-", "") + String.format("%06d", seq.incrementAndGet());
    }
}
