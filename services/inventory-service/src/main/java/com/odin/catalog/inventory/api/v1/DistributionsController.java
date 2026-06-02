package com.odin.catalog.inventory.api.v1;

import com.odin.catalog.inventory.api.v1.dto.ColumnElementSuggestion;
import com.odin.catalog.inventory.application.logical.LogicalModelService;
import com.odin.catalog.inventory.infrastructure.jpa.entity.CsvwColumnEntity;
import com.odin.catalog.inventory.infrastructure.jpa.entity.DistributionEntity;
import com.odin.catalog.inventory.infrastructure.jpa.repository.CsvwColumnRepository;
import com.odin.catalog.inventory.infrastructure.jpa.repository.DistributionRepository;
import com.odin.catalog.shared.auth.filter.TenantContextHolder;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Tag(name = "Distributions", description = "DCAT Distributions — physical access points and CSV-W column schemas for datasets")
@RestController
@RequiredArgsConstructor
public class DistributionsController {

    private final DistributionRepository distributionRepository;
    private final CsvwColumnRepository csvwColumnRepository;
    private final JdbcTemplate jdbcTemplate;
    private final LogicalModelService logicalModelService;

    @Operation(summary = "List all distributions",
        description = "Returns all non-deleted distributions for the current tenant, paginated and sorted by creation date descending.")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Paginated list of distributions"),
        @ApiResponse(responseCode = "401", description = "Missing or invalid auth", content = @Content)
    })
    @GetMapping("/api/v1/distributions")
    public Page<DistributionEntity> listAll(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        UUID tenantId = UUID.fromString(TenantContextHolder.get());
        return distributionRepository.findByTenantIdAndIsDeletedFalse(
            tenantId, PageRequest.of(page, size, Sort.by("createdAt").descending()));
    }

    @Operation(summary = "List distributions for a dataset",
        description = "Returns all non-deleted distributions belonging to the given dataset.")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "List of distributions"),
        @ApiResponse(responseCode = "401", description = "Missing or invalid auth", content = @Content)
    })
    @GetMapping("/api/v1/datasets/{datasetId}/distributions")
    public List<DistributionEntity> listByDataset(
            @Parameter(description = "Dataset UUID", example = "3fa85f64-5717-4562-b3fc-2c963f66afa6")
            @PathVariable UUID datasetId) {
        return distributionRepository.findByDatasetIdAndIsDeletedFalse(datasetId);
    }

    @Operation(summary = "Create a distribution for a dataset",
        description = "Adds a new DCAT Distribution (e.g. Parquet file, Snowflake endpoint, REST API) to a dataset.")
    @ApiResponses({
        @ApiResponse(responseCode = "201", description = "Distribution created"),
        @ApiResponse(responseCode = "401", description = "Missing or invalid auth", content = @Content)
    })
    @io.swagger.v3.oas.annotations.parameters.RequestBody(
        description = "Distribution fields",
        content = @Content(schema = @Schema(example = """
            {"title": "Parquet snapshot", "mediaType": "application/parquet", "format": "Parquet",
             "downloadUrl": "s3://bucket/positions/2024-01-01.parquet", "byteSize": 1048576}""")))
    @PostMapping("/api/v1/datasets/{datasetId}/distributions")
    @ResponseStatus(HttpStatus.CREATED)
    public DistributionEntity create(
            @Parameter(description = "Dataset UUID", example = "3fa85f64-5717-4562-b3fc-2c963f66afa6")
            @PathVariable UUID datasetId,
            @RequestBody Map<String, Object> body) {
        UUID tenantId = UUID.fromString(TenantContextHolder.get());
        DistributionEntity dist = new DistributionEntity();
        dist.setTenantId(tenantId);
        dist.setDatasetId(datasetId);
        dist.setTitle((String) body.getOrDefault("title", "Distribution"));
        dist.setDescription((String) body.get("description"));
        dist.setAccessUrl((String) body.get("accessUrl"));
        dist.setDownloadUrl((String) body.get("downloadUrl"));
        dist.setMediaType((String) body.get("mediaType"));
        dist.setFormat((String) body.get("format"));
        if (body.get("byteSize") instanceof Number n) dist.setByteSize(n.longValue());
        dist.setCompressFormat((String) body.get("compressFormat"));
        dist.setAvailability((String) body.get("availability"));
        dist.setDatabaseName((String) body.get("databaseName"));
        dist.setSchemaName((String) body.get("schemaName"));
        dist.setTableName((String) body.get("tableName"));
        return distributionRepository.save(dist);
    }

    @Operation(summary = "Get distribution", description = "Returns a single distribution by UUID.")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Distribution found"),
        @ApiResponse(responseCode = "404", description = "Distribution not found", content = @Content),
        @ApiResponse(responseCode = "401", description = "Missing or invalid auth", content = @Content)
    })
    @GetMapping("/api/v1/distributions/{id}")
    public DistributionEntity get(
            @Parameter(description = "Distribution UUID", example = "3fa85f64-5717-4562-b3fc-2c963f66afa6")
            @PathVariable UUID id) {
        return distributionRepository.findById(id)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
    }

    @Operation(summary = "Delete distribution", description = "Soft-deletes a distribution.")
    @ApiResponses({
        @ApiResponse(responseCode = "204", description = "Deleted"),
        @ApiResponse(responseCode = "404", description = "Distribution not found", content = @Content),
        @ApiResponse(responseCode = "401", description = "Missing or invalid auth", content = @Content)
    })
    @DeleteMapping("/api/v1/distributions/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(
            @Parameter(description = "Distribution UUID", example = "3fa85f64-5717-4562-b3fc-2c963f66afa6")
            @PathVariable UUID id) {
        DistributionEntity dist = distributionRepository.findById(id)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
        dist.setDeleted(true);
        distributionRepository.save(dist);
    }

    @Operation(summary = "Get physical schema for a dataset",
        description = "Returns the CSV-W column schema harvested for this dataset. Columns are ordered by their ordinal position.")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "List of physical columns"),
        @ApiResponse(responseCode = "401", description = "Missing or invalid auth", content = @Content)
    })
    @GetMapping("/api/v1/datasets/{datasetId}/physical-schema")
    public List<CsvwColumnEntity> getPhysicalSchema(
            @Parameter(description = "Dataset UUID", example = "3fa85f64-5717-4562-b3fc-2c963f66afa6")
            @PathVariable UUID datasetId) {
        UUID schemaId = UUID.nameUUIDFromBytes((datasetId.toString() + ":schema").getBytes());
        return csvwColumnRepository.findBySchemaIdOrderByOrdinalAsc(schemaId);
    }

    @Operation(summary = "Set physical schema for a dataset",
        description = "Replaces the full CSV-W column schema for a dataset. All existing columns are deleted and replaced. Used by the harvest pipeline.")
    @ApiResponses({
        @ApiResponse(responseCode = "201", description = "Schema replaced"),
        @ApiResponse(responseCode = "401", description = "Missing or invalid auth", content = @Content)
    })
    @PostMapping("/api/v1/datasets/{datasetId}/physical-schema")
    @ResponseStatus(HttpStatus.CREATED)
    @Transactional
    public List<CsvwColumnEntity> setPhysicalSchema(
            @Parameter(description = "Dataset UUID", example = "3fa85f64-5717-4562-b3fc-2c963f66afa6")
            @PathVariable UUID datasetId,
            @RequestBody List<Map<String, Object>> columns) {
        return upsertSchema(datasetId.toString(), columns);
    }

    @Operation(summary = "Get physical schema for a distribution",
        description = "Returns the CSV-W column schema for a specific distribution.")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "List of physical columns"),
        @ApiResponse(responseCode = "401", description = "Missing or invalid auth", content = @Content)
    })
    @GetMapping("/api/v1/distributions/{distributionId}/physical-schema")
    public List<CsvwColumnEntity> getDistributionPhysicalSchema(
            @Parameter(description = "Distribution UUID", example = "3fa85f64-5717-4562-b3fc-2c963f66afa6")
            @PathVariable UUID distributionId) {
        UUID schemaId = UUID.nameUUIDFromBytes((distributionId.toString() + ":schema").getBytes());
        return csvwColumnRepository.findBySchemaIdOrderByOrdinalAsc(schemaId);
    }

    @Operation(summary = "Suggest logical element bindings for a distribution",
        description = "Returns confidence-scored suggestions for binding physical columns to logical data elements in the given model. "
            + "Matching is done by name similarity.")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "List of column-to-element suggestions"),
        @ApiResponse(responseCode = "404", description = "Distribution or model not found", content = @Content),
        @ApiResponse(responseCode = "401", description = "Missing or invalid auth", content = @Content)
    })
    @GetMapping("/api/v1/distributions/{distributionId}/suggest-element-mappings")
    public List<ColumnElementSuggestion> suggestElementMappings(
            @Parameter(description = "Distribution UUID", example = "3fa85f64-5717-4562-b3fc-2c963f66afa6")
            @PathVariable UUID distributionId,
            @Parameter(description = "Logical model UUID to match against", example = "3fa85f64-5717-4562-b3fc-2c963f66afa6")
            @RequestParam UUID modelId) {
        return logicalModelService.suggestElementMappings(distributionId, modelId);
    }

    @Operation(summary = "Set physical schema for a distribution",
        description = "Replaces the full CSV-W column schema for a specific distribution.")
    @ApiResponses({
        @ApiResponse(responseCode = "201", description = "Schema replaced"),
        @ApiResponse(responseCode = "401", description = "Missing or invalid auth", content = @Content)
    })
    @PostMapping("/api/v1/distributions/{distributionId}/physical-schema")
    @ResponseStatus(HttpStatus.CREATED)
    @Transactional
    public List<CsvwColumnEntity> setDistributionPhysicalSchema(
            @Parameter(description = "Distribution UUID", example = "3fa85f64-5717-4562-b3fc-2c963f66afa6")
            @PathVariable UUID distributionId,
            @RequestBody List<Map<String, Object>> columns) {
        return upsertSchema(distributionId.toString(), columns);
    }

    private List<CsvwColumnEntity> upsertSchema(String ownerId, List<Map<String, Object>> columns) {
        UUID tableId  = UUID.nameUUIDFromBytes((ownerId + ":table").getBytes());
        UUID schemaId = UUID.nameUUIDFromBytes((ownerId + ":schema").getBytes());

        jdbcTemplate.update("""
            INSERT INTO csvw_tables (id, title)
            VALUES (?::uuid, ?)
            ON CONFLICT (id) DO NOTHING
            """, tableId.toString(), "Physical Schema");

        jdbcTemplate.update("""
            INSERT INTO csvw_table_schemas (id, table_id)
            VALUES (?::uuid, ?::uuid)
            ON CONFLICT (id) DO NOTHING
            """, schemaId.toString(), tableId.toString());

        csvwColumnRepository.deleteBySchemaId(schemaId);

        List<CsvwColumnEntity> saved = new ArrayList<>();
        for (int i = 0; i < columns.size(); i++) {
            Map<String, Object> col = columns.get(i);
            CsvwColumnEntity entity = new CsvwColumnEntity();
            entity.setSchemaId(schemaId);
            entity.setOrdinal(i + 1);
            entity.setName((String) col.get("name"));
            entity.setDatatype((String) col.get("datatype"));
            entity.setDescription((String) col.get("description"));
            entity.setRequired(Boolean.TRUE.equals(col.get("required")));
            if (col.get("titles") instanceof List<?> t)
                entity.setTitles(t.stream().map(Object::toString).toList());
            entity.setPropertyUrl((String) col.get("propertyUrl"));
            saved.add(csvwColumnRepository.save(entity));
        }
        return saved;
    }
}
