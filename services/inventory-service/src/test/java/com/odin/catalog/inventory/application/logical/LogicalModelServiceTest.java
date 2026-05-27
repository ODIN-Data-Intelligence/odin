package com.odin.catalog.inventory.application.logical;

import com.odin.catalog.inventory.api.v1.dto.*;
import com.odin.catalog.inventory.infrastructure.ai.AiServiceClient;
import com.odin.catalog.inventory.infrastructure.jpa.entity.*;
import com.odin.catalog.inventory.infrastructure.jpa.repository.*;
import com.odin.catalog.inventory.application.logical.BulkRecommendationJobRegistry;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.transaction.PlatformTransactionManager;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class LogicalModelServiceTest {

    @Mock LogicalModelRepository modelRepository;
    @Mock LogicalDataElementRepository elementRepository;
    @Mock VocabMappingRepository mappingRepository;
    @Mock CsvwColumnRepository columnRepository;
    @Mock AiServiceClient aiServiceClient;
    @Mock PlatformTransactionManager transactionManager;
    @Mock BulkRecommendationJobRegistry jobRegistry;

    @InjectMocks LogicalModelService service;

    // ── listForDataset ────────────────────────────────────────────────────

    @Test
    void listForDataset_returnsMappedResponses() {
        UUID dsId = UUID.randomUUID();
        LogicalModelEntity m = model(dsId);
        m.setElements(List.of());
        when(modelRepository.findByDatasetIdOrderByCreatedAtDesc(dsId)).thenReturn(List.of(m));

        List<LogicalModelResponse> result = service.listForDataset(dsId);

        assertThat(result).hasSize(1);
        assertThat(result.get(0).datasetId()).isEqualTo(dsId);
    }

    // ── get ───────────────────────────────────────────────────────────────

    @Test
    void get_found_returnsResponse() {
        LogicalModelEntity m = model(UUID.randomUUID());
        m.setElements(List.of());
        when(modelRepository.findById(m.getId())).thenReturn(Optional.of(m));

        LogicalModelResponse result = service.get(m.getId());

        assertThat(result.id()).isEqualTo(m.getId());
        assertThat(result.name()).isEqualTo("Trade Model");
    }

    @Test
    void get_notFound_throws() {
        UUID id = UUID.randomUUID();
        when(modelRepository.findById(id)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.get(id))
            .isInstanceOf(NoSuchElementException.class)
            .hasMessageContaining(id.toString());
    }

    // ── create ────────────────────────────────────────────────────────────

    @Test
    void create_savesWithDatasetIdAndDefaultVersion() {
        UUID dsId = UUID.randomUUID();
        LogicalModelRequest req = new LogicalModelRequest("My Model", "desc", null);
        LogicalModelEntity saved = model(dsId);
        saved.setElements(List.of());
        when(modelRepository.save(any())).thenReturn(saved);

        LogicalModelResponse result = service.create(dsId, req);

        ArgumentCaptor<LogicalModelEntity> captor = ArgumentCaptor.forClass(LogicalModelEntity.class);
        verify(modelRepository).save(captor.capture());
        assertThat(captor.getValue().getDatasetId()).isEqualTo(dsId);
        assertThat(captor.getValue().getName()).isEqualTo("My Model");
        assertThat(result.id()).isEqualTo(saved.getId());
    }

    @Test
    void create_withExplicitVersion_setsVersion() {
        UUID dsId = UUID.randomUUID();
        LogicalModelRequest req = new LogicalModelRequest("Model", null, "2.0");
        LogicalModelEntity saved = model(dsId);
        saved.setElements(List.of());
        when(modelRepository.save(any())).thenReturn(saved);

        service.create(dsId, req);

        ArgumentCaptor<LogicalModelEntity> captor = ArgumentCaptor.forClass(LogicalModelEntity.class);
        verify(modelRepository).save(captor.capture());
        assertThat(captor.getValue().getVersion()).isEqualTo("2.0");
    }

    // ── updateStatus ──────────────────────────────────────────────────────

    @Test
    void updateStatus_changesStatusAndSaves() {
        LogicalModelEntity m = model(UUID.randomUUID());
        m.setElements(List.of());
        when(modelRepository.findById(m.getId())).thenReturn(Optional.of(m));
        when(modelRepository.save(m)).thenReturn(m);

        service.updateStatus(m.getId(), "published");

        assertThat(m.getStatus()).isEqualTo("published");
        verify(modelRepository).save(m);
    }

    // ── delete ────────────────────────────────────────────────────────────

    @Test
    void delete_callsDeleteById() {
        UUID id = UUID.randomUUID();
        service.delete(id);
        verify(modelRepository).deleteById(id);
    }

    // ── addElement ────────────────────────────────────────────────────────

    @Test
    void addElement_savesElementWithModelId() {
        LogicalModelEntity m = model(UUID.randomUUID());
        when(modelRepository.findById(m.getId())).thenReturn(Optional.of(m));

        LogicalDataElementRequest req = new LogicalDataElementRequest(
            "trade_amount", "Trade Amount", "Monetary amount",
            "MonetaryAmount", 0, false, true, true, null
        );
        LogicalDataElementEntity saved = element(m.getId());
        when(elementRepository.save(any())).thenReturn(saved);
        when(columnRepository.findByLogicalDataElementId(saved.getId())).thenReturn(List.of());

        LogicalDataElementResponse result = service.addElement(m.getId(), req);

        ArgumentCaptor<LogicalDataElementEntity> captor = ArgumentCaptor.forClass(LogicalDataElementEntity.class);
        verify(elementRepository).save(captor.capture());
        assertThat(captor.getValue().getLogicalModelId()).isEqualTo(m.getId());
        assertThat(captor.getValue().getName()).isEqualTo("trade_amount");
        assertThat(captor.getValue().isIdentifier()).isTrue();
        assertThat(result.id()).isEqualTo(saved.getId());
    }

    @Test
    void addElement_modelNotFound_throws() {
        UUID modelId = UUID.randomUUID();
        when(modelRepository.findById(modelId)).thenReturn(Optional.empty());

        LogicalDataElementRequest req = new LogicalDataElementRequest(
            "x", null, null, null, 0, false, false, true, null
        );
        assertThatThrownBy(() -> service.addElement(modelId, req))
            .isInstanceOf(NoSuchElementException.class);
    }

    // ── listElements ──────────────────────────────────────────────────────

    @Test
    void listElements_returnsOrderedElements() {
        UUID modelId = UUID.randomUUID();
        LogicalDataElementEntity e1 = element(modelId);
        e1.setOrdinal(0);
        LogicalDataElementEntity e2 = element(modelId);
        e2.setOrdinal(1);
        when(elementRepository.findByLogicalModelIdOrderByOrdinalAsc(modelId)).thenReturn(List.of(e1, e2));
        when(columnRepository.findByLogicalDataElementId(any())).thenReturn(List.of());

        List<LogicalDataElementResponse> result = service.listElements(modelId);

        assertThat(result).hasSize(2);
    }

    // ── updateElement ─────────────────────────────────────────────────────

    @Test
    void updateElement_appliesAndSaves() {
        LogicalDataElementEntity e = element(UUID.randomUUID());
        when(elementRepository.findById(e.getId())).thenReturn(Optional.of(e));
        when(elementRepository.save(e)).thenReturn(e);
        when(columnRepository.findByLogicalDataElementId(e.getId())).thenReturn(List.of());

        LogicalDataElementRequest req = new LogicalDataElementRequest(
            "updated_name", "Updated Label", "new desc",
            "Date", 5, true, false, false, null
        );
        service.updateElement(e.getId(), req);

        assertThat(e.getName()).isEqualTo("updated_name");
        assertThat(e.getLabel()).isEqualTo("Updated Label");
        assertThat(e.getLogicalType()).isEqualTo("Date");
        assertThat(e.getOrdinal()).isEqualTo(5);
        assertThat(e.isRequired()).isTrue();
        assertThat(e.isIdentifier()).isFalse();
        assertThat(e.isNullable()).isFalse();
        verify(elementRepository).save(e);
    }

    @Test
    void updateElement_notFound_throws() {
        UUID id = UUID.randomUUID();
        when(elementRepository.findById(id)).thenReturn(Optional.empty());

        LogicalDataElementRequest req = new LogicalDataElementRequest("x", null, null, null, 0, false, false, true, null);
        assertThatThrownBy(() -> service.updateElement(id, req)).isInstanceOf(NoSuchElementException.class);
    }

    // ── bindPhysicalColumn ────────────────────────────────────────────────

    @Test
    void bindPhysicalColumn_columnExists_callsBindAndReturnsElement() {
        LogicalDataElementEntity e = element(UUID.randomUUID());
        CsvwColumnEntity col = column();
        when(elementRepository.findById(e.getId())).thenReturn(Optional.of(e));
        when(columnRepository.findById(col.getId())).thenReturn(Optional.of(col));
        when(columnRepository.findByLogicalDataElementId(e.getId())).thenReturn(List.of());
        when(elementRepository.findById(e.getId())).thenReturn(Optional.of(e));

        service.bindPhysicalColumn(e.getId(), col.getId());

        verify(columnRepository).bindLogicalElement(col.getId(), e.getId());
    }

    @Test
    void bindPhysicalColumn_columnNotFound_throws() {
        LogicalDataElementEntity e = element(UUID.randomUUID());
        UUID colId = UUID.randomUUID();
        when(elementRepository.findById(e.getId())).thenReturn(Optional.of(e));
        when(columnRepository.findById(colId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.bindPhysicalColumn(e.getId(), colId))
            .isInstanceOf(NoSuchElementException.class)
            .hasMessageContaining(colId.toString());
    }

    // ── deleteElement ─────────────────────────────────────────────────────

    @Test
    void deleteElement_callsDeleteById() {
        UUID id = UUID.randomUUID();
        service.deleteElement(id);
        verify(elementRepository).deleteById(id);
    }

    // ── addVocabMapping ───────────────────────────────────────────────────

    @Test
    void addVocabMapping_defaultsMatchTypeToExactMatch() {
        LogicalDataElementEntity e = element(UUID.randomUUID());
        when(elementRepository.findById(e.getId())).thenReturn(Optional.of(e));

        UUID vocabId = UUID.randomUUID();
        VocabMappingRequest req = new VocabMappingRequest(
            vocabId, "https://schema.org/price", "price", "A price", null
        );
        VocabMappingEntity saved = mapping(e.getId(), vocabId);
        when(mappingRepository.save(any())).thenReturn(saved);

        VocabMappingResponse result = service.addVocabMapping(e.getId(), req);

        ArgumentCaptor<VocabMappingEntity> captor = ArgumentCaptor.forClass(VocabMappingEntity.class);
        verify(mappingRepository).save(captor.capture());
        assertThat(captor.getValue().getMatchType()).isEqualTo("exactMatch");
        assertThat(result.id()).isEqualTo(saved.getId());
    }

    @Test
    void addVocabMapping_customMatchType_preserved() {
        LogicalDataElementEntity e = element(UUID.randomUUID());
        when(elementRepository.findById(e.getId())).thenReturn(Optional.of(e));

        UUID vocabId = UUID.randomUUID();
        VocabMappingRequest req = new VocabMappingRequest(
            vocabId, "https://schema.org/price", "price", null, "broadMatch"
        );
        VocabMappingEntity saved = mapping(e.getId(), vocabId);
        when(mappingRepository.save(any())).thenReturn(saved);

        service.addVocabMapping(e.getId(), req);

        ArgumentCaptor<VocabMappingEntity> captor = ArgumentCaptor.forClass(VocabMappingEntity.class);
        verify(mappingRepository).save(captor.capture());
        assertThat(captor.getValue().getMatchType()).isEqualTo("broadMatch");
    }

    @Test
    void addVocabMapping_elementNotFound_throws() {
        UUID id = UUID.randomUUID();
        when(elementRepository.findById(id)).thenReturn(Optional.empty());

        VocabMappingRequest req = new VocabMappingRequest(UUID.randomUUID(), "iri", null, null, null);
        assertThatThrownBy(() -> service.addVocabMapping(id, req)).isInstanceOf(NoSuchElementException.class);
    }

    // ── listVocabMappings ─────────────────────────────────────────────────

    @Test
    void listVocabMappings_returnsMappedList() {
        UUID elementId = UUID.randomUUID();
        VocabMappingEntity m = mapping(elementId, UUID.randomUUID());
        when(mappingRepository.findByLogicalElementId(elementId)).thenReturn(List.of(m));

        List<VocabMappingResponse> result = service.listVocabMappings(elementId);

        assertThat(result).hasSize(1);
        assertThat(result.get(0).id()).isEqualTo(m.getId());
    }

    // ── deleteVocabMapping ────────────────────────────────────────────────

    @Test
    void deleteVocabMapping_callsDeleteById() {
        UUID id = UUID.randomUUID();
        service.deleteVocabMapping(id);
        verify(mappingRepository).deleteById(id);
    }

    // ── fixtures ──────────────────────────────────────────────────────────

    private LogicalModelEntity model(UUID datasetId) {
        LogicalModelEntity m = new LogicalModelEntity();
        m.setId(UUID.randomUUID());
        m.setDatasetId(datasetId);
        m.setName("Trade Model");
        m.setVersion("1.0");
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
        e.setVocabMappings(List.of());
        return e;
    }

    private CsvwColumnEntity column() {
        CsvwColumnEntity c = new CsvwColumnEntity();
        c.setId(UUID.randomUUID());
        c.setSchemaId(UUID.randomUUID());
        c.setOrdinal(0);
        c.setName("amount");
        return c;
    }

    private VocabMappingEntity mapping(UUID elementId, UUID vocabId) {
        VocabMappingEntity m = new VocabMappingEntity();
        m.setId(UUID.randomUUID());
        m.setLogicalElementId(elementId);
        m.setVocabularyId(vocabId);
        m.setConceptIri("https://schema.org/price");
        m.setMatchType("exactMatch");
        m.setCreatedAt(OffsetDateTime.now());
        return m;
    }
}
