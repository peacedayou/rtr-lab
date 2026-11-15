package io.github.peacedayou.rtrlab.engine;

import io.github.peacedayou.rtrlab.iso.CreditTransfer;
import io.github.peacedayou.rtrlab.iso.Pacs008;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.List;

/** Données de test : deux institutions (Desjardins fictive 815, Banque Nationale fictive 006). */
public final class Fixtures {

    public static final Clock CLOCK = Clock.fixed(Instant.parse("2026-11-16T14:30:00Z"), ZoneId.of("America/Toronto"));

    private Fixtures() {}

    public static InMemoryLedger ledger() {
        return new InMemoryLedger()
                .open("CA-815-0001", "0815", "1000.00")
                .open("CA-815-0002", "0815", "50.00")
                .open("CA-006-0001", "0006", "0.00")
                .open("CA-006-0099", "0006", "0.00").withStatus("CA-006-0099", Ledger.Status.CLOSED);
    }

    public static PaymentEngine engine(Ledger ledger) {
        return new PaymentEngine(ledger, new IdempotencyStore.InMemory(), new BigDecimal("10000"), CLOCK);
    }

    public static CreditTransfer tx(String e2e, String amount, String from, String to) {
        return new CreditTransfer("MSG", e2e, null, new BigDecimal(amount), "CAD", "Alice Tremblay", from,
                from.contains("-815-") ? "0815" : "0006", "Bruno Côté", to, to.contains("-815-") ? "0815" : "0006", "Loyer novembre");
    }

    public static String pacs008(String msgId, CreditTransfer... txs) {
        return Pacs008.write(msgId, OffsetDateTime.now(CLOCK), List.of(txs));
    }
}
