ALTER TABLE logical_data_elements
  ADD COLUMN is_personal_information             BOOLEAN     NOT NULL DEFAULT FALSE,
  ADD COLUMN is_direct_identifier                BOOLEAN     NOT NULL DEFAULT FALSE,
  ADD COLUMN recommended_is_personal_information BOOLEAN,
  ADD COLUMN recommended_is_direct_identifier    BOOLEAN,
  ADD COLUMN pii_recommendation_reasoning        TEXT,
  ADD COLUMN pii_recommended_at                  TIMESTAMPTZ;
