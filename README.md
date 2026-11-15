# rtr-lab

[![CI](https://github.com/peacedayou/rtr-lab/actions/workflows/ci.yml/badge.svg)](https://github.com/peacedayou/rtr-lab/actions/workflows/ci.yml)

Simulateur de **paiements en temps réel ISO 20022**, sur le modèle du Real-Time Rail canadien :
un `pacs.008` entre, chaque virement est contrôlé puis réglé ou rejeté avec un code ISO, un
`pacs.002` répond, un `camt.054` avise les titulaires des comptes, et un rapprochement vérifie
que tout concorde.

**Démo :** https://peacedayou.github.io/rtr-lab/ (page générée par le vrai moteur à chaque déploiement).
Démo complète en local : `docker compose up --build`.

## Ce que le projet montre

| Sujet | Où regarder |
| --- | --- |
| Lecture et écriture de `pacs.008.001.08`, `pacs.002.001.10`, `camt.054.001.08` | `iso/Pacs008`, `Pacs002`, `Camt054` |
| **Validation XSD** de chaque message entrant et sortant, analyseur durci contre XXE | `iso/XsdValidator`, `iso/Iso20022` |
| **Rejets** avec codes ISO : AC01, AC03, AC04, AC06, AM02, AM03, AM04, AM05, AM09, RC01, FF01 | `engine/ReasonCode`, `PaymentEngine.check` |
| **Idempotence** : un renvoi identique retrouve son état sans nouveau débit ; même référence et contenu différent → AM05 ; réservation PDNG contre les doubles soumissions simultanées | `engine/PaymentEngine`, table `payment_outcomes` |
| Règlement atomique, **jamais de découvert** même en parallèle (débit conditionnel + contrainte en base) | `app/JdbcLedger`, `schema.sql` |
| **Kafka** : publication des pacs.002 et camt.054 ; un consommateur joue le système comptable du client | `app/PaymentService`, `app/Camt054Listener` |
| **Rapprochement** : manquant, en double, montant ou compte différent, inattendu | `engine/Reconciler`, `GET /api/rapprochement` |
| Tests unitaires et **Testcontainers** (vrais PostgreSQL et Kafka) | `src/test` |

## Architecture

```
            pacs.008 (XML)                        Kafka
  client ──────────────► POST /api/pacs008 ──► rtr.pacs002 ──► (banque émettrice)
                              │                rtr.camt054 ──► Camt054Listener ──► camt_entries
                              ▼                                                        │
                       PaymentEngine ── JdbcLedger / JdbcIdempotencyStore (PostgreSQL) │
                              │                                                        ▼
                              └──────────────► settled_transfers ──► Reconciler ◄──────┘
```

Le cœur (`iso`, `engine`) n'utilise que le JDK : il se teste sans serveur. La couche `app`
branche Spring Boot, PostgreSQL et Kafka.

## API

| Méthode | Chemin | Rôle |
| --- | --- | --- |
| `POST` | `/api/pacs008` | Reçoit un pacs.008 (XML), répond par le pacs.002. Message non conforme : `400` et pacs.002 de rejet `FF01`, détail dans l'en-tête `X-Rejet-Detail` |
| `GET` | `/api/comptes` | Soldes des comptes de démonstration |
| `GET` | `/api/rapprochement` | Rapport de rapprochement |

```bash
docker compose up --build
curl -s -X POST -H "Content-Type: application/xml" \
  --data-binary @src/main/resources/exemples/pacs008-lot.xml http://localhost:8080/api/pacs008
curl -s http://localhost:8080/api/rapprochement
```

## Tests

```bash
mvn verify   # Docker requis pour les tests Testcontainers
```

La CI GitHub Actions lance tous les tests, puis démarre la démo `docker compose`, envoie le lot
d'exemple et vérifie que le rapprochement est équilibré.

## Limites, en toute transparence

- Les schémas XSD fournis sont des **sous-ensembles pédagogiques** des schémas officiels (mêmes
  noms, même espace de noms, même ordre, limités aux champs utilisés). Pour une validation
  complète, remplacez-les par les XSD publiés sur iso20022.org.
- Le simulateur ne reproduit ni les règles d'usage propres au Real-Time Rail, ni la signature
  des messages, ni la gestion des liquidités entre participants.
- Toutes les données (institutions, comptes, personnes) sont fictives.

## Licence

MIT
