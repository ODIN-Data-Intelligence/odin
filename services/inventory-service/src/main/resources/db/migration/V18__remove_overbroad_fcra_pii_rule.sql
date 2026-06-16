-- V15 seeded a HAS_PII_ELEMENTS -> FCRA Consumer Data Protection rule that fires whenever a
-- dataset has ANY logical element flagged isPersonalInformation=true, regardless of context.
-- FCRA specifically governs consumer reports used by credit bureaus / background-check agencies
-- for credit, employment-screening, or insurance-eligibility decisions — not every dataset that
-- merely contains personal information (that's GDPR's domain, already covered by a separate rule).
-- This caused false positives such as tagging an internal employee/HR directory as FCRA-governed.
--
-- The KEYWORD/credit rule for FCRA (also seeded in V15) is correctly scoped and is left in place.
DELETE FROM terms_regulation_rules
WHERE signal_type = 'HAS_PII_ELEMENTS'
  AND pattern = 'pii'
  AND regulation_name = 'FCRA Consumer Data Protection';
