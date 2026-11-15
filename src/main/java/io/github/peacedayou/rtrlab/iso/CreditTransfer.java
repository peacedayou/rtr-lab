package io.github.peacedayou.rtrlab.iso;

import java.math.BigDecimal;

/** Une transaction CdtTrfTxInf d'un pacs.008, avec l'identifiant du message qui la porte. */
public record CreditTransfer(
        String msgId,
        String endToEndId,
        String uetr,
        BigDecimal amount,
        String currency,
        String debtorName,
        String debtorAccount,
        String debtorAgent,
        String creditorName,
        String creditorAccount,
        String creditorAgent,
        String remittance) {

    /** Empreinte du contenu métier : sert à distinguer un renvoi identique d'un conflit. */
    public String fingerprint() {
        return String.join("|", endToEndId, String.valueOf(uetr), amount.stripTrailingZeros().toPlainString(), currency,
                debtorAccount, debtorAgent, creditorAccount, creditorAgent);
    }
}
