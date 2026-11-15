package io.github.peacedayou.rtrlab.app;

import io.github.peacedayou.rtrlab.engine.PaymentEngine;
import io.github.peacedayou.rtrlab.engine.Reconciler;
import io.github.peacedayou.rtrlab.iso.Camt054;
import io.github.peacedayou.rtrlab.iso.CreditTransfer;
import java.math.BigDecimal;
import java.time.Clock;
import java.util.List;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;

/** Relie le moteur au monde extérieur : PostgreSQL pour l'état, Kafka pour les messages sortants. */
@Service
public class PaymentService {

    private final PaymentEngine engine;
    private final JdbcTemplate jdbc;
    private final KafkaTemplate<String, String> kafka;
    private final String topicPacs002;
    private final String topicCamt054;

    public PaymentService(JdbcLedger ledger, JdbcIdempotencyStore store, JdbcTemplate jdbc, KafkaTemplate<String, String> kafka,
                          @Value("${rtr.limite-par-transaction}") BigDecimal limit,
                          @Value("${rtr.sujets.pacs002}") String topicPacs002,
                          @Value("${rtr.sujets.camt054}") String topicCamt054) {
        this.engine = new PaymentEngine(ledger, store, limit, Clock.systemDefaultZone());
        this.jdbc = jdbc;
        this.kafka = kafka;
        this.topicPacs002 = topicPacs002;
        this.topicCamt054 = topicCamt054;
    }

    public PaymentEngine.Result submit(String pacs008Xml) {
        PaymentEngine.Result r = engine.process(pacs008Xml);
        for (PaymentEngine.TxResult tx : r.transactions()) {
            if (tx.settled() && !tx.replay()) {
                CreditTransfer t = tx.transfer();
                jdbc.update("INSERT INTO settled_transfers (msg_id, end_to_end_id, amount, currency, debtor_account, creditor_account) "
                        + "VALUES (?, ?, ?, ?, ?, ?) ON CONFLICT DO NOTHING",
                        t.msgId(), t.endToEndId(), t.amount(), t.currency(), t.debtorAccount(), t.creditorAccount());
            }
        }
        kafka.send(topicPacs002, r.originalMsgId(), r.pacs002());
        r.camt054().ifPresent(xml -> kafka.send(topicCamt054, r.originalMsgId(), xml));
        return r;
    }

    public String reject(String reason) {
        return engine.rejectMessage(null, reason);
    }

    public Reconciler.Report reconcile() {
        List<CreditTransfer> settled = jdbc.query(
                "SELECT msg_id, end_to_end_id, amount, currency, debtor_account, creditor_account FROM settled_transfers ORDER BY settled_at",
                (rs, i) -> new CreditTransfer(rs.getString(1), rs.getString(2), null, rs.getBigDecimal(3), rs.getString(4).trim(),
                        null, rs.getString(5), null, null, rs.getString(6), null, null));
        List<Camt054.Entry> entries = jdbc.query(
                "SELECT account, amount, currency, credit_debit, booking_date, msg_id, end_to_end_id FROM camt_entries ORDER BY id",
                (rs, i) -> new Camt054.Entry(rs.getString(1), rs.getBigDecimal(2), rs.getString(3).trim(), rs.getString(4).trim(),
                        rs.getDate(5) == null ? null : rs.getDate(5).toLocalDate(), rs.getString(6), rs.getString(7), null));
        return Reconciler.reconcile(settled, entries);
    }
}
