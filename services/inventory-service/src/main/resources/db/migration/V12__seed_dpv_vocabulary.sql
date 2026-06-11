-- Seed DPV core and DPV Personal Data Categories as system vocabularies
INSERT INTO vocabularies (id, name, prefix, base_iri, vocabulary_type, description, is_system)
VALUES
  (gen_random_uuid(),
   'Data Privacy Vocabulary',
   'dpv',
   'https://w3id.org/dpv#',
   'general',
   'W3C Data Privacy Vocabulary (DPV) — concepts for data processing activities, purposes, legal bases, and data subjects.',
   true),
  (gen_random_uuid(),
   'DPV Personal Data Categories',
   'dpv-pd',
   'https://w3id.org/dpv/dpv-pd#',
   'general',
   'DPV extension for personal data categories — identifies types of personal and special category data as defined under GDPR.',
   true)
ON CONFLICT (prefix) DO NOTHING;

-- Add DPV-PD IRI signal to the active terms policy.
-- IRI_CONTAINS 'dpv-pd' fires whenever any element carries a dpv-pd: concept IRI,
-- matching the same signal_type pattern used for FIBO rules in V11.
WITH ps AS (SELECT id FROM terms_policy_sets WHERE status = 'ACTIVE' LIMIT 1)
INSERT INTO terms_regulation_rules (policy_set_id, signal_type, pattern, regulation_name, signal_label)
SELECT ps.id, 'IRI_CONTAINS', 'dpv-pd', 'GDPR Data Protection', 'DPV Personal Data Category'
FROM ps
WHERE NOT EXISTS (
  SELECT 1 FROM terms_regulation_rules r
  WHERE r.policy_set_id = ps.id AND r.pattern = 'dpv-pd'
);

-- Seed the GDPR obligation that backs the 'GDPR Data Protection' regulation piece.
-- Without this, the regulation rule fires but produces no ODRL duty in the assembled policy.
WITH ps AS (SELECT id FROM terms_policy_sets WHERE status = 'ACTIVE' LIMIT 1)
INSERT INTO terms_regulation_obligations (policy_set_id, regulation_name, obligation, odrl_duty)
SELECT ps.id,
       'GDPR Data Protection',
       'Ensure a lawful basis for processing personal data (GDPR Article 6)',
       'obtainConsent'
FROM ps
WHERE NOT EXISTS (
  SELECT 1 FROM terms_regulation_obligations o
  WHERE o.policy_set_id = ps.id AND o.regulation_name = 'GDPR Data Protection'
);
