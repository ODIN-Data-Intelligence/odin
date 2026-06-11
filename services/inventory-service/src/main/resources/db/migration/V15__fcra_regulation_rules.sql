-- Add FCRA (Fair Credit Reporting Act) regulation detection rules and obligations
-- to the default ACTIVE policy set.
--
-- FCRA governs the collection, dissemination, and use of consumer information,
-- including credit history, payment history, and any data used for credit, insurance,
-- employment, or tenant-screening decisions.
--
-- Signal: HAS_PII_ELEMENTS fires when the dataset has at least one logical data
-- element marked isPersonalInformation=true, indicating consumer personal data
-- that may be subject to FCRA consumer-report obligations.

WITH ps AS (SELECT id FROM terms_policy_sets WHERE status = 'ACTIVE' LIMIT 1)
INSERT INTO terms_regulation_rules (policy_set_id, signal_type, pattern, regulation_name, signal_label)
SELECT ps.id, v.signal_type, v.pattern, v.regulation_name, v.signal_label
FROM ps, (VALUES
    ('HAS_PII_ELEMENTS', 'pii', 'FCRA Consumer Data Protection', 'Personal data elements present'),
    ('KEYWORD',          'credit', 'FCRA Consumer Data Protection', 'credit')
) AS v(signal_type, pattern, regulation_name, signal_label)
WHERE NOT EXISTS (
    SELECT 1 FROM terms_regulation_rules r
    WHERE r.policy_set_id = ps.id
      AND r.regulation_name = 'FCRA Consumer Data Protection'
);

WITH ps AS (SELECT id FROM terms_policy_sets WHERE status = 'ACTIVE' LIMIT 1)
INSERT INTO terms_regulation_obligations (policy_set_id, regulation_name, obligation, odrl_duty)
SELECT ps.id, v.regulation_name, v.obligation, v.odrl_duty
FROM ps, (VALUES
    ('FCRA Consumer Data Protection',
     'Ensure permissible purpose for any consumer report use; provide adverse action notice when required',
     'obtainConsent'),
    ('FCRA Consumer Data Protection',
     'Maintain data accuracy and provide consumer dispute resolution procedures',
     'attribute')
) AS v(regulation_name, obligation, odrl_duty)
WHERE NOT EXISTS (
    SELECT 1 FROM terms_regulation_obligations o
    WHERE o.policy_set_id = ps.id
      AND o.regulation_name = 'FCRA Consumer Data Protection'
);
