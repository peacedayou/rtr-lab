package io.github.peacedayou.rtrlab.engine;

/**
 * Codes de raison ISO 20022 (liste externe ExternalStatusReason1Code) utilisés par le simulateur.
 * Le libellé français est celui affiché dans les rapports.
 */
public enum ReasonCode {
    AC01("Numéro de compte du débiteur inconnu"),
    AC03("Numéro de compte du créancier inconnu"),
    AC04("Compte fermé"),
    AC06("Compte bloqué"),
    AM02("Montant supérieur à la limite autorisée"),
    AM03("Devise non autorisée"),
    AM04("Provision insuffisante"),
    AM05("Doublon : même référence, contenu différent"),
    AM09("Montant invalide"),
    FF01("Format de message invalide"),
    RC01("Identifiant d'institution invalide");

    private final String label;

    ReasonCode(String label) {
        this.label = label;
    }

    public String label() {
        return label;
    }
}
