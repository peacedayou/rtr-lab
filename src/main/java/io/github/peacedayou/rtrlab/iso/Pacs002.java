package io.github.peacedayou.rtrlab.iso;

import static io.github.peacedayou.rtrlab.iso.Iso20022.add;

import java.time.OffsetDateTime;
import java.util.List;
import org.w3c.dom.Element;

/** Écriture d'un pacs.002 (rapport d'état de paiement). */
public final class Pacs002 {

    /** État d'une transaction : ACSC (réglée) ou RJCT (rejetée), avec le code ISO de la raison. */
    public record TxStatus(String endToEndId, String uetr, String status, String reasonCode, String reasonText) {}

    private Pacs002() {}

    public static String write(String msgId, OffsetDateTime createdAt, String originalMsgId, String groupStatus,
                               String groupReason, List<TxStatus> statuses) {
        var doc = Iso20022.newDocument();
        Element root = doc.createElementNS(Iso20022.ns(Iso20022.PACS_002), "Document");
        doc.appendChild(root);
        Element body = add(root, "FIToFIPmtStsRpt");
        Element hdr = add(body, "GrpHdr");
        add(hdr, "MsgId", msgId);
        add(hdr, "CreDtTm", Iso20022.dateTime(createdAt));
        Element orig = add(body, "OrgnlGrpInfAndSts");
        add(orig, "OrgnlMsgId", originalMsgId);
        add(orig, "OrgnlMsgNmId", Iso20022.PACS_008);
        if (groupStatus != null) {
            add(orig, "GrpSts", groupStatus);
        }
        if (groupReason != null) {
            Element rsn = add(orig, "StsRsnInf");
            add(add(rsn, "Rsn"), "Cd", groupReason);
        }
        for (TxStatus s : statuses) {
            Element tx = add(body, "TxInfAndSts");
            add(tx, "OrgnlEndToEndId", s.endToEndId());
            if (s.uetr() != null) {
                add(tx, "OrgnlUETR", s.uetr());
            }
            add(tx, "TxSts", s.status());
            if (s.reasonCode() != null) {
                Element rsn = add(tx, "StsRsnInf");
                add(add(rsn, "Rsn"), "Cd", s.reasonCode());
                if (s.reasonText() != null) {
                    add(rsn, "AddtlInf", s.reasonText().length() > 105 ? s.reasonText().substring(0, 105) : s.reasonText());
                }
            }
        }
        String xml = Iso20022.toXml(doc);
        XsdValidator.requireValid(Iso20022.PACS_002, xml);
        return xml;
    }
}
