package io.github.peacedayou.rtrlab.engine;

import io.github.peacedayou.rtrlab.iso.Camt054;
import io.github.peacedayou.rtrlab.iso.CreditTransfer;
import java.io.InputStream;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.util.List;

/**
 * Génère la page de démonstration (GitHub Pages) : on rejoue le lot d'exemple, plus un renvoi et un conflit,
 * et on publie les vrais messages produits par le moteur. JDK seulement : aucun serveur nécessaire.
 */
public final class DemoReport {

    private DemoReport() {}

    public static void main(String[] args) throws Exception {
        Path out = Path.of(args.length > 0 ? args[0] : "_site/index.html");
        Files.createDirectories(out.toAbsolutePath().getParent());
        Files.writeString(out, render(), StandardCharsets.UTF_8);
        System.out.println("Page écrite : " + out);
    }

    public static String render() throws Exception {
        String lot;
        try (InputStream in = DemoReport.class.getResourceAsStream("/exemples/pacs008-lot.xml")) {
            lot = new String(in.readAllBytes(), StandardCharsets.UTF_8);
        }
        Clock clock = Clock.fixed(Instant.parse("2026-11-16T14:30:00Z"), ZoneId.of("America/Toronto"));
        InMemoryLedger ledger = new InMemoryLedger()
                .open("CA-815-0001", "0815", "1000.00")
                .open("CA-815-0002", "0815", "50.00")
                .open("CA-006-0001", "0006", "0.00")
                .open("CA-006-0099", "0006", "0.00").withStatus("CA-006-0099", Ledger.Status.CLOSED);
        PaymentEngine engine = new PaymentEngine(ledger, new IdempotencyStore.InMemory(), new BigDecimal("10000"), clock);

        PaymentEngine.Result first = engine.process(lot);
        PaymentEngine.Result replay = engine.process(lot);
        String conflict = lot.replace("<IntrBkSttlmAmt Ccy=\"CAD\">125.50</IntrBkSttlmAmt>", "<IntrBkSttlmAmt Ccy=\"CAD\">999.00</IntrBkSttlmAmt>")
                .replace("LOT-20261116-001", "LOT-20261116-002");
        PaymentEngine.Result conflictResult = engine.process(conflict);

        List<CreditTransfer> settled = first.transactions().stream().filter(PaymentEngine.TxResult::settled)
                .map(PaymentEngine.TxResult::transfer).toList();
        Reconciler.Report rec = Reconciler.reconcile(settled, Camt054.parse(first.camt054().orElseThrow()));

        StringBuilder rows = new StringBuilder();
        for (PaymentEngine.TxResult t : first.transactions()) {
            rows.append("<tr><td><code>").append(esc(t.transfer().endToEndId())).append("</code></td><td class=num>")
                    .append(t.transfer().amount().toPlainString()).append(" $</td><td>").append(esc(t.transfer().debtorAccount()))
                    .append(" → ").append(esc(t.transfer().creditorAccount())).append("</td><td><b class=")
                    .append(t.settled() ? "ok" : "ko").append('>').append(t.status()).append("</b>")
                    .append(t.reason() == null ? "" : " <code>" + t.reason().name() + "</code> " + esc(t.reason().label()))
                    .append("</td></tr>");
        }
        StringBuilder recRows = new StringBuilder();
        for (Reconciler.Line l : rec.lines()) {
            recRows.append("<li><code>").append(esc(l.endToEndId())).append("</code> : ").append(l.kind()).append(" (")
                    .append(esc(l.detail())).append(")</li>");
        }
        PaymentEngine.TxResult c = conflictResult.transactions().get(0);

        return """
                <!DOCTYPE html><html lang="fr-CA"><head><meta charset="utf-8">
                <meta name="viewport" content="width=device-width, initial-scale=1">
                <title>rtr-lab – simulateur de paiements ISO 20022</title>
                <style>
                :root{--bg:#f7f7f4;--ink:#18202b;--mut:#5d6675;--line:#dcdcd4;--card:#fff;--acc:#1f5f99;--ok:#1b7a43;--ko:#b3261e}
                @media (prefers-color-scheme:dark){:root{--bg:#121519;--ink:#e6e6e2;--mut:#a0a7b3;--line:#2b3038;--card:#1a1e24;--acc:#7fb6ea;--ok:#6fd49a;--ko:#ff8a80}}
                body{margin:0;background:var(--bg);color:var(--ink);font:16px/1.55 system-ui,-apple-system,"Segoe UI",sans-serif}
                main{max-width:980px;margin:0 auto;padding:40px 20px 64px}h1{font-size:34px;margin:0 0 6px}h2{margin-top:36px}
                .lead{color:var(--mut);max-width:70ch}table{width:100%%;border-collapse:collapse;background:var(--card);border:1px solid var(--line)}
                td,th{padding:9px 12px;border-bottom:1px solid var(--line);text-align:left;vertical-align:top}.num{text-align:right;white-space:nowrap}
                .ok{color:var(--ok)}.ko{color:var(--ko)}details{background:var(--card);border:1px solid var(--line);border-radius:10px;padding:10px 14px;margin:10px 0}
                summary{cursor:pointer;font-weight:600}pre{overflow:auto;font-size:13px;line-height:1.4}code{font-family:ui-monospace,Menlo,Consolas,monospace}
                .grid{display:grid;grid-template-columns:repeat(auto-fit,minmax(260px,1fr));gap:12px}.card{background:var(--card);border:1px solid var(--line);border-radius:10px;padding:14px}
                a{color:var(--acc)}
                </style></head><body><main>
                <h1>rtr-lab</h1>
                <p class="lead">Simulateur de paiements en temps réel au format ISO 20022, comme ceux du Real-Time Rail canadien :
                un <b>pacs.008</b> entre, chaque virement est contrôlé puis réglé ou rejeté avec un code ISO, un <b>pacs.002</b> répond,
                un <b>camt.054</b> avise les titulaires, et le rapprochement vérifie que tout concorde. Cette page est générée par le
                vrai moteur, à chaque déploiement. Données fictives.</p>

                <h2>1. Le lot reçu et son traitement</h2>
                <table><tr><th>EndToEndId</th><th class=num>Montant</th><th>Comptes</th><th>Résultat</th></tr>%s</table>

                <h2>2. Idempotence</h2>
                <div class="grid">
                <div class="card"><b>Renvoi identique du lot</b><br>Même état pour chaque virement (%s), <b>aucun nouveau débit</b> :
                le solde du compte CA-815-0001 reste à %s $. Aucun deuxième avis camt.054.</div>
                <div class="card"><b>Même référence, montant différent</b><br>Le virement <code>LOYER-NOV-2026</code> renvoyé avec 999,00 $
                est rejeté : <b class="ko">%s</b> <code>%s</code> %s.</div>
                </div>

                <h2>3. Rapprochement</h2>
                <p>%s</p><ul>%s</ul>

                <h2>4. Les messages produits</h2>
                <details><summary>pacs.008 reçu (lot d'exemple)</summary><pre>%s</pre></details>
                <details open><summary>pacs.002 renvoyé</summary><pre>%s</pre></details>
                <details><summary>camt.054 publié sur Kafka</summary><pre>%s</pre></details>

                <p class="lead">Code source, API Spring Boot, Kafka, PostgreSQL et tests Testcontainers :
                <a href="https://github.com/peacedayou/rtr-lab">github.com/peacedayou/rtr-lab</a>.</p>
                </main></body></html>
                """.formatted(rows, String.join(", ", replay.transactions().stream().map(PaymentEngine.TxResult::status).toList()),
                ledger.balance("CA-815-0001").toPlainString(), c.status(), c.reason() == null ? "" : c.reason().name(),
                c.reason() == null ? "" : esc(c.reason().label()),
                rec.balanced() ? "Équilibré : chaque virement réglé a exactement une écriture de crédit du même montant."
                        : "Écarts détectés.",
                recRows, esc(lot), esc(first.pacs002()), esc(first.camt054().orElse("")));
    }

    private static String esc(String s) {
        return s == null ? "" : s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("\"", "&quot;");
    }
}
