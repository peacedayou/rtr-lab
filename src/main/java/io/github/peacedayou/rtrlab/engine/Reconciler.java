package io.github.peacedayou.rtrlab.engine;

import io.github.peacedayou.rtrlab.iso.Camt054;
import io.github.peacedayou.rtrlab.iso.CreditTransfer;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Rapprochement : compare les virements réglés (selon le pacs.002) aux écritures de crédit reçues
 * dans les camt.054. Chaque virement réglé doit avoir exactement une écriture CRDT du même montant
 * sur le compte du créancier.
 */
public final class Reconciler {

    public enum Kind { RAPPROCHE, MANQUANT_DANS_CAMT, MONTANT_DIFFERENT, COMPTE_DIFFERENT, INATTENDU_DANS_CAMT, EN_DOUBLE_DANS_CAMT }

    public record Line(String endToEndId, Kind kind, String detail) {}

    public record Report(List<Line> lines) {
        public long count(Kind k) {
            return lines.stream().filter(l -> l.kind() == k).count();
        }

        public boolean balanced() {
            return lines.stream().allMatch(l -> l.kind() == Kind.RAPPROCHE);
        }
    }

    private Reconciler() {}

    public static Report reconcile(List<CreditTransfer> settled, List<Camt054.Entry> entries) {
        Map<String, List<Camt054.Entry>> credits = new LinkedHashMap<>();
        for (Camt054.Entry e : entries) {
            if ("CRDT".equals(e.creditDebit())) {
                credits.computeIfAbsent(e.endToEndId(), k -> new ArrayList<>()).add(e);
            }
        }
        List<Line> lines = new ArrayList<>();
        for (CreditTransfer t : settled) {
            List<Camt054.Entry> found = credits.remove(t.endToEndId());
            if (found == null) {
                lines.add(new Line(t.endToEndId(), Kind.MANQUANT_DANS_CAMT, "aucune écriture de crédit"));
                continue;
            }
            if (found.size() > 1) {
                lines.add(new Line(t.endToEndId(), Kind.EN_DOUBLE_DANS_CAMT, found.size() + " écritures de crédit"));
                continue;
            }
            Camt054.Entry e = found.get(0);
            if (!e.account().equals(t.creditorAccount())) {
                lines.add(new Line(t.endToEndId(), Kind.COMPTE_DIFFERENT, "attendu " + t.creditorAccount() + ", reçu " + e.account()));
            } else if (e.amount().compareTo(t.amount()) != 0 || !e.currency().equals(t.currency())) {
                lines.add(new Line(t.endToEndId(), Kind.MONTANT_DIFFERENT,
                        "attendu " + fmt(t.amount()) + " " + t.currency() + ", reçu " + fmt(e.amount()) + " " + e.currency()));
            } else {
                lines.add(new Line(t.endToEndId(), Kind.RAPPROCHE, fmt(t.amount()) + " " + t.currency()));
            }
        }
        credits.forEach((id, list) -> lines.add(new Line(id, Kind.INATTENDU_DANS_CAMT, "aucun virement réglé avec cette référence")));
        return new Report(lines);
    }

    private static String fmt(BigDecimal b) {
        return b.setScale(2, java.math.RoundingMode.HALF_UP).toPlainString();
    }
}
