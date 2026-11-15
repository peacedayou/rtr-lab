package io.github.peacedayou.rtrlab.iso;

import static io.github.peacedayou.rtrlab.iso.Iso20022.add;
import static io.github.peacedayou.rtrlab.iso.Iso20022.children;
import static io.github.peacedayou.rtrlab.iso.Iso20022.text;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import org.w3c.dom.Element;

/** Avis de débit et de crédit au client (camt.054) : écriture et lecture. */
public final class Camt054 {

    /** Une écriture comptable notifiée sur un compte. */
    public record Entry(String account, BigDecimal amount, String currency, String creditDebit, LocalDate bookingDate,
                        String msgId, String endToEndId, String uetr) {}

    private Camt054() {}

    /** Regroupe les écritures par compte, une notification (Ntfctn) par compte. */
    public static String write(String msgId, OffsetDateTime createdAt, List<Entry> entries) {
        var doc = Iso20022.newDocument();
        Element root = doc.createElementNS(Iso20022.ns(Iso20022.CAMT_054), "Document");
        doc.appendChild(root);
        Element body = add(root, "BkToCstmrDbtCdtNtfctn");
        Element hdr = add(body, "GrpHdr");
        String created = Iso20022.dateTime(createdAt);
        add(hdr, "MsgId", msgId);
        add(hdr, "CreDtTm", created);
        Map<String, List<Entry>> byAccount = new TreeMap<>();
        for (Entry e : entries) {
            byAccount.computeIfAbsent(e.account(), k -> new ArrayList<>()).add(e);
        }
        int n = 0;
        for (var acct : byAccount.entrySet()) {
            Element ntf = add(body, "Ntfctn");
            add(ntf, "Id", msgId + "-" + (++n));
            add(ntf, "CreDtTm", created);
            add(add(add(add(ntf, "Acct"), "Id"), "Othr"), "Id", acct.getKey());
            for (Entry e : acct.getValue()) {
                Element ntry = add(ntf, "Ntry");
                add(ntry, "Amt", e.amount().toPlainString()).setAttribute("Ccy", e.currency());
                add(ntry, "CdtDbtInd", e.creditDebit());
                add(add(ntry, "Sts"), "Cd", "BOOK");
                add(add(ntry, "BookgDt"), "Dt", e.bookingDate().toString());
                Element refs = add(add(add(ntry, "NtryDtls"), "TxDtls"), "Refs");
                if (e.msgId() != null) {
                    add(refs, "MsgId", e.msgId());
                }
                add(refs, "EndToEndId", e.endToEndId());
                if (e.uetr() != null) {
                    add(refs, "UETR", e.uetr());
                }
            }
        }
        if (byAccount.isEmpty()) {
            throw new IllegalArgumentException("Un camt.054 doit contenir au moins une notification.");
        }
        String xml = Iso20022.toXml(doc);
        XsdValidator.requireValid(Iso20022.CAMT_054, xml);
        return xml;
    }

    public static List<Entry> parse(String xml) {
        XsdValidator.requireValid(Iso20022.CAMT_054, xml);
        Element body = Iso20022.child(Iso20022.parse(xml).getDocumentElement(), "BkToCstmrDbtCdtNtfctn");
        List<Entry> out = new ArrayList<>();
        for (Element ntf : children(body, "Ntfctn")) {
            String account = text(ntf, "Acct/Id/Othr/Id");
            for (Element ntry : children(ntf, "Ntry")) {
                Element amt = Iso20022.child(ntry, "Amt");
                String date = text(ntry, "BookgDt/Dt");
                out.add(new Entry(account, new BigDecimal(amt.getTextContent().trim()), amt.getAttribute("Ccy"),
                        text(ntry, "CdtDbtInd"), date == null ? null : LocalDate.parse(date),
                        text(ntry, "NtryDtls/TxDtls/Refs/MsgId"), text(ntry, "NtryDtls/TxDtls/Refs/EndToEndId"),
                        text(ntry, "NtryDtls/TxDtls/Refs/UETR")));
            }
        }
        return out;
    }
}
