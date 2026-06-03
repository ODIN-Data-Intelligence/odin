package com.odin.catalog.ai.tools;

import com.odin.catalog.ai.client.CatalogServiceClient;
import dev.langchain4j.agent.tool.Tool;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Component
@RequiredArgsConstructor
public class SqlGenerationTool {

    private static final Logger log = LoggerFactory.getLogger(SqlGenerationTool.class);

    private final CatalogServiceClient catalogClient;

    @Tool("""
        Retrieve platform type, table access information, and column schema for the current dataset \
        to support SQL query generation. Traverses from dataset to its distributions, selects \
        the most query-friendly distribution (Snowflake > Teradata > Delta > Parquet > CSV), \
        fetches its CSV-W physical schema, and joins each column to its bound logical data element \
        (business name, logical type, vocabulary concept). Call this tool when the user asks to \
        write, generate, or suggest a SQL query against a dataset.
        """)
    public String getQueryContext() {
        String datasetId = DatasetIdContext.get();
        long start = System.currentTimeMillis();
        log.info("step=TOOL_CALL tool=getQueryContext input.datasetId={}", datasetId);

        if (datasetId == null || datasetId.isBlank()) {
            log.warn("step=TOOL_RESULT tool=getQueryContext result=NO_DATASET_CONTEXT");
            return "No dataset is currently in focus. Cannot generate a SQL query.";
        }

        // ── 1. distributions ──────────────────────────────────────────────────
        List<CatalogServiceClient.Distribution> distributions = catalogClient.getDistributions(datasetId);
        if (distributions.isEmpty()) {
            String result = "No distributions found for dataset " + datasetId +
                ". Cannot generate a SQL query without a physical access point.";
            log.info("step=TOOL_RESULT tool=getQueryContext result=NO_DISTRIBUTIONS elapsed={}ms", elapsed(start));
            return result;
        }

        CatalogServiceClient.Distribution dist = selectBestDistribution(distributions);
        log.info("step=TOOL_CALL_DETAIL tool=getQueryContext selectedDistribution={} format={} platform={}",
            dist.id(), dist.format(), detectPlatform(dist));

        // ── 2. physical schema ────────────────────────────────────────────────
        List<CatalogServiceClient.PhysicalColumn> columns = catalogClient.getDistributionPhysicalSchema(dist.id());
        if (columns.isEmpty()) {
            columns = catalogClient.getDatasetPhysicalSchema(datasetId);
        }

        // ── 3. logical elements (for column→concept join) ─────────────────────
        Map<String, CatalogServiceClient.LogicalElement> elementById = buildElementMap(datasetId);

        // ── 4. assemble context ───────────────────────────────────────────────
        String platform  = detectPlatform(dist);
        String tableName = extractTableName(dist);

        var sb = new StringBuilder();
        sb.append("Platform: ").append(platform).append('\n');
        sb.append("Distribution: ").append(dist.title() != null ? dist.title() : dist.id()).append('\n');
        if (dist.format()    != null) sb.append("Format: ").append(dist.format()).append('\n');
        if (dist.accessUrl() != null) sb.append("Access URL: ").append(dist.accessUrl()).append('\n');
        else if (dist.downloadUrl() != null) sb.append("Download URL: ").append(dist.downloadUrl()).append('\n');
        sb.append("Table / Object: ").append(tableName).append('\n');

        if (columns.isEmpty()) {
            sb.append("\nNo physical column schema defined for this distribution.");
        } else {
            sb.append("\nColumns (").append(columns.size()).append("):\n");
            List<CatalogServiceClient.PhysicalColumn> ordered = columns.stream()
                .sorted(Comparator.comparingInt(c -> c.ordinal() != null ? c.ordinal() : 0))
                .toList();
            for (var col : ordered) {
                sb.append("  - ").append(col.name());
                if (col.datatype() != null) sb.append(" [").append(col.datatype()).append(']');
                if (Boolean.TRUE.equals(col.required())) sb.append(" NOT NULL");

                if (col.logicalDataElementId() != null) {
                    CatalogServiceClient.LogicalElement el = elementById.get(col.logicalDataElementId());
                    if (el != null) {
                        sb.append("\n      → ").append(el.name());
                        if (el.label() != null && !el.label().equals(el.name()))
                            sb.append(" (").append(el.label()).append(')');
                        if (el.logicalType() != null)
                            sb.append(" [").append(el.logicalType()).append(']');
                        if (el.vocabMappings() != null && !el.vocabMappings().isEmpty()) {
                            var vm = el.vocabMappings().get(0);
                            if (vm.conceptLabel() != null)
                                sb.append(" — ").append(vm.conceptLabel())
                                  .append(" (").append(vm.matchType()).append(": ").append(vm.conceptIri()).append(')');
                        }
                    }
                }
                sb.append('\n');
            }
        }

        if (distributions.size() > 1) {
            sb.append("\nOther available distributions:\n");
            distributions.stream()
                .filter(d -> !d.id().equals(dist.id()))
                .forEach(d -> {
                    sb.append("  - ");
                    sb.append(d.title() != null ? d.title() : d.id());
                    if (d.format() != null) sb.append(" [").append(d.format()).append(']');
                    sb.append('\n');
                });
        }

        String result = sb.toString().strip();
        log.info("step=TOOL_RESULT tool=getQueryContext platform={} columns.count={} output.length={} elapsed={}ms",
            platform, columns.size(), result.length(), elapsed(start));
        log.debug("step=TOOL_RESULT tool=getQueryContext output={}", result);
        return result;
    }

    @Tool("""
        Retrieve Snowflake-specific table schema for the current dataset and return context \
        for writing a Snowflake SQL query. Finds the Snowflake distribution exclusively \
        (format=Snowflake or account URL containing snowflakecomputing.com), extracts the \
        account identifier and fully-qualified object name (DATABASE.SCHEMA.TABLE), and lists \
        every physical column with its Snowflake data type and bound logical data element. \
        Call this tool when the user explicitly asks for a Snowflake SQL query or SELECT statement. \
        Returns an error if the dataset has no Snowflake distribution.
        """)
    public String getSnowflakeQueryContext() {
        String datasetId = DatasetIdContext.get();
        long start = System.currentTimeMillis();
        log.info("step=TOOL_CALL tool=getSnowflakeQueryContext input.datasetId={}", datasetId);

        if (datasetId == null || datasetId.isBlank()) {
            log.warn("step=TOOL_RESULT tool=getSnowflakeQueryContext result=NO_DATASET_CONTEXT");
            return "No dataset is currently in focus.";
        }

        List<CatalogServiceClient.Distribution> all = catalogClient.getDistributions(datasetId);
        if (all.isEmpty()) {
            return "No distributions found for this dataset.";
        }

        List<CatalogServiceClient.Distribution> snowflake = all.stream()
            .filter(this::isSnowflake)
            .toList();

        if (snowflake.isEmpty()) {
            String available = all.stream()
                .map(d -> (d.title() != null ? d.title() : d.id()) + (d.format() != null ? " [" + d.format() + "]" : ""))
                .collect(Collectors.joining(", "));
            log.info("step=TOOL_RESULT tool=getSnowflakeQueryContext result=NO_SNOWFLAKE elapsed={}ms", elapsed(start));
            return "No Snowflake distribution found for this dataset. Available distributions: " + available;
        }

        CatalogServiceClient.Distribution dist = snowflake.get(0);
        log.info("step=TOOL_CALL_DETAIL tool=getSnowflakeQueryContext distribution={}", dist.id());

        List<CatalogServiceClient.PhysicalColumn> columns = catalogClient.getDistributionPhysicalSchema(dist.id());
        Map<String, CatalogServiceClient.LogicalElement> elementById = buildElementMap(datasetId);

        String fqn     = buildSnowflakeFqn(dist);
        String account = extractSnowflakeAccount(dist.accessUrl());

        var sb = new StringBuilder();
        sb.append("Platform: Snowflake\n");
        if (account != null) sb.append("Account: ").append(account).append('\n');
        sb.append("Fully-Qualified Object: ").append(fqn).append('\n');
        if (dist.accessUrl() != null) sb.append("Access URL: ").append(dist.accessUrl()).append('\n');

        if (columns.isEmpty()) {
            sb.append("\nNo physical column schema defined for the Snowflake distribution.");
        } else {
            sb.append("\nColumns (").append(columns.size()).append("):\n");
            columns.stream()
                .sorted(Comparator.comparingInt(c -> c.ordinal() != null ? c.ordinal() : 0))
                .forEach(col -> {
                    sb.append("  ").append(col.name());
                    if (col.datatype() != null) sb.append("  ").append(col.datatype());
                    if (Boolean.TRUE.equals(col.required())) sb.append("  NOT NULL");
                    CatalogServiceClient.LogicalElement el =
                        col.logicalDataElementId() != null ? elementById.get(col.logicalDataElementId()) : null;
                    if (el != null) {
                        sb.append("  -- ").append(el.name());
                        if (el.logicalType() != null) sb.append(" [").append(el.logicalType()).append(']');
                        if (el.vocabMappings() != null && !el.vocabMappings().isEmpty()) {
                            var vm = el.vocabMappings().get(0);
                            if (vm.conceptLabel() != null)
                                sb.append(", ").append(vm.conceptLabel());
                        }
                    }
                    sb.append('\n');
                });
        }

        if (snowflake.size() > 1) {
            sb.append("\nOther Snowflake distributions:\n");
            snowflake.stream().skip(1).forEach(d -> sb.append("  - ")
                .append(d.title() != null ? d.title() : d.id()).append('\n'));
        }

        String result = sb.toString().strip();
        log.info("step=TOOL_RESULT tool=getSnowflakeQueryContext fqn={} columns.count={} output.length={} elapsed={}ms",
            fqn, columns.size(), result.length(), elapsed(start));
        return result;
    }

    // ── platform & table helpers ──────────────────────────────────────────────

    private CatalogServiceClient.Distribution selectBestDistribution(
            List<CatalogServiceClient.Distribution> distributions) {
        return distributions.stream()
            .min(Comparator.comparingInt(d -> platformPriority(d.format(), d.accessUrl())))
            .orElse(distributions.get(0));
    }

    private int platformPriority(String format, String url) {
        String f = format != null ? format.toLowerCase() : "";
        String u = url    != null ? url.toLowerCase()    : "";
        if (f.equals("snowflake") || u.contains("snowflakecomputing.com")) return 0;
        if (f.equals("delta")     || u.contains("databricks"))             return 1;
        if (u.startsWith("jdbc:teradata://") || u.contains("teradata"))    return 2;
        if (f.equals("parquet")   || u.startsWith("s3://"))                return 3;
        if (f.equals("avro")      || f.equals("orc"))                      return 4;
        if (f.equals("csv"))                                                return 5;
        if (f.equals("kafka"))                                              return 9;
        return 6;
    }

    private String detectPlatform(CatalogServiceClient.Distribution d) {
        String f = d.format()    != null ? d.format().toLowerCase()    : "";
        String u = d.accessUrl() != null ? d.accessUrl().toLowerCase() : "";
        if (f.equals("snowflake") || u.contains("snowflakecomputing.com"))   return "Snowflake";
        if (u.startsWith("jdbc:teradata://") || u.contains("teradata"))      return "Teradata";
        if (u.contains("bigquery.googleapis.com") || u.startsWith("bigquery://")) return "Google BigQuery";
        if (u.contains("databricks") || f.equals("delta"))                   return "Databricks / Delta Lake";
        if (u.startsWith("s3://") && (u.contains("athena") || u.contains("glue")))  return "AWS Athena";
        if (u.startsWith("s3://"))                                            return "AWS S3 (Athena / Trino)";
        if (f.equals("parquet"))                                              return "Parquet (query via Trino, Athena, or Spark)";
        if (f.equals("csv"))                                                  return "CSV (Standard SQL)";
        if (f.equals("kafka"))                                                return "Kafka (not directly queryable via SQL)";
        return "Standard SQL";
    }

    private boolean isSnowflake(CatalogServiceClient.Distribution d) {
        String f = d.format()    != null ? d.format().toLowerCase()    : "";
        String u = d.accessUrl() != null ? d.accessUrl().toLowerCase() : "";
        return f.equals("snowflake") || u.contains("snowflakecomputing.com");
    }

    private String buildSnowflakeFqn(CatalogServiceClient.Distribution d) {
        // Prefer the explicit structured fields set by the seed / UI
        if (d.databaseName() != null || d.schemaName() != null || d.tableName() != null) {
            StringBuilder fqn = new StringBuilder();
            if (d.databaseName() != null) fqn.append(d.databaseName());
            if (d.schemaName()   != null) { if (!fqn.isEmpty()) fqn.append('.'); fqn.append(d.schemaName()); }
            if (d.tableName()    != null) { if (!fqn.isEmpty()) fqn.append('.'); fqn.append(d.tableName()); }
            return fqn.toString();
        }
        // Fall back to parsing the URL path: snowflake://account.snowflakecomputing.com/DB/SCHEMA/TABLE
        String url = d.accessUrl();
        if (url != null) {
            String path = url.replaceAll("(?i)^snowflake://[^/]+", "")   // strip scheme+host
                            .replaceAll("(?i)^jdbc:snowflake://[^/]+", "")
                            .replaceFirst("^/+", "");
            if (!path.isBlank()) {
                String[] parts = path.split("/");
                if (parts.length >= 3) return parts[0] + "." + parts[1] + "." + parts[2];
                if (parts.length == 2) return parts[0] + "." + parts[1];
                if (parts.length == 1 && !parts[0].isBlank()) return parts[0];
            }
        }
        return d.title() != null ? d.title().replaceAll("[^A-Z0-9_.]", "_") : "<TABLE>";
    }

    private String extractSnowflakeAccount(String url) {
        if (url == null) return null;
        // snowflake://myaccount.snowflakecomputing.com/... → myaccount
        // jdbc:snowflake://myaccount.snowflakecomputing.com/... → myaccount
        java.util.regex.Matcher m = java.util.regex.Pattern
            .compile("(?i)(?:snowflake://|jdbc:snowflake://)([^./]+)\\.snowflakecomputing\\.com")
            .matcher(url);
        return m.find() ? m.group(1) : null;
    }

    private String extractTableName(CatalogServiceClient.Distribution d) {
        String url = d.accessUrl() != null ? d.accessUrl() : d.downloadUrl();
        if (url != null) {
            // s3://bucket/path/to/table → last non-empty segment
            if (url.startsWith("s3://")) {
                String[] parts = url.replaceFirst("^s3://", "").split("/");
                for (int i = parts.length - 1; i >= 0; i--) {
                    if (!parts[i].isBlank()) return parts[i];
                }
            }
            // JDBC-style: jdbc:snowflake://account.snowflakecomputing.com/?db=DB&schema=SCH&warehouse=WH
            if (url.contains("db=") && url.contains("schema=")) {
                String db     = extractParam(url, "db");
                String schema = extractParam(url, "schema");
                if (db != null && schema != null)
                    return db + "." + schema + ".<table>";
            }
        }
        // Fall back to distribution title, sanitised
        String title = d.title() != null ? d.title() : "dataset_table";
        return title.replaceAll("[^a-zA-Z0-9_.\\-]", "_");
    }

    private static String extractParam(String url, String key) {
        int idx = url.indexOf(key + "=");
        if (idx < 0) return null;
        int start = idx + key.length() + 1;
        int end   = url.indexOf('&', start);
        return end < 0 ? url.substring(start) : url.substring(start, end);
    }

    // ── logical element lookup ────────────────────────────────────────────────

    private Map<String, CatalogServiceClient.LogicalElement> buildElementMap(String datasetId) {
        List<CatalogServiceClient.LogicalModel> models = catalogClient.getLogicalModels(datasetId);
        if (models == null || models.isEmpty()) return Map.of();

        CatalogServiceClient.LogicalModel model = models.stream()
            .min(Comparator.comparingInt(m -> modelStatusPriority(m.status())))
            .orElse(models.get(0));

        List<CatalogServiceClient.LogicalElement> elements = catalogClient.getLogicalElements(model.id());
        if (elements == null || elements.isEmpty()) return Map.of();

        return elements.stream().collect(Collectors.toMap(
            CatalogServiceClient.LogicalElement::id,
            e -> e,
            (a, b) -> a
        ));
    }

    private static int modelStatusPriority(String status) {
        return switch (status == null ? "" : status) {
            case "published"  -> 0;
            case "draft"      -> 1;
            case "deprecated" -> 2;
            default           -> 3;
        };
    }

    private static long elapsed(long since) {
        return System.currentTimeMillis() - since;
    }
}
