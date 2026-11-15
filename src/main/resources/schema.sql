-- Comptes des participants. Le solde ne peut jamais devenir négatif (contrainte en base).
CREATE TABLE IF NOT EXISTS accounts (
  id        VARCHAR(34) PRIMARY KEY,
  agent     VARCHAR(35) NOT NULL,
  currency  CHAR(3)     NOT NULL,
  balance   NUMERIC(18, 2) NOT NULL CHECK (balance >= 0),
  status    VARCHAR(10) NOT NULL
);

-- Idempotence : une ligne par transaction reçue (clé = UETR, ou institution + EndToEndId).
CREATE TABLE IF NOT EXISTS payment_outcomes (
  idem_key    VARCHAR(120) PRIMARY KEY,
  fingerprint VARCHAR(512) NOT NULL,
  status      VARCHAR(4)   NOT NULL,
  reason_code VARCHAR(4),
  updated_at  TIMESTAMPTZ  NOT NULL DEFAULT now()
);

-- Virements réglés : côté « attendu » du rapprochement.
CREATE TABLE IF NOT EXISTS settled_transfers (
  msg_id           VARCHAR(35) NOT NULL,
  end_to_end_id    VARCHAR(35) NOT NULL,
  amount           NUMERIC(18, 2) NOT NULL,
  currency         CHAR(3) NOT NULL,
  debtor_account   VARCHAR(34) NOT NULL,
  creditor_account VARCHAR(34) NOT NULL,
  settled_at       TIMESTAMPTZ NOT NULL DEFAULT now(),
  PRIMARY KEY (msg_id, end_to_end_id)
);

-- Écritures reçues par les camt.054 (consommés depuis Kafka) : côté « constaté ».
CREATE TABLE IF NOT EXISTS camt_entries (
  id            BIGSERIAL PRIMARY KEY,
  account       VARCHAR(34) NOT NULL,
  amount        NUMERIC(18, 2) NOT NULL,
  currency      CHAR(3) NOT NULL,
  credit_debit  CHAR(4) NOT NULL,
  booking_date  DATE,
  msg_id        VARCHAR(35),
  end_to_end_id VARCHAR(35)
);
