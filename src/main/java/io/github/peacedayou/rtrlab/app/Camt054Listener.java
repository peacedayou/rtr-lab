package io.github.peacedayou.rtrlab.app;

import io.github.peacedayou.rtrlab.iso.Camt054;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

/** Joue le rôle du système comptable du client : lit les camt.054 publiés et enregistre les écritures. */
@Component
public class Camt054Listener {

    private final JdbcTemplate jdbc;

    public Camt054Listener(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @KafkaListener(topics = "${rtr.sujets.camt054}")
    public void onNotification(String xml) {
        for (Camt054.Entry e : Camt054.parse(xml)) {
            jdbc.update("INSERT INTO camt_entries (account, amount, currency, credit_debit, booking_date, msg_id, end_to_end_id) "
                    + "VALUES (?, ?, ?, ?, ?, ?, ?)", e.account(), e.amount(), e.currency(), e.creditDebit(),
                    e.bookingDate() == null ? null : java.sql.Date.valueOf(e.bookingDate()), e.msgId(), e.endToEndId());
        }
    }
}
