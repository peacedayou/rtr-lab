package io.github.peacedayou.rtrlab.iso;

import static io.github.peacedayou.rtrlab.iso.Iso20022.add;
import static io.github.peacedayou.rtrlab.iso.Iso20022.children;
import static io.github.peacedayou.rtrlab.iso.Iso20022.text;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;
import org.w3c.dom.Element;

/** Lecture et écriture d'un pacs.008 (virement client entre institutions). */
public final class Pacs008 {

    public record Message(String msgId, OffsetDateTime createdAt, int declaredCount, List<CreditTransfer> transfers) {}

    private Pacs008() {}

    /** Valide contre le XSD, puis lit le message. Lève InvalidMessageException si non conforme. */
    public static Message parse(String xml) {
        XsdValidator.requireValid(Iso20022.PACS_008, xml);
        Element root = Iso20022.parse(xml).getDocumentElement();
        Element body = Iso20022.child(root, "FIToFICstmrCdtTrf");
        String msgId = text(body, "GrpHdr/MsgId");
        int declared = Integer.parseInt(text(body, "GrpHdr/NbOfTxs"));
        OffsetDateTime created = OffsetDateTime.parse(text(body, "GrpHdr/CreDtTm"));
        List<CreditTransfer> txs = children(body, "CdtTrfTxInf").stream().map(tx -> {
            Element amt = Iso20022.child(tx, "IntrBkSttlmAmt");
            return new CreditTransfer(
                    msgId,
                    text(tx, "PmtId/EndToEndId"),
                    text(tx, "PmtId/UETR"),
                    new BigDecimal(amt.getTextContent().trim()),
                    amt.getAttribute("Ccy"),
                    text(tx, "Dbtr/Nm"),
                    text(tx, "DbtrAcct/Id/Othr/Id"),
                    text(tx, "DbtrAgt/FinInstnId/ClrSysMmbId/MmbId"),
                    text(tx, "Cdtr/Nm"),
                    text(tx, "CdtrAcct/Id/Othr/Id"),
                    text(tx, "CdtrAgt/FinInstnId/ClrSysMmbId/MmbId"),
                    text(tx, "RmtInf/Ustrd"));
        }).toList();
        return new Message(msgId, created, declared, txs);
    }

    /** Construit un pacs.008 conforme (utile pour les tests, la démo et un client). */
    public static String write(String msgId, OffsetDateTime createdAt, List<CreditTransfer> transfers) {
        var doc = Iso20022.newDocument();
        Element root = doc.createElementNS(Iso20022.ns(Iso20022.PACS_008), "Document");
        doc.appendChild(root);
        Element body = add(root, "FIToFICstmrCdtTrf");
        Element hdr = add(body, "GrpHdr");
        add(hdr, "MsgId", msgId);
        add(hdr, "CreDtTm", Iso20022.dateTime(createdAt));
        add(hdr, "NbOfTxs", String.valueOf(transfers.size()));
        add(add(hdr, "SttlmInf"), "SttlmMtd", "CLRG");
        for (CreditTransfer t : transfers) {
            Element tx = add(body, "CdtTrfTxInf");
            Element pmt = add(tx, "PmtId");
            add(pmt, "EndToEndId", t.endToEndId());
            if (t.uetr() != null) {
                add(pmt, "UETR", t.uetr());
            }
            add(tx, "IntrBkSttlmAmt", t.amount().toPlainString()).setAttribute("Ccy", t.currency());
            add(tx, "ChrgBr", "SLEV");
            add(add(tx, "Dbtr"), "Nm", t.debtorName());
            add(add(add(add(tx, "DbtrAcct"), "Id"), "Othr"), "Id", t.debtorAccount());
            add(add(add(add(tx, "DbtrAgt"), "FinInstnId"), "ClrSysMmbId"), "MmbId", t.debtorAgent());
            add(add(add(add(tx, "CdtrAgt"), "FinInstnId"), "ClrSysMmbId"), "MmbId", t.creditorAgent());
            add(add(tx, "Cdtr"), "Nm", t.creditorName());
            add(add(add(add(tx, "CdtrAcct"), "Id"), "Othr"), "Id", t.creditorAccount());
            if (t.remittance() != null) {
                add(add(tx, "RmtInf"), "Ustrd", t.remittance());
            }
        }
        return Iso20022.toXml(doc);
    }
}
