ALTER TABLE logical_data_elements
  ADD COLUMN recommended_description       TEXT,
  ADD COLUMN description_reasoning         TEXT,
  ADD COLUMN description_recommended_at    TIMESTAMPTZ;
