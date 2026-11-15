package io.github.peacedayou.rtrlab.engine;

import static io.github.peacedayou.rtrlab.engine.Fixtures.pacs008;
import static io.github.peacedayou.rtrlab.engine.Fixtures.tx;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.peacedayou.rtrlab.engine.Reconciler.Kind;
import io.github.peacedayou.rtrlab.iso.Camt054;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

class ReconcilerTest {

    private static final LocalDate D = LocalDate.of(2026, 11, 16);

    @Test
    void toutRapproche_apresUnLotNormal() {
        var r = Fixtures.engine(Fixtures.ledger()).process(pacs008("R1",
                tx("A", "10.00", "CA-815-0001", "CA-006-0001"),
                tx("B", "20.00", "CA-815-0001", "CA-006-0001"),
                tx("C", "5000.00", "CA-815-0001", "CA-006-0001"))); // rejeté : AM04
        var settled = r.transactions().stream().filter(PaymentEngine.TxResult::settled).map(PaymentEngine.TxResult::transfer).toList();
        var report = Reconciler.reconcile(settled, Camt054.parse(r.camt054().orElseThrow()));
        assertTrue(report.balanced());
        assertEquals(2, report.count(Kind.RAPPROCHE));
    }

    @Test
    void ecartsDetectes() {
        var a = tx("A", "10.00", "CA-815-0001", "CA-006-0001");
        var b = tx("B", "20.00", "CA-815-0001", "CA-006-0001");
        var c = tx("C", "30.00", "CA-815-0001", "CA-006-0001");
        var d = tx("D", "40.00", "CA-815-0001", "CA-006-0001");
        var e = tx("E", "50.00", "CA-815-0001", "CA-006-0001");
        List<Camt054.Entry> entries = new ArrayList<>(List.of(
                credit("CA-006-0001", "10.00", "A"),
                credit("CA-006-0001", "19.99", "B"),           // montant différent
                credit("CA-815-0002", "40.00", "D"),           // mauvais compte
                credit("CA-006-0001", "50.00", "E"),
                credit("CA-006-0001", "50.00", "E"),           // en double
                credit("CA-006-0001", "7.00", "Z"),            // inattendu
                new Camt054.Entry("CA-815-0001", new BigDecimal("10.00"), "CAD", "DBIT", D, "M", "A", null))); // débit ignoré
        var report = Reconciler.reconcile(List.of(a, b, c, d, e), entries);

        assertFalse(report.balanced());
        assertEquals(1, report.count(Kind.RAPPROCHE));
        assertEquals(1, report.count(Kind.MONTANT_DIFFERENT));
        assertEquals(1, report.count(Kind.MANQUANT_DANS_CAMT));
        assertEquals(1, report.count(Kind.COMPTE_DIFFERENT));
        assertEquals(1, report.count(Kind.EN_DOUBLE_DANS_CAMT));
        assertEquals(1, report.count(Kind.INATTENDU_DANS_CAMT));
    }

    private static Camt054.Entry credit(String account, String amount, String e2e) {
        return new Camt054.Entry(account, new BigDecimal(amount), "CAD", "CRDT", D, "M", e2e, null);
    }
}
