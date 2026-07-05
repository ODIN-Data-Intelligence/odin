ALTER TABLE logical_data_elements
  ADD COLUMN classification               TEXT
      CHECK (classification IN ('PUBLIC','INTERNAL','CONFIDENTIAL','HIGH_CONFIDENTIAL')),
  ADD COLUMN recommended_classification   TEXT
      CHECK (recommended_classification IN ('PUBLIC','INTERNAL','CONFIDENTIAL','HIGH_CONFIDENTIAL')),
  ADD COLUMN classification_reasoning     TEXT,
  ADD COLUMN classification_recommended_at TIMESTAMPTZ;
