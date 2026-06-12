package com.odin.catalog.ai.tools;

import com.odin.catalog.ai.client.CatalogServiceClient;
import dev.langchain4j.model.ollama.OllamaChatModel;
import dev.langchain4j.service.AiServices;
import dev.langchain4j.service.SystemMessage;
import dev.langchain4j.service.UserMessage;
import dev.langchain4j.service.V;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

@Service
public class DatasetContextService {

    private static final Logger log = LoggerFactory.getLogger(DatasetContextService.class);

    interface DatasetContextAgent {
        @SystemMessage("""
            You are a data catalog assistant. Use the available tools to retrieve complete \
            information about the requested dataset and return a concise, factual summary. \
            Always call getDatasetInfo first, then getLogicalElementsWithVocabulary. \
            When the user asks to write, generate, or suggest a SQL query: \
            - Call getQueryContext for EVERY dataset referenced in the query before writing any SQL. \
            - Only use column names that appear in the retrieved schema. Never invent column names. \
            - Each dataset block states its Platform. Generate SQL using only the syntax and \
              built-in functions of that platform. \
            - If the PLATFORM CONFLICT section is present, write one separate query per platform \
              group — never a single SQL statement that mixes tables from different platforms. \
            - Use the Join Hints section (if present) for join predicates. \
            - If schema for a required dataset is not loaded, call findRelatedDatasets to locate it first. \
            - If the user specifically asks for Snowflake SQL, call getSnowflakeQueryContext \
              to get the Snowflake account, fully-qualified table name (DATABASE.SCHEMA.TABLE), \
              and column schema before writing the query. \
            Do not add commentary — return only the retrieved facts and any requested SQL.
            """)
        String gatherContext(@UserMessage @V("prompt") String prompt);
    }

    private final DatasetContextAgent agent;
    private final CatalogServiceClient catalogClient;

    public DatasetContextService(OllamaChatModel ollamaChatModel,
                                 DatasetContextTool contextTool,
                                 SqlGenerationTool sqlTool,
                                 CatalogServiceClient catalogClient) {
        this.agent = AiServices.builder(DatasetContextAgent.class)
            .chatLanguageModel(ollamaChatModel)
            .tools(contextTool, sqlTool)
            .build();
        this.catalogClient = catalogClient;
    }

    /**
     * Builds focused context for one or more datasets.
     * For a single dataset, delegates to the tool-chain agent (same as before).
     * For multiple datasets, assembles schema blocks for each, performs platform resolution
     * (single-platform header vs. PLATFORM CONFLICT warning), then appends join hints.
     */
    public String buildFocusedContext(List<String> datasetIds, String userQuery, String conversationId) {
        if (datasetIds == null || datasetIds.isEmpty()) return "";

        List<String> ids = new ArrayList<>(new LinkedHashSet<>(datasetIds));

        MDC.put("conversationId", conversationId);

        if (ids.size() == 1) {
            return buildSingleDatasetContext(ids.get(0), userQuery, conversationId);
        }

        try {
            log.info("step=MULTI_DATASET_CONTEXT_START datasetCount={} ids={}", ids.size(), ids);
            long t = System.currentTimeMillis();

            List<DatasetSchemaBlock> blocks = ids.stream()
                .map(id -> buildSchemaBlock(id, userQuery))
                .filter(b -> b != null)
                .collect(Collectors.toList());

            if (blocks.isEmpty()) return "";

            // ── Platform resolution ────────────────────────────────────────────
            Set<String> distinctPlatforms = blocks.stream()
                .map(DatasetSchemaBlock::platform)
                .filter(p -> p != null && !p.isBlank())
                .collect(Collectors.toCollection(LinkedHashSet::new));

            StringBuilder sb = new StringBuilder();

            if (distinctPlatforms.size() <= 1) {
                // All datasets on the same platform — emit a single target platform header
                String platform = distinctPlatforms.isEmpty() ? "Unknown" : distinctPlatforms.iterator().next();
                sb.append("Target Platform: ").append(platform).append('\n');
                sb.append("Write all SQL using ").append(platform).append(" syntax only.\n\n");
            } else {
                // Mixed platforms — emit a conflict warning so the LLM doesn't blend them
                sb.append("=== PLATFORM CONFLICT ===\n");
                sb.append("The selected datasets are on DIFFERENT platforms and cannot be queried ")
                  .append("in a single SQL statement:\n");
                for (DatasetSchemaBlock block : blocks) {
                    sb.append("  - ").append(block.title())
                      .append(" → ").append(block.platform() != null ? block.platform() : "Unknown")
                      .append(" (").append(block.tableName()).append(")\n");
                }
                sb.append("Generate one query per platform group with a clear platform header for each.\n");
                sb.append("If the user requested a specific platform, generate only the query for that platform.\n");
                sb.append("DO NOT write a single SQL statement that references tables from different platforms.\n\n");
                log.info("step=MULTI_DATASET_PLATFORM_CONFLICT platforms={}", distinctPlatforms);
            }

            // ── Dataset schema blocks ──────────────────────────────────────────
            for (int i = 0; i < blocks.size(); i++) {
                sb.append("=== DATASET ").append(i + 1).append(": ").append(blocks.get(i).title())
                  .append(" ===\n").append(blocks.get(i).schemaText()).append("\n\n");
            }

            // ── Join hints ────────────────────────────────────────────────────
            String joinHints = buildJoinHints(blocks);
            if (!joinHints.isBlank()) {
                sb.append(joinHints);
            }

            String result = sb.toString().strip();
            log.info("step=MULTI_DATASET_CONTEXT_COMPLETE datasetCount={} platforms={} result.length={} elapsed={}ms",
                ids.size(), distinctPlatforms, result.length(), System.currentTimeMillis() - t);
            return result;

        } catch (Exception e) {
            log.warn("step=MULTI_DATASET_CONTEXT_ERROR error={}", e.getMessage());
            return "";
        } finally {
            MDC.remove("conversationId");
        }
    }

    /** Single-dataset path: use the tool-chain agent (same as before). */
    private String buildSingleDatasetContext(String datasetId, String userQuery, String conversationId) {
        MDC.put("conversationId", conversationId);
        DatasetIdContext.set(datasetId);
        try {
            log.info("step=TOOL_CHAIN_INVOKE datasetId={} agent=DatasetContextAgent", datasetId);
            String result = agent.gatherContext(
                "Retrieve information for dataset ID: " + datasetId +
                ". The user is asking: " + userQuery
            );
            log.info("step=TOOL_CHAIN_COMPLETE datasetId={} result.length={}", datasetId, result.length());
            return result;
        } catch (Exception e) {
            log.warn("step=TOOL_CHAIN_ERROR datasetId={} error={}", datasetId, e.getMessage());
            return "";
        } finally {
            DatasetIdContext.clear();
            MDC.remove("conversationId");
        }
    }

    /**
     * Assembles a schema block for one dataset: summary, logical model, distribution,
     * physical columns. Detects the platform from the selected distribution.
     */
    private DatasetSchemaBlock buildSchemaBlock(String datasetId, String userQuery) {
        try {
            DatasetIdContext.set(datasetId);

            CatalogServiceClient.DatasetSummary ds = catalogClient.getDataset(datasetId);
            String title = ds != null ? ds.title() : datasetId;

            Map<String, CatalogServiceClient.LogicalElement> elementById = buildElementMap(datasetId);

            List<CatalogServiceClient.Distribution> distributions = catalogClient.getDistributions(datasetId);
            CatalogServiceClient.Distribution dist = distributions.isEmpty() ? null : selectBestDistribution(distributions);

            String platform = dist != null ? detectPlatform(dist) : "Unknown";

            List<CatalogServiceClient.PhysicalColumn> columns = List.of();
            if (dist != null) {
                columns = catalogClient.getDistributionPhysicalSchema(dist.id());
                if (columns.isEmpty()) columns = catalogClient.getDatasetPhysicalSchema(datasetId);
            }

            List<ColumnEntry> columnEntries = new ArrayList<>();
            for (CatalogServiceClient.PhysicalColumn col : columns.stream()
                    .sorted(Comparator.comparingInt(c -> c.ordinal() != null ? c.ordinal() : 0))
                    .toList()) {
                CatalogServiceClient.LogicalElement el =
                    col.logicalDataElementId() != null ? elementById.get(col.logicalDataElementId()) : null;
                List<String> conceptIris = el != null && el.vocabMappings() != null
                    ? el.vocabMappings().stream().map(CatalogServiceClient.VocabMapping::conceptIri).collect(Collectors.toList())
                    : List.of();
                columnEntries.add(new ColumnEntry(col.name(), col.datatype(), Boolean.TRUE.equals(col.required()),
                    el != null ? el.name() : null, conceptIris));
            }

            StringBuilder sb = new StringBuilder();
            if (ds != null) {
                sb.append("Title: ").append(ds.title()).append('\n');
                if (ds.description() != null) sb.append("Description: ").append(ds.description()).append('\n');
            }
            sb.append("Platform: ").append(platform).append('\n');
            if (dist != null) {
                String tableName = extractTableName(dist);
                sb.append("Table: ").append(tableName).append('\n');
                if (dist.accessUrl() != null) sb.append("Access URL: ").append(dist.accessUrl()).append('\n');
            }

            if (!columnEntries.isEmpty()) {
                sb.append("Columns (").append(columnEntries.size()).append("):\n");
                for (ColumnEntry ce : columnEntries) {
                    sb.append("  - ").append(ce.name());
                    if (ce.datatype() != null) sb.append(" [").append(ce.datatype()).append(']');
                    if (ce.required()) sb.append(" NOT NULL");
                    if (ce.logicalName() != null) sb.append(" → ").append(ce.logicalName());
                    sb.append('\n');
                }
            } else {
                sb.append("(No physical column schema defined)\n");
            }

            String tableName = dist != null ? extractTableName(dist) : datasetId;
            return new DatasetSchemaBlock(datasetId, title, tableName, platform, sb.toString().strip(), columnEntries);

        } catch (Exception e) {
            log.warn("step=SCHEMA_BLOCK_ERROR datasetId={} error={}", datasetId, e.getMessage());
            return null;
        } finally {
            DatasetIdContext.clear();
        }
    }

    /**
     * Derives join hints by finding columns that share the same vocabulary concept IRI
     * across two or more datasets.
     */
    private String buildJoinHints(List<DatasetSchemaBlock> blocks) {
        if (blocks.size() < 2) return "";

        Map<String, List<String[]>> iriToColumns = new LinkedHashMap<>();
        for (DatasetSchemaBlock block : blocks) {
            for (ColumnEntry col : block.columns()) {
                for (String iri : col.conceptIris()) {
                    iriToColumns.computeIfAbsent(iri, k -> new ArrayList<>())
                        .add(new String[]{ block.title(), block.tableName(), col.name() });
                }
            }
        }

        List<Map.Entry<String, List<String[]>>> joinCandidates = iriToColumns.entrySet().stream()
            .filter(e -> {
                Set<String> titles = e.getValue().stream().map(a -> a[0]).collect(Collectors.toSet());
                return titles.size() >= 2;
            })
            .collect(Collectors.toList());

        if (joinCandidates.isEmpty()) return "";

        StringBuilder sb = new StringBuilder();
        sb.append("=== JOIN HINTS (derived from shared vocabulary concepts) ===\n");

        for (Map.Entry<String, List<String[]>> entry : joinCandidates) {
            List<String[]> occurrences = entry.getValue();
            sb.append("Shared concept: ").append(entry.getKey()).append('\n');
            for (String[] occ : occurrences) {
                sb.append("  ").append(occ[0]).append(" [").append(occ[1]).append("] → column: ").append(occ[2]).append('\n');
            }
            if (occurrences.size() >= 2) {
                sb.append("  Suggested join: ").append(occurrences.get(0)[1]).append('.').append(occurrences.get(0)[2])
                  .append(" = ").append(occurrences.get(1)[1]).append('.').append(occurrences.get(1)[2]).append('\n');
            }
        }

        return sb.toString();
    }

    // ── helpers ───────────────────────────────────────────────────────────────

    private Map<String, CatalogServiceClient.LogicalElement> buildElementMap(String datasetId) {
        List<CatalogServiceClient.LogicalModel> models = catalogClient.getLogicalModels(datasetId);
        if (models == null || models.isEmpty()) return Map.of();
        CatalogServiceClient.LogicalModel model = models.stream()
            .min(Comparator.comparingInt(m -> modelStatusPriority(m.status())))
            .orElse(models.get(0));
        List<CatalogServiceClient.LogicalElement> elements = catalogClient.getLogicalElements(model.id());
        if (elements == null || elements.isEmpty()) return Map.of();
        return elements.stream().collect(Collectors.toMap(
            CatalogServiceClient.LogicalElement::id, e -> e, (a, b) -> a));
    }

    private CatalogServiceClient.Distribution selectBestDistribution(List<CatalogServiceClient.Distribution> dists) {
        return dists.stream().min(Comparator.comparingInt(d -> platformPriority(d.format(), d.accessUrl())))
            .orElse(dists.get(0));
    }

    private int platformPriority(String format, String url) {
        String f = format != null ? format.toLowerCase() : "";
        String u = url    != null ? url.toLowerCase()    : "";
        if (f.equals("snowflake") || u.contains("snowflakecomputing.com")) return 0;
        if (f.equals("delta")     || u.contains("databricks"))             return 1;
        if (u.startsWith("jdbc:teradata://") || u.contains("teradata"))    return 2;
        if (f.equals("parquet")   || u.startsWith("s3://"))                return 3;
        if (f.equals("csv"))                                                return 5;
        return 6;
    }

    private String detectPlatform(CatalogServiceClient.Distribution d) {
        String f = d.format()    != null ? d.format().toLowerCase()    : "";
        String u = d.accessUrl() != null ? d.accessUrl().toLowerCase() : "";
        if (f.equals("snowflake") || u.contains("snowflakecomputing.com"))       return "Snowflake";
        if (u.startsWith("jdbc:teradata://") || u.contains("teradata"))          return "Teradata";
        if (u.contains("bigquery.googleapis.com") || u.startsWith("bigquery://")) return "Google BigQuery";
        if (u.contains("databricks") || f.equals("delta"))                       return "Databricks / Delta Lake";
        if (u.startsWith("s3://") && (u.contains("athena") || u.contains("glue"))) return "AWS Athena";
        if (u.startsWith("s3://"))                                                return "AWS S3";
        if (f.equals("parquet"))                                                  return "Parquet";
        if (f.equals("csv"))                                                      return "CSV";
        if (f.equals("kafka"))                                                    return "Kafka";
        return "Standard SQL";
    }

    private String extractTableName(CatalogServiceClient.Distribution d) {
        if (d.databaseName() != null || d.schemaName() != null || d.tableName() != null) {
            StringBuilder fqn = new StringBuilder();
            if (d.databaseName() != null) fqn.append(d.databaseName());
            if (d.schemaName()   != null) { if (!fqn.isEmpty()) fqn.append('.'); fqn.append(d.schemaName()); }
            if (d.tableName()    != null) { if (!fqn.isEmpty()) fqn.append('.'); fqn.append(d.tableName()); }
            return fqn.toString();
        }
        String title = d.title() != null ? d.title() : "dataset_table";
        return title.replaceAll("[^a-zA-Z0-9_.\\-]", "_");
    }

    private static int modelStatusPriority(String s) {
        return switch (s == null ? "" : s) {
            case "published" -> 0; case "draft" -> 1; case "deprecated" -> 2; default -> 3;
        };
    }

    // ── internal data holders ─────────────────────────────────────────────────

    record ColumnEntry(String name, String datatype, boolean required, String logicalName, List<String> conceptIris) {}

    record DatasetSchemaBlock(String datasetId, String title, String tableName,
                              String platform, String schemaText, List<ColumnEntry> columns) {}
}
