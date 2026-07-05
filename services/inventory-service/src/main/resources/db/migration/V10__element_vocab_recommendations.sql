ALTER TABLE logical_data_elements
  ADD COLUMN IF NOT EXISTS recommended_vocab_mappings   JSONB,
  ADD COLUMN IF NOT EXISTS vocab_mapping_reasoning      TEXT,
  ADD COLUMN IF NOT EXISTS vocab_mapping_recommended_at TIMESTAMPTZ;
