CREATE TABLE terms_policy_sets (
    id             UUID         PRIMARY KEY DEFAULT gen_random_uuid(),
    name           VARCHAR(255) NOT NULL,
    description    TEXT,
    status         VARCHAR(20)  NOT NULL DEFAULT 'DRAFT',
    version        INTEGER      NOT NULL DEFAULT 1,
    effective_from TIMESTAMPTZ,
    created_by     UUID,
    created_at     TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at     TIMESTAMPTZ  NOT NULL DEFAULT now()
);

CREATE TABLE terms_classification_rules (
    id                UUID    PRIMARY KEY DEFAULT gen_random_uuid(),
    policy_set_id     UUID    NOT NULL REFERENCES terms_policy_sets(id) ON DELETE CASCADE,
    classification    VARCHAR(50)  NOT NULL,
    rank              INTEGER NOT NULL,
    access_level      VARCHAR(50)  NOT NULL,
    permissions       JSONB   NOT NULL DEFAULT '[]',
    prohibitions      JSONB   NOT NULL DEFAULT '[]',
    obligations       JSONB   NOT NULL DEFAULT '[]',
    odrl_permissions  JSONB   NOT NULL DEFAULT '[]',
    odrl_prohibitions JSONB   NOT NULL DEFAULT '[]',
    odrl_duties       JSONB   NOT NULL DEFAULT '[]',
    UNIQUE (policy_set_id, classification)
);

CREATE TABLE terms_regulation_rules (
    id              UUID         PRIMARY KEY DEFAULT gen_random_uuid(),
    policy_set_id   UUID         NOT NULL REFERENCES terms_policy_sets(id) ON DELETE CASCADE,
    signal_type     VARCHAR(20)  NOT NULL,
    pattern         VARCHAR(255) NOT NULL,
    regulation_name VARCHAR(255) NOT NULL,
    signal_label    VARCHAR(100) NOT NULL
);

CREATE TABLE terms_regulation_obligations (
    id              UUID         PRIMARY KEY DEFAULT gen_random_uuid(),
    policy_set_id   UUID         NOT NULL REFERENCES terms_policy_sets(id) ON DELETE CASCADE,
    regulation_name VARCHAR(255) NOT NULL,
    obligation      VARCHAR(500) NOT NULL,
    odrl_duty       VARCHAR(100)
);

-- Seed the initial ACTIVE policy set, replicating all hard-coded logic from TermsOfUseService
WITH ps AS (
    INSERT INTO terms_policy_sets (name, description, status, effective_from)
    VALUES ('Default Terms Policy', 'Initial policy set seeded from built-in classification and regulation rules', 'ACTIVE', now())
    RETURNING id
)
INSERT INTO terms_classification_rules
    (policy_set_id, classification, rank, access_level, permissions, prohibitions, obligations, odrl_permissions, odrl_prohibitions, odrl_duties)
SELECT ps.id, v.classification, v.rank, v.access_level,
       v.permissions::jsonb, v.prohibitions::jsonb, v.obligations::jsonb,
       v.odrl_permissions::jsonb, v.odrl_prohibitions::jsonb, v.odrl_duties::jsonb
FROM ps, (VALUES
    ('PUBLIC', 0, 'OPEN',
     '["Use and reproduce freely","Redistribute with attribution","Incorporate into analytics and products"]',
     '[]',
     '["Cite the data source when publishing results"]',
     '["use","reproduce","distribute"]',
     '[]',
     '["attribute"]'),
    ('INTERNAL', 1, 'INTERNAL_ONLY',
     '["Use for internal analytics and reporting","Share within the organisation"]',
     '["Redistribute to external parties","Sell or sublicense data","Public disclosure"]',
     '[]',
     '["use","present"]',
     '["distribute","sell"]',
     '[]'),
    ('CONFIDENTIAL', 2, 'RESTRICTED',
     '["Use for approved internal analytics","Include in regulatory reporting submissions"]',
     '["Redistribute or share externally","Reproduce or publish data samples","Use for commercial purposes"]',
     '["Notify the data owner before use in AI/ML models","Document the intended usage purpose"]',
     '["use"]',
     '["distribute","reproduce","present"]',
     '["notify"]'),
    ('HIGH_CONFIDENTIAL', 3, 'HIGHLY_RESTRICTED',
     '["Use only with explicit written approval from the data owner","Access limited to named authorised personnel"]',
     '["Redistribute, reproduce, or present externally","Incorporate into derived data products","Use for any non-approved purpose"]',
     '["Obtain explicit consent from the data owner prior to access","Maintain an audit trail of all access and usage","Report usage to the data governance team quarterly"]',
     '["use"]',
     '["distribute","reproduce","present","modify"]',
     '["obtainConsent","notify"]')
) AS v(classification, rank, access_level, permissions, prohibitions, obligations, odrl_permissions, odrl_prohibitions, odrl_duties);

WITH ps AS (SELECT id FROM terms_policy_sets WHERE status = 'ACTIVE' LIMIT 1)
INSERT INTO terms_regulation_rules (policy_set_id, signal_type, pattern, regulation_name, signal_label)
SELECT ps.id, v.signal_type, v.pattern, v.regulation_name, v.signal_label
FROM ps, (VALUES
    ('IRI_CONTAINS', 'fibo-fbc', 'Securities & Market Regulation', 'fibo-fbc'),
    ('IRI_CONTAINS', 'fibo-sec', 'Securities & Market Regulation', 'fibo-sec'),
    ('IRI_CONTAINS', 'fibo-md',  'Market Data Licensing',          'fibo-md'),
    ('IRI_CONTAINS', 'fibo-fnd', 'Financial Foundations Standards','fibo-fnd'),
    ('KEYWORD',      'mifid',    'MiFID II Transaction Reporting',  'mifid'),
    ('KEYWORD',      'emir',     'EMIR Derivatives Reporting',      'emir'),
    ('KEYWORD',      'finrep',   'EBA FinRep Reporting',            'finrep'),
    ('KEYWORD',      'gdpr',     'GDPR Data Protection',            'gdpr'),
    ('KEYWORD',      'basel',    'Basel III Capital Requirements',   'basel')
) AS v(signal_type, pattern, regulation_name, signal_label);

WITH ps AS (SELECT id FROM terms_policy_sets WHERE status = 'ACTIVE' LIMIT 1)
INSERT INTO terms_regulation_obligations (policy_set_id, regulation_name, obligation, odrl_duty)
SELECT ps.id, 'Market Data Licensing', 'Comply with market data vendor licence terms', 'licenseMarketData'
FROM ps;
