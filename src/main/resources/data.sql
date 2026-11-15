-- Comptes de démonstration (institutions fictives 0815 et 0006).
INSERT INTO accounts (id, agent, currency, balance, status) VALUES
  ('CA-815-0001', '0815', 'CAD', 1000.00, 'OPEN'),
  ('CA-815-0002', '0815', 'CAD',   50.00, 'OPEN'),
  ('CA-006-0001', '0006', 'CAD',    0.00, 'OPEN'),
  ('CA-006-0099', '0006', 'CAD',    0.00, 'CLOSED')
ON CONFLICT (id) DO NOTHING;
