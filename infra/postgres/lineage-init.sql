-- Load the Apache AGE extension and create the lineage graph
CREATE EXTENSION IF NOT EXISTS age;
LOAD 'age';
SET search_path = ag_catalog, "$user", public;
SELECT create_graph('lineage_graph');
