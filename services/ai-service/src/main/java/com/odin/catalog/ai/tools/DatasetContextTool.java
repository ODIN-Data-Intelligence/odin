package com.odin.catalog.ai.tools;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.odin.catalog.ai.client.CatalogServiceClient;
import dev.langchain4j.agent.tool.P;
import dev.langchain4j.agent.tool.Tool;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.util.Comparator;
import java.util.List;

@Component
public class DatasetContextTool {

    private static final Logger log = LoggerFactory.getLogger(DatasetContextTool.class);

    private final CatalogServiceClient catalogClient;
    private final RestClient searchClient;

    public DatasetContextTool(
            CatalogServiceClient catalogClient,
            RestClient.Builder restClientBuilder,
            @Value("${search.service.url:http://search-service:8004}") String searchServiceUrl,
            @Value("${catalog.service.api-key:dev-internal}") String apiKey) {
        this.catalogClient = catalogClient;
        this.searchClient = restClientBuilder.clone()
            .baseUrl(searchServiceUrl)
            .defaultHeader("X-API-Key", apiKey)
            .build();
    }

    @Tool("Retrieve the title, description, keywords, and themes for a dataset by its ID")
    public String getDatasetInfo(@P("UUID of the dataset") String datasetId) {
        long start = System.currentTimeMillis();
        log.info("step=TOOL_CALL tool=getDatasetInfo input.datasetId={}", datasetId);

        var ds = catalogClient.getDataset(datasetId);
        if (ds == null) {
            log.info("step=TOOL_RESULT tool=getDatasetInfo result=NOT_FOUND elapsed={}ms", elapsed(start));
            return "Dataset not found: " + datasetId;
        }

        var sb = new StringBuilder();
        sb.append("Title: ").append(ds.title()).append('\n');
        if (ds.description() != null) sb.append("Description: ").append(ds.description()).append('\n');
        if (ds.keywords() != null && !ds.keywords().isEmpty())
            sb.append("Keywords: ").append(String.join(", ", ds.keywords())).append('\n');
        if (ds.themes() != null && !ds.themes().isEmpty())
            sb.append("Themes: ").append(String.join(", ", ds.themes())).append('\n');
        String result = sb.toString().strip();

        log.info("step=TOOL_RESULT tool=getDatasetInfo output.title={} output.length={} elapsed={}ms",
            ds.title(), result.length(), elapsed(start));
        log.debug("step=TOOL_RESULT tool=getDatasetInfo output={}", result);
        return result;
    }

    @Tool("Retrieve logical data elements with their vocabulary concept mappings for a dataset. " +
          "Returns each element's business name, logical type, description, and associated vocabulary concepts (e.g. FIBO, schema.org).")
    public String getLogicalElementsWithVocabulary(@P("UUID of the dataset") String datasetId) {
        long start = System.currentTimeMillis();
        log.info("step=TOOL_CALL tool=getLogicalElementsWithVocabulary input.datasetId={}", datasetId);

        List<CatalogServiceClient.LogicalModel> models = catalogClient.getLogicalModels(datasetId);
        if (models == null || models.isEmpty()) {
            log.info("step=TOOL_RESULT tool=getLogicalElementsWithVocabulary result=NO_MODEL elapsed={}ms", elapsed(start));
            return "No logical model defined for this dataset.";
        }

        // Prefer published model, then draft, then any
        CatalogServiceClient.LogicalModel model = models.stream()
            .sorted(Comparator.comparingInt(m -> statusPriority(m.status())))
            .findFirst().orElse(models.get(0));

        log.info("step=TOOL_CALL_DETAIL tool=getLogicalElementsWithVocabulary selectedModel={} status={}",
            model.name(), model.status());

        List<CatalogServiceClient.LogicalElement> elements = catalogClient.getLogicalElements(model.id());
        if (elements == null || elements.isEmpty()) {
            log.info("step=TOOL_RESULT tool=getLogicalElementsWithVocabulary result=NO_ELEMENTS model={} elapsed={}ms",
                model.name(), elapsed(start));
            return "Logical model \"" + model.name() + "\" has no elements defined yet.";
        }

        var sb = new StringBuilder();
        sb.append("Logical Model: ").append(model.name())
          .append(" (").append(model.status()).append(", v").append(model.version()).append(")\n\n");

        for (var el : elements) {
            sb.append("- ").append(el.name());
            if (el.label() != null && !el.label().equals(el.name()))
                sb.append(" (").append(el.label()).append(')');
            if (el.logicalType() != null) sb.append(" [").append(el.logicalType()).append(']');
            sb.append('\n');
            if (el.description() != null) sb.append("  Description: ").append(el.description()).append('\n');
            if (el.vocabMappings() != null && !el.vocabMappings().isEmpty()) {
                sb.append("  Vocabulary concepts:\n");
                for (var vm : el.vocabMappings()) {
                    sb.append("    • ");
                    if (vm.conceptLabel() != null) sb.append(vm.conceptLabel()).append(' ');
                    sb.append('(').append(vm.matchType()).append("): ").append(vm.conceptIri()).append('\n');
                    if (vm.conceptDefinition() != null)
                        sb.append("      Definition: ").append(vm.conceptDefinition()).append('\n');
                }
            }
        }
        String result = sb.toString().strip();

        log.info("step=TOOL_RESULT tool=getLogicalElementsWithVocabulary model={} elements.count={} output.length={} elapsed={}ms",
            model.name(), elements.size(), result.length(), elapsed(start));
        log.debug("step=TOOL_RESULT tool=getLogicalElementsWithVocabulary output={}", result);
        return result;
    }

    @Tool("Search the data catalog for datasets related to the given query or concept. " +
          "Returns dataset IDs and titles. Call getQueryContext() for each result you need physical schema for " +
          "before writing any SQL query that references those datasets.")
    public String findRelatedDatasets(@P("Natural language search query describing the datasets to find") String query) {
        long start = System.currentTimeMillis();
        log.info("step=TOOL_CALL tool=findRelatedDatasets query={}", query);
        try {
            List<SearchHit> hits = searchClient.get()
                .uri(uriBuilder -> uriBuilder
                    .path("/api/v1/search")
                    .queryParam("q", query)
                    .queryParam("type", "dataset")
                    .queryParam("size", 5)
                    .build())
                .retrieve()
                .body(new ParameterizedTypeReference<List<SearchHit>>() {});

            if (hits == null || hits.isEmpty()) {
                log.info("step=TOOL_RESULT tool=findRelatedDatasets result=NONE elapsed={}ms", elapsed(start));
                return "No datasets found matching: " + query;
            }

            StringBuilder sb = new StringBuilder("Found ").append(hits.size()).append(" dataset(s):\n");
            for (SearchHit h : hits) {
                sb.append("  - id=").append(h.id()).append("  title=").append(h.title());
                if (h.description() != null && !h.description().isBlank())
                    sb.append("  (").append(h.description(), 0, Math.min(80, h.description().length())).append(")");
                sb.append('\n');
            }
            String result = sb.toString().strip();
            log.info("step=TOOL_RESULT tool=findRelatedDatasets hits={} elapsed={}ms", hits.size(), elapsed(start));
            return result;
        } catch (Exception e) {
            log.warn("step=TOOL_RESULT tool=findRelatedDatasets error={} elapsed={}ms", e.getMessage(), elapsed(start));
            return "Could not search catalog: " + e.getMessage();
        }
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    private record SearchHit(String id, String title, String description) {}

    private static int statusPriority(String status) {
        return switch (status == null ? "" : status) {
            case "published"   -> 0;
            case "draft"       -> 1;
            case "deprecated"  -> 2;
            default            -> 3;
        };
    }

    private static long elapsed(long since) {
        return System.currentTimeMillis() - since;
    }
}
