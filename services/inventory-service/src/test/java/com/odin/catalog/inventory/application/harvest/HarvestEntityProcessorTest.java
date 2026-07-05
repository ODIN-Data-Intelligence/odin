package com.odin.catalog.inventory.application.harvest;

import com.odin.catalog.inventory.infrastructure.jpa.entity.*;
import com.odin.catalog.inventory.infrastructure.jpa.repository.*;
import com.odin.catalog.inventory.infrastructure.kafka.CatalogEventProducer;
import com.odin.catalog.shared.models.common.NormalizedColumn;
import com.odin.catalog.shared.models.events.HarvestEntityDiscoveredPayload;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class HarvestEntityProcessorTest {

    @Mock DatasetRepository datasetRepository;
    @Mock DistributionRepository distributionRepository;
    @Mock LogicalModelRepository logicalModelRepository;
    @Mock LogicalDataElementRepository elementRepository;
    @Mock CsvwColumnRepository columnRepository;
    @Mock CatalogEventProducer eventProducer;

    @InjectMocks HarvestEntityProcessor processor;

    // ── entity type filtering ─────────────────────────────────────────────

    @Test
    void process_nonDatasetEntity_isIgnored() {
        processor.process(payload("TABLE", "src://schema/view", List.of()));
        verifyNoInteractions(datasetRepository, eventProducer);
    }

    // ── dataset upsert ────────────────────────────────────────────────────

    @Test
    void process_newDataset_createsAndPublishes() {
        HarvestEntityDiscoveredPayload p = payload("DATASET", "src://trading/trades", null);
        DatasetEntity saved = dataset();
        when(datasetRepository.findBySourceUri(p.sourceUri())).thenReturn(List.of());
        when(datasetRepository.save(any())).thenReturn(saved);

        processor.process(p);

        ArgumentCaptor<DatasetEntity> captor = ArgumentCaptor.forClass(DatasetEntity.class);
        verify(datasetRepository).save(captor.capture());
        assertThat(captor.getValue().getSourceUri()).isEqualTo("src://trading/trades");
        assertThat(captor.getValue().getTitle()).isEqualTo("Trades");
        verify(eventProducer).publishDatasetChanged("UPDATED", saved);
    }

    @Test
    void process_newDataset_nullTitle_fallsBackToSourceKey() {
        HarvestEntityDiscoveredPayload p = payloadWithTitle("DATASET", "src://ns/tbl", "trading/tbl", null, null);
        DatasetEntity saved = dataset();
        when(datasetRepository.findBySourceUri(any())).thenReturn(List.of());
        when(datasetRepository.save(any())).thenReturn(saved);

        processor.process(p);

        ArgumentCaptor<DatasetEntity> captor = ArgumentCaptor.forClass(DatasetEntity.class);
        verify(datasetRepository).save(captor.capture());
        // falls back to sourceKey when title is null
        assertThat(captor.getValue().getTitle()).isEqualTo("trading/tbl");
    }

    @Test
    void process_existingDataset_updatesAndPublishes() {
        HarvestEntityDiscoveredPayload p = payload("DATASET", "src://trading/trades", null);
        DatasetEntity existing = dataset();
        existing.setTitle("Old Title");
        when(datasetRepository.findBySourceUri(p.sourceUri())).thenReturn(List.of(existing));
        when(datasetRepository.save(existing)).thenReturn(existing);

        processor.process(p);

        assertThat(existing.getTitle()).isEqualTo("Trades");
        verify(eventProducer).publishDatasetChanged("UPDATED", existing);
    }

    // ── auto-scaffold ─────────────────────────────────────────────────────

    @Test
    void process_withColumns_noExistingModel_autoScaffoldsLogicalModel() {
        List<NormalizedColumn> cols = List.of(
            new NormalizedColumn("trade_id", "VARCHAR", true, false, "ID", null, 0),
            new NormalizedColumn("amount",   "DECIMAL(18,4)", false, false, "Amount", null, 1)
        );
        HarvestEntityDiscoveredPayload p = payload("DATASET", "src://trading/trades", cols);

        DatasetEntity saved = dataset();
        when(datasetRepository.findBySourceUri(any())).thenReturn(List.of());
        when(datasetRepository.save(any())).thenReturn(saved);
        when(columnRepository.findBySchemaIdAndNameIgnoreCase(any(), eq("trade_id"))).thenReturn(Optional.empty());
        when(columnRepository.findBySchemaIdAndNameIgnoreCase(any(), eq("amount"))).thenReturn(Optional.empty());

        CsvwColumnEntity col1 = col("trade_id", "VARCHAR");
        CsvwColumnEntity col2 = col("amount", "DECIMAL(18,4)");
        when(columnRepository.save(any())).thenReturn(col1).thenReturn(col2);

        when(logicalModelRepository.findByDatasetIdOrderByCreatedAtDesc(saved.getId()))
            .thenReturn(List.of());

        LogicalModelEntity scaffolded = logicalModel(saved.getId());
        when(logicalModelRepository.save(any())).thenReturn(scaffolded);

        LogicalDataElementEntity el = element(scaffolded.getId());
        when(elementRepository.save(any())).thenReturn(el);

        processor.process(p);

        verify(logicalModelRepository).save(argThat(m ->
            "Auto-generated from harvest".equals(m.getName()) && "draft".equals(m.getStatus())
        ));
        verify(elementRepository, times(2)).save(any());
    }

    @Test
    void process_withColumns_existingModel_autobindsMatchingElements() {
        List<NormalizedColumn> cols = List.of(
            new NormalizedColumn("amount", "DECIMAL(18,4)", false, false, null, null, 0)
        );
        HarvestEntityDiscoveredPayload p = payload("DATASET", "src://trading/trades", cols);

        DatasetEntity saved = dataset();
        when(datasetRepository.findBySourceUri(any())).thenReturn(List.of());
        when(datasetRepository.save(any())).thenReturn(saved);

        CsvwColumnEntity colEntity = col("amount", "DECIMAL");
        colEntity.setLogicalDataElementId(null); // unbound
        when(columnRepository.findBySchemaIdAndNameIgnoreCase(any(), eq("amount"))).thenReturn(Optional.empty());
        when(columnRepository.save(any())).thenReturn(colEntity);

        LogicalModelEntity existingModel = logicalModel(saved.getId());
        LogicalDataElementEntity existingElement = element(existingModel.getId());
        existingElement.setName("amount");

        when(logicalModelRepository.findByDatasetIdOrderByCreatedAtDesc(saved.getId()))
            .thenReturn(List.of(existingModel));
        when(elementRepository.findByLogicalModelIdOrderByOrdinalAsc(existingModel.getId()))
            .thenReturn(List.of(existingElement));

        processor.process(p);

        assertThat(colEntity.getLogicalDataElementId()).isEqualTo(existingElement.getId());
        verify(columnRepository, atLeastOnce()).save(colEntity);
    }

    // ── inferLogicalType ─────────────────────────────────────────────────

    @ParameterizedTest
    @CsvSource({
        "DATE,Date",
        "TIMESTAMP,Date",
        "TIME,Date",
        "DECIMAL,Measure",
        "NUMERIC,Measure",
        "FLOAT,Measure",
        "DOUBLE,Measure",
        "INT,Count",
        "BIGINT,Count",
        "BOOLEAN,Flag",
        "VARCHAR,Text",
        "TEXT,Text"
    })
    void inferLogicalType_mapsCorrectly(String sqlType, String expectedLogicalType) {
        List<NormalizedColumn> cols = List.of(
            new NormalizedColumn("col", sqlType, false, false, null, null, 0)
        );
        HarvestEntityDiscoveredPayload p = payload("DATASET", "src://ns/tbl", cols);

        DatasetEntity saved = dataset();
        when(datasetRepository.findBySourceUri(any())).thenReturn(List.of());
        when(datasetRepository.save(any())).thenReturn(saved);
        when(columnRepository.findBySchemaIdAndNameIgnoreCase(any(), eq("col"))).thenReturn(Optional.empty());
        when(columnRepository.save(any())).thenReturn(col("col", sqlType));
        when(logicalModelRepository.findByDatasetIdOrderByCreatedAtDesc(saved.getId())).thenReturn(List.of());

        LogicalModelEntity scaffolded = logicalModel(saved.getId());
        when(logicalModelRepository.save(any())).thenReturn(scaffolded);

        ArgumentCaptor<LogicalDataElementEntity> captor = ArgumentCaptor.forClass(LogicalDataElementEntity.class);
        when(elementRepository.save(captor.capture())).thenAnswer(inv -> {
            LogicalDataElementEntity e = inv.getArgument(0);
            e.setId(UUID.randomUUID());
            return e;
        });

        processor.process(p);

        assertThat(captor.getValue().getLogicalType()).isEqualTo(expectedLogicalType);
    }

    @Test
    void inferLogicalType_nullSqlType_returnsNull() {
        List<NormalizedColumn> cols = List.of(
            new NormalizedColumn("col", null, false, false, null, null, 0)
        );
        HarvestEntityDiscoveredPayload p = payload("DATASET", "src://ns/tbl", cols);

        DatasetEntity saved = dataset();
        when(datasetRepository.findBySourceUri(any())).thenReturn(List.of());
        when(datasetRepository.save(any())).thenReturn(saved);
        when(columnRepository.findBySchemaIdAndNameIgnoreCase(any(), eq("col"))).thenReturn(Optional.empty());
        when(columnRepository.save(any())).thenReturn(col("col", null));
        when(logicalModelRepository.findByDatasetIdOrderByCreatedAtDesc(saved.getId())).thenReturn(List.of());

        LogicalModelEntity scaffolded = logicalModel(saved.getId());
        when(logicalModelRepository.save(any())).thenReturn(scaffolded);

        ArgumentCaptor<LogicalDataElementEntity> captor = ArgumentCaptor.forClass(LogicalDataElementEntity.class);
        when(elementRepository.save(captor.capture())).thenAnswer(inv -> {
            LogicalDataElementEntity e = inv.getArgument(0);
            e.setId(UUID.randomUUID());
            return e;
        });

        processor.process(p);

        assertThat(captor.getValue().getLogicalType()).isNull();
    }

    // ── existing column upsert ────────────────────────────────────────────

    @Test
    void process_existingColumn_updatesDatatype() {
        List<NormalizedColumn> cols = List.of(
            new NormalizedColumn("amount", "DECIMAL(18,4)", false, false, null, null, 0)
        );
        HarvestEntityDiscoveredPayload p = payload("DATASET", "src://trading/trades", cols);

        DatasetEntity saved = dataset();
        when(datasetRepository.findBySourceUri(any())).thenReturn(List.of());
        when(datasetRepository.save(any())).thenReturn(saved);

        CsvwColumnEntity existing = col("amount", "DECIMAL(10,2)");
        when(columnRepository.findBySchemaIdAndNameIgnoreCase(any(), eq("amount")))
            .thenReturn(Optional.of(existing));
        when(columnRepository.save(existing)).thenReturn(existing);
        when(logicalModelRepository.findByDatasetIdOrderByCreatedAtDesc(saved.getId())).thenReturn(List.of());

        LogicalModelEntity scaffolded = logicalModel(saved.getId());
        when(logicalModelRepository.save(any())).thenReturn(scaffolded);
        when(elementRepository.save(any())).thenAnswer(inv -> {
            LogicalDataElementEntity e = inv.getArgument(0);
            e.setId(UUID.randomUUID());
            return e;
        });

        processor.process(p);

        assertThat(existing.getDatatype()).isEqualTo("DECIMAL(18,4)");
    }

    // ── fixtures ──────────────────────────────────────────────────────────

    private HarvestEntityDiscoveredPayload payload(String entityType, String sourceUri,
                                                    List<NormalizedColumn> cols) {
        return new HarvestEntityDiscoveredPayload(
            "run-1", "src-1", "snowflake", entityType,
            "trading/trades", sourceUri,
            "Trades", "description", null, null,
            List.of("tag"), List.of("Finance"),
            List.of(), cols, null
        );
    }

    private HarvestEntityDiscoveredPayload payloadWithTitle(String entityType, String sourceUri,
                                                             String sourceKey, String title,
                                                             List<NormalizedColumn> cols) {
        return new HarvestEntityDiscoveredPayload(
            "run-1", "src-1", "snowflake", entityType,
            sourceKey, sourceUri,
            title, null, null, null,
            List.of(), List.of(),
            List.of(), cols, null
        );
    }

    private DatasetEntity dataset() {
        DatasetEntity ds = new DatasetEntity();
        ds.setId(UUID.randomUUID());
        ds.setTenantId(UUID.fromString("00000000-0000-0000-0000-000000000001"));
        ds.setTitle("Trades");
        ds.setSourceUri("src://trading/trades");
        ds.setCreatedAt(OffsetDateTime.now());
        ds.setUpdatedAt(OffsetDateTime.now());
        return ds;
    }

    private CsvwColumnEntity col(String name, String datatype) {
        CsvwColumnEntity c = new CsvwColumnEntity();
        c.setId(UUID.randomUUID());
        c.setSchemaId(UUID.randomUUID());
        c.setOrdinal(0);
        c.setName(name);
        c.setDatatype(datatype);
        return c;
    }

    private LogicalModelEntity logicalModel(UUID datasetId) {
        LogicalModelEntity m = new LogicalModelEntity();
        m.setId(UUID.randomUUID());
        m.setDatasetId(datasetId);
        m.setName("Auto-generated from harvest");
        m.setStatus("draft");
        m.setCreatedAt(OffsetDateTime.now());
        m.setUpdatedAt(OffsetDateTime.now());
        return m;
    }

    private LogicalDataElementEntity element(UUID modelId) {
        LogicalDataElementEntity e = new LogicalDataElementEntity();
        e.setId(UUID.randomUUID());
        e.setLogicalModelId(modelId);
        e.setName("amount");
        e.setOrdinal(0);
        e.setCreatedAt(OffsetDateTime.now());
        e.setUpdatedAt(OffsetDateTime.now());
        return e;
    }
}
