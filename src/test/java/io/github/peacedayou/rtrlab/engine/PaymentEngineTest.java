package io.github.peacedayou.rtrlab.engine;

import static io.github.peacedayou.rtrlab.engine.Fixtures.pacs008;
import static io.github.peacedayou.rtrlab.engine.Fixtures.tx;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.peacedayou.rtrlab.iso.Camt054;
import io.github.peacedayou.rtrlab.iso.InvalidMessageException;
import io.github.peacedayou.rtrlab.iso.Iso20022;
import io.github.peacedayou.rtrlab.iso.XsdValidator;
import java.math.BigDecimal;
import java.util.List;
import org.junit.jupiter.api.Test;

class PaymentEngineTest {

    @Test
    void reglementReussi_debiteCrediteEtNotifie() {
        InMemoryLedger ledger = Fixtures.ledger();
        var r = Fixtures.engine(ledger).process(pacs008("M1", tx("E2E-1", "125.50", "CA-815-0001", "CA-006-0001")));

        assertEquals("ACSC", r.transactions().get(0).status());
        assertEquals(new BigDecimal("874.50"), ledger.balance("CA-815-0001"));
        assertEquals(new BigDecimal("125.50"), ledger.balance("CA-006-0001"));
        assertTrue(r.pacs002().contains("<TxSts>ACSC</TxSts>"));
        assertTrue(XsdValidator.errors(Iso20022.PACS_002, r.pacs002()).isEmpty());
        List<Camt054.Entry> entries = Camt054.parse(r.camt054().orElseThrow());
        assertEquals(2, entries.size());
        assertTrue(entries.stream().anyMatch(e -> e.account().equals("CA-006-0001") && e.creditDebit().equals("CRDT")));
        assertTrue(entries.stream().anyMatch(e -> e.account().equals("CA-815-0001") && e.creditDebit().equals("DBIT")));
    }

    @Test
    void rejets_avecCodesIso() {
        InMemoryLedger ledger = Fixtures.ledger();
        var r = Fixtures.engine(ledger).process(pacs008("M2",
                tx("E-AM04", "500.00", "CA-815-0002", "CA-006-0001"),
                tx("E-AC01", "10.00", "CA-815-9999", "CA-006-0001"),
                tx("E-AC03", "10.00", "CA-815-0001", "CA-006-7777"),
                tx("E-AC04", "10.00", "CA-815-0001", "CA-006-0099"),
                tx("E-AM02", "10000.01", "CA-815-0001", "CA-006-0001"),
                tx("E-OK", "1.00", "CA-815-0001", "CA-006-0001")));

        var codes = r.transactions().stream().map(t -> t.reason() == null ? "OK" : t.reason().name()).toList();
        assertEquals(List.of("AM04", "AC01", "AC03", "AC04", "AM02", "OK"), codes);
        assertTrue(r.pacs002().contains("<GrpSts>PART</GrpSts>"));
        assertTrue(r.pacs002().contains("<Cd>AM04</Cd>"));
        assertEquals(new BigDecimal("50.00"), ledger.balance("CA-815-0002"), "aucun débit sur un rejet");
        assertEquals(2, r.entries().size(), "seule la transaction réglée est notifiée");
    }

    @Test
    void deviseEtMontantInvalides() {
        var ledger = Fixtures.ledger();
        var usd = new io.github.peacedayou.rtrlab.iso.CreditTransfer("M", "E-USD", null, new BigDecimal("5"), "USD",
                "A", "CA-815-0001", "0815", "B", "CA-006-0001", "0006", null);
        var zero = tx("E-ZERO", "0", "CA-815-0001", "CA-006-0001");
        var r = Fixtures.engine(ledger).process(pacs008("M3", usd, zero));
        assertEquals("AM03", r.transactions().get(0).reason().name());
        assertEquals("AM09", r.transactions().get(1).reason().name());
        assertTrue(r.camt054().isEmpty());
        assertTrue(r.pacs002().contains("<GrpSts>RJCT</GrpSts>"));
    }

    @Test
    void mauvaiseInstitution_RC01() {
        var t = new io.github.peacedayou.rtrlab.iso.CreditTransfer("M", "E-RC", null, new BigDecimal("5"), "CAD",
                "A", "CA-815-0001", "0006", "B", "CA-006-0001", "0006", null);
        var r = Fixtures.engine(Fixtures.ledger()).process(pacs008("M4", t));
        assertEquals("RC01", r.transactions().get(0).reason().name());
    }

    @Test
    void idempotence_renvoiIdentiqueNeRegleQuUneFois() {
        InMemoryLedger ledger = Fixtures.ledger();
        PaymentEngine engine = Fixtures.engine(ledger);
        String msg = pacs008("M5", tx("E2E-REPLAY", "100.00", "CA-815-0001", "CA-006-0001"));

        var first = engine.process(msg);
        var second = engine.process(msg);

        assertEquals("ACSC", first.transactions().get(0).status());
        assertEquals("ACSC", second.transactions().get(0).status());
        assertTrue(second.transactions().get(0).replay());
        assertTrue(second.camt054().isEmpty(), "pas de deuxième avis");
        assertEquals(new BigDecimal("900.00"), ledger.balance("CA-815-0001"), "débité une seule fois");
    }

    @Test
    void idempotence_memeReferenceContenuDifferent_AM05() {
        InMemoryLedger ledger = Fixtures.ledger();
        PaymentEngine engine = Fixtures.engine(ledger);
        engine.process(pacs008("M6", tx("E2E-X", "100.00", "CA-815-0001", "CA-006-0001")));
        var r = engine.process(pacs008("M7", tx("E2E-X", "999.00", "CA-815-0001", "CA-006-0001")));
        assertEquals("AM05", r.transactions().get(0).reason().name());
        assertEquals(new BigDecimal("900.00"), ledger.balance("CA-815-0001"));
    }

    @Test
    void idempotence_unRejetRenvoyeResteRejete() {
        PaymentEngine engine = Fixtures.engine(Fixtures.ledger());
        String msg = pacs008("M8", tx("E2E-POOR", "500.00", "CA-815-0002", "CA-006-0001"));
        engine.process(msg);
        var again = engine.process(msg);
        assertEquals("AM04", again.transactions().get(0).reason().name());
        assertTrue(again.transactions().get(0).replay());
    }

    @Test
    void messageNonConforme_estRefuse() {
        PaymentEngine engine = Fixtures.engine(Fixtures.ledger());
        String bad = pacs008("M9", tx("E2E-1", "1.00", "CA-815-0001", "CA-006-0001")).replace("<ChrgBr>SLEV</ChrgBr>", "");
        assertThrows(InvalidMessageException.class, () -> engine.process(bad));
        assertThrows(InvalidMessageException.class, () -> engine.process("<pas du xml"));
        String rejet = engine.rejectMessage("M9", "schéma");
        assertTrue(rejet.contains("<Cd>FF01</Cd>"));
    }

    @Test
    void nombreDeTransactionsIncoherent_estRefuse() {
        String msg = pacs008("M10", tx("E2E-1", "1.00", "CA-815-0001", "CA-006-0001")).replace("<NbOfTxs>1</NbOfTxs>", "<NbOfTxs>2</NbOfTxs>");
        assertThrows(InvalidMessageException.class, () -> Fixtures.engine(Fixtures.ledger()).process(msg));
    }

    @Test
    void xxe_bloque() {
        String evil = "<?xml version=\"1.0\"?><!DOCTYPE d [<!ENTITY x SYSTEM \"file:///etc/passwd\">]><d>&x;</d>";
        assertThrows(InvalidMessageException.class, () -> Fixtures.engine(Fixtures.ledger()).process(evil));
    }

    @Test
    void reglementsConcurrents_jamaisDeDecouvert() throws Exception {
        InMemoryLedger ledger = Fixtures.ledger(); // CA-815-0002 : 50,00 $
        PaymentEngine engine = Fixtures.engine(ledger);
        List<Thread> threads = new java.util.ArrayList<>();
        for (int i = 0; i < 20; i++) {
            String msg = pacs008("MC" + i, tx("E2E-C" + i, "10.00", "CA-815-0002", "CA-006-0001"));
            threads.add(Thread.ofVirtual().start(() -> engine.process(msg)));
        }
        for (Thread t : threads) {
            t.join();
        }
        assertEquals(new BigDecimal("0.00"), ledger.balance("CA-815-0002"));
        assertEquals(new BigDecimal("50.00"), ledger.balance("CA-006-0001"));
        assertFalse(ledger.balance("CA-815-0002").signum() < 0);
    }
}
