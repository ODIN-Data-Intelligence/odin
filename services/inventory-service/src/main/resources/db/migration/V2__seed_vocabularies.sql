INSERT INTO vocabularies (id, name, prefix, base_iri, vocabulary_type, description, is_system) VALUES
  (uuid_generate_v4(), 'schema.org',  'schema',   'https://schema.org/',                                              'general',   'General-purpose vocabulary by schema.org', TRUE),
  (uuid_generate_v4(), 'FIBO FND',    'fibo-fnd', 'https://spec.edmcouncil.org/fibo/ontology/FND/',                  'financial', 'FIBO Foundations (FND)',                    TRUE),
  (uuid_generate_v4(), 'FIBO FBC',    'fibo-fbc', 'https://spec.edmcouncil.org/fibo/ontology/FBC/',                  'financial', 'FIBO Financial Business and Commerce (FBC)', TRUE),
  (uuid_generate_v4(), 'FIBO SEC',    'fibo-sec', 'https://spec.edmcouncil.org/fibo/ontology/SEC/',                  'financial', 'FIBO Securities (SEC)',                      TRUE),
  (uuid_generate_v4(), 'FIBO MD',     'fibo-md',  'https://spec.edmcouncil.org/fibo/ontology/MD/',                   'financial', 'FIBO Market Data (MD)',                      TRUE),
  (uuid_generate_v4(), 'FIBO BP',     'fibo-bp',  'https://spec.edmcouncil.org/fibo/ontology/BP/',                   'financial', 'FIBO Business Processes (BP)',               TRUE),
  (uuid_generate_v4(), 'SKOS',        'skos',     'http://www.w3.org/2004/02/skos/core#',                            'general',   'W3C SKOS Simple Knowledge Organization System', TRUE),
  (uuid_generate_v4(), 'GeoSPARQL',   'geo',      'http://www.opengis.net/ont/geosparql#',                           'geospatial','OGC GeoSPARQL ontology',                    TRUE),
  (uuid_generate_v4(), 'Dublin Core', 'dcterms',  'http://purl.org/dc/terms/',                                       'general',   'Dublin Core Metadata Terms',                 TRUE);
