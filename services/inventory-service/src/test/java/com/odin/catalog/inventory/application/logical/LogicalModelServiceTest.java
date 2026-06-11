package com.odin.catalog.inventory.application.logical;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.odin.catalog.inventory.api.v1.dto.*;
import com.odin.catalog.inventory.infrastructure.ai.AiServiceClient;
import com.odin.catalog.inventory.infrastructure.jpa.entity.*;
import com.odin.catalog.inventory.infrastructure.jpa.repository.*;
import com.odin.catalog.inventory.infrastructure.kafka.CatalogEventProducer;
import com.odin.catalog.inventory.application.logical.BulkRecommendationJobRegistry;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.transaction.PlatformTransactionManager;

import com.odin.catalog.inventory.api.v1.dto.RecommendedVocabMapping;
import com.odin.catalog.inventory.infrastructure.jpa.entity.DatasetEntity;
import com.odin.catalog.inventory.infrastructure.jpa.entity.DatasetSemanticTagEntity;

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

    @Mock LogicalModelRepository              modelRepository;
    @Mock LogicalDataElementRepository        elementRepository;
    @Mock VocabMappingRepository              mappingRepository;
    @Mock CsvwColumnRepository                columnRepository;
    @Mock DatasetRepository                   datasetRepository;
    @Mock DatasetSemanticTagRepository        semanticTagRepository;
    @Mock CatalogEventProducer                eventProducer;
    @Mock VocabularyRepository                vocabularyRepository;
    @Mock AiServiceClient                     aiServiceClient;
    @Mock PlatformTransactionManager          transactionManager;
    @Mock BulkRecommendationJobRegistry       jobRegistry;
    @Spy  ObjectMapper                        objectMapper = new ObjectMapper();

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
            "MonetaryAmount", 0, false, true, true, null, false, false
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
            "x", null, null, null, 0, false, false, true, null, false, false
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
            "Date", 5, true, false, false, null, false, false
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

        LogicalDataElementRequest req = new LogicalDataElementRequest("x", null, null, null, 0, false, false, true, null, false, false);
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

    // ── unbindPhysicalColumn ──────────────────────────────────────────────

    @Test
    void unbindPhysicalColumn_callsUnbindAndReturnsElement() {
        LogicalDataElementEntity e = element(UUID.randomUUID());
        when(elementRepository.findById(e.getId())).thenReturn(Optional.of(e));
        when(columnRepository.findByLogicalDataElementId(e.getId())).thenReturn(List.of());

        service.unbindPhysicalColumn(e.getId());

        verify(columnRepository).unbindLogicalElement(e.getId());
    }

    // ── deleteVocabMapping ────────────────────────────────────────────────

    @Test
    void deleteVocabMapping_withNullDataset_stillDeletes() {
        UUID mappingId = UUID.randomUUID();
        when(mappingRepository.findById(mappingId)).thenReturn(Optional.empty());

        service.deleteVocabMapping(mappingId);

        verify(mappingRepository).deleteById(mappingId);
    }

    // ── suggestElementMappings ────────────────────────────────────────────

    @Test
    void suggestElementMappings_emptyColumns_returnsEmpty() {
        UUID distId = UUID.randomUUID();
        UUID modelId = UUID.randomUUID();
        UUID schemaId = UUID.nameUUIDFromBytes((distId.toString() + ":schema").getBytes());
        when(columnRepository.findBySchemaIdOrderByOrdinalAsc(schemaId)).thenReturn(List.of());

        List<ColumnElementSuggestion> result = service.suggestElementMappings(distId, modelId);

        assertThat(result).isEmpty();
    }

    @Test
    void suggestElementMappings_matchingNames_returnsSuggestion() {
        UUID distId = UUID.randomUUID();
        UUID modelId = UUID.randomUUID();
        UUID schemaId = UUID.nameUUIDFromBytes((distId.toString() + ":schema").getBytes());

        CsvwColumnEntity col = column();
        col.setName("trade_amount");
        col.setLogicalDataElementId(null);
        when(columnRepository.findBySchemaIdOrderByOrdinalAsc(schemaId)).thenReturn(List.of(col));

        LogicalDataElementEntity el = element(modelId);
        el.setName("trade_amount");
        when(elementRepository.findByLogicalModelIdOrderByOrdinalAsc(modelId)).thenReturn(List.of(el));

        List<ColumnElementSuggestion> result = service.suggestElementMappings(distId, modelId);

        assertThat(result).hasSize(1);
        assertThat(result.get(0).columnName()).isEqualTo("trade_amount");
    }

    // ── acceptClassification ──────────────────────────────────────────────

    @Test
    void acceptClassification_pendingRecommendation_promotesToAccepted() {
        LogicalDataElementEntity e = element(UUID.randomUUID());
        e.setRecommendedClassification("CONFIDENTIAL");
        when(elementRepository.findById(e.getId())).thenReturn(Optional.of(e));
        when(elementRepository.save(e)).thenReturn(e);
        when(columnRepository.findByLogicalDataElementId(e.getId())).thenReturn(List.of());

        LogicalDataElementResponse result = service.acceptClassification(e.getId());

        assertThat(e.getClassification()).isEqualTo("CONFIDENTIAL");
        assertThat(e.getRecommendedClassification()).isNull();
        assertThat(result.id()).isEqualTo(e.getId());
    }

    @Test
    void acceptClassification_noPendingRecommendation_throwsNoSuchElement() {
        LogicalDataElementEntity e = element(UUID.randomUUID());
        when(elementRepository.findById(e.getId())).thenReturn(Optional.of(e));

        assertThatThrownBy(() -> service.acceptClassification(e.getId()))
            .isInstanceOf(NoSuchElementException.class);
    }

    // ── rejectClassification ──────────────────────────────────────────────

    @Test
    void rejectClassification_clearsRecommendation() {
        LogicalDataElementEntity e = element(UUID.randomUUID());
        e.setRecommendedClassification("PUBLIC");
        when(elementRepository.findById(e.getId())).thenReturn(Optional.of(e));
        when(elementRepository.save(e)).thenReturn(e);
        when(columnRepository.findByLogicalDataElementId(e.getId())).thenReturn(List.of());

        service.rejectClassification(e.getId());

        assertThat(e.getRecommendedClassification()).isNull();
        verify(elementRepository).save(e);
    }

    // ── acceptDescription ─────────────────────────────────────────────────

    @Test
    void acceptDescription_promotesRecommendedDescription() {
        LogicalDataElementEntity e = element(UUID.randomUUID());
        e.setRecommendedDescription("A monetary amount field");
        when(elementRepository.findById(e.getId())).thenReturn(Optional.of(e));
        when(elementRepository.save(e)).thenReturn(e);
        when(columnRepository.findByLogicalDataElementId(e.getId())).thenReturn(List.of());

        service.acceptDescription(e.getId());

        assertThat(e.getDescription()).isEqualTo("A monetary amount field");
        assertThat(e.getRecommendedDescription()).isNull();
    }

    @Test
    void acceptDescription_noPending_throwsNoSuchElement() {
        LogicalDataElementEntity e = element(UUID.randomUUID());
        when(elementRepository.findById(e.getId())).thenReturn(Optional.of(e));

        assertThatThrownBy(() -> service.acceptDescription(e.getId()))
            .isInstanceOf(NoSuchElementException.class);
    }

    // ── rejectDescription ─────────────────────────────────────────────────

    @Test
    void rejectDescription_clearsRecommendedDescription() {
        LogicalDataElementEntity e = element(UUID.randomUUID());
        e.setRecommendedDescription("Some description");
        when(elementRepository.findById(e.getId())).thenReturn(Optional.of(e));
        when(elementRepository.save(e)).thenReturn(e);
        when(columnRepository.findByLogicalDataElementId(e.getId())).thenReturn(List.of());

        service.rejectDescription(e.getId());

        assertThat(e.getRecommendedDescription()).isNull();
    }

    // ── acceptPii ─────────────────────────────────────────────────────────

    @Test
    void acceptPii_pendingRecommendation_promotesToAccepted() {
        LogicalDataElementEntity e = element(UUID.randomUUID());
        e.setRecommendedIsPersonalInformation(true);
        e.setRecommendedIsDirectIdentifier(false);
        when(elementRepository.findById(e.getId())).thenReturn(Optional.of(e));
        when(elementRepository.save(e)).thenReturn(e);
        when(columnRepository.findByLogicalDataElementId(e.getId())).thenReturn(List.of());

        service.acceptPii(e.getId());

        assertThat(e.isPersonalInformation()).isTrue();
        assertThat(e.getRecommendedIsPersonalInformation()).isNull();
    }

    @Test
    void acceptPii_noPending_throwsNoSuchElement() {
        LogicalDataElementEntity e = element(UUID.randomUUID());
        when(elementRepository.findById(e.getId())).thenReturn(Optional.of(e));

        assertThatThrownBy(() -> service.acceptPii(e.getId()))
            .isInstanceOf(NoSuchElementException.class);
    }

    // ── rejectPii ─────────────────────────────────────────────────────────

    @Test
    void rejectPii_clearsRecommendation() {
        LogicalDataElementEntity e = element(UUID.randomUUID());
        e.setRecommendedIsPersonalInformation(true);
        when(elementRepository.findById(e.getId())).thenReturn(Optional.of(e));
        when(elementRepository.save(e)).thenReturn(e);
        when(columnRepository.findByLogicalDataElementId(e.getId())).thenReturn(List.of());

        service.rejectPii(e.getId());

        assertThat(e.getRecommendedIsPersonalInformation()).isNull();
    }

    // ── acceptSemanticTag ─────────────────────────────────────────────────

    @Test
    void acceptSemanticTag_savesTagAndPublishesUpdate() {
        UUID datasetId = UUID.randomUUID();
        DatasetSemanticTagEntity saved = new DatasetSemanticTagEntity();
        saved.setId(UUID.randomUUID());
        saved.setDatasetId(datasetId);
        saved.setSemanticType("fibo:Customer");
        when(semanticTagRepository.save(any())).thenReturn(saved);
        when(datasetRepository.findById(datasetId)).thenReturn(Optional.empty());

        DatasetSemanticTagResponse result = service.acceptSemanticTag(datasetId,
            new DatasetSemanticTagRequest("fibo:Customer", "https://spec.edmcouncil.org/fibo/ontology/Customer"));

        assertThat(result.type()).isEqualTo("fibo:Customer");
        assertThat(result.datasetId()).isEqualTo(datasetId);
    }

    // ── deleteSemanticTag ─────────────────────────────────────────────────

    @Test
    void deleteSemanticTag_found_deletesTag() {
        UUID datasetId = UUID.randomUUID();
        UUID tagId = UUID.randomUUID();
        DatasetSemanticTagEntity tag = new DatasetSemanticTagEntity();
        tag.setId(tagId);
        tag.setDatasetId(datasetId);
        when(semanticTagRepository.findByIdAndDatasetId(tagId, datasetId)).thenReturn(Optional.of(tag));
        when(datasetRepository.findById(datasetId)).thenReturn(Optional.empty());

        service.deleteSemanticTag(datasetId, tagId);

        verify(semanticTagRepository).deleteById(tagId);
    }

    @Test
    void deleteSemanticTag_notFound_throwsNoSuchElement() {
        UUID datasetId = UUID.randomUUID();
        UUID tagId = UUID.randomUUID();
        when(semanticTagRepository.findByIdAndDatasetId(tagId, datasetId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.deleteSemanticTag(datasetId, tagId))
            .isInstanceOf(NoSuchElementException.class);
    }

    // ── getSemanticContext ────────────────────────────────────────────────

    @Test
    void getSemanticContext_noModels_returnsEmptyContext() {
        UUID datasetId = UUID.randomUUID();
        when(modelRepository.findByDatasetIdOrderByCreatedAtDesc(datasetId)).thenReturn(List.of());
        when(mappingRepository.findSemanticTypesByDatasetId(datasetId)).thenReturn(List.of());
        when(semanticTagRepository.findByDatasetIdOrderByCreatedAtDesc(datasetId)).thenReturn(List.of());

        DatasetSemanticContext result = service.getSemanticContext(datasetId);

        assertThat(result.logicalElementNames()).isEmpty();
        assertThat(result.vocabConceptIris()).isEmpty();
        assertThat(result.acceptedTags()).isEmpty();
    }

    // ── updateStatus — non-published does not trigger publishSemanticUpdate ─

    @Test
    void updateStatus_nonPublished_doesNotPublish() {
        LogicalModelEntity m = model(UUID.randomUUID());
        m.setElements(List.of());
        when(modelRepository.findById(m.getId())).thenReturn(Optional.of(m));
        when(modelRepository.save(m)).thenReturn(m);

        service.updateStatus(m.getId(), "draft"); // false branch

        verify(eventProducer, never()).publishDatasetChanged(any(), any(DatasetEntity.class), any(DatasetSemanticContext.class));
    }

    // ── updateStatus — published triggers publishSemanticUpdate ──────────

    @Test
    void updateStatus_published_withDataset_publishesSemanticUpdate() {
        UUID datasetId = UUID.randomUUID();
        LogicalModelEntity m = model(datasetId);
        m.setElements(List.of());
        when(modelRepository.findById(m.getId())).thenReturn(Optional.of(m));
        when(modelRepository.save(m)).thenReturn(m);

        DatasetEntity ds = dataset(datasetId);
        when(datasetRepository.findById(datasetId)).thenReturn(Optional.of(ds));
        when(modelRepository.findByDatasetIdOrderByCreatedAtDesc(datasetId)).thenReturn(List.of(m));
        when(mappingRepository.findSemanticTypesByDatasetId(datasetId)).thenReturn(List.of());
        when(semanticTagRepository.findByDatasetIdOrderByCreatedAtDesc(datasetId)).thenReturn(List.of());

        service.updateStatus(m.getId(), "published");

        verify(eventProducer).publishDatasetChanged(eq("UPDATED"), eq(ds), any(DatasetSemanticContext.class));
    }

    // ── recommendClassification ───────────────────────────────────────────

    @Test
    void recommendClassification_aiReturnsResult_appliesRecommendation() {
        LogicalDataElementEntity e = element(UUID.randomUUID());
        when(elementRepository.findById(e.getId())).thenReturn(Optional.of(e));
        when(elementRepository.save(e)).thenReturn(e);
        when(columnRepository.findByLogicalDataElementId(e.getId())).thenReturn(List.of());

        AiServiceClient.ElementClassificationResult result =
            new AiServiceClient.ElementClassificationResult(e.getId().toString(), "CONFIDENTIAL", "Monetary data");
        when(aiServiceClient.classify(any())).thenReturn(List.of(result));

        LogicalDataElementResponse response = service.recommendClassification(e.getId());

        assertThat(e.getRecommendedClassification()).isEqualTo("CONFIDENTIAL");
        assertThat(response.id()).isEqualTo(e.getId());
    }

    @Test
    void recommendClassification_aiReturnsNoMatchForElement_savesWithoutApplying() {
        LogicalDataElementEntity e = element(UUID.randomUUID());
        when(elementRepository.findById(e.getId())).thenReturn(Optional.of(e));
        when(elementRepository.save(e)).thenReturn(e);
        when(columnRepository.findByLogicalDataElementId(e.getId())).thenReturn(List.of());
        when(aiServiceClient.classify(any())).thenReturn(List.of()); // no match

        service.recommendClassification(e.getId());

        assertThat(e.getRecommendedClassification()).isNull();
        verify(elementRepository).save(e);
    }

    // ── recommendDescription ──────────────────────────────────────────────

    @Test
    void recommendDescription_aiReturnsResult_setsRecommendedDescription() {
        LogicalDataElementEntity e = element(UUID.randomUUID());
        when(elementRepository.findById(e.getId())).thenReturn(Optional.of(e));
        when(elementRepository.save(e)).thenReturn(e);
        when(columnRepository.findByLogicalDataElementId(e.getId())).thenReturn(List.of());

        AiServiceClient.ElementDescriptionResult result =
            new AiServiceClient.ElementDescriptionResult(e.getId().toString(), "A monetary amount", "matches field name");
        when(aiServiceClient.describeElements(any())).thenReturn(List.of(result));

        LogicalDataElementResponse response = service.recommendDescription(e.getId());

        assertThat(e.getRecommendedDescription()).isEqualTo("A monetary amount");
        assertThat(response.id()).isEqualTo(e.getId());
    }

    @Test
    void recommendDescription_aiReturnsNoMatch_savesWithoutChanges() {
        LogicalDataElementEntity e = element(UUID.randomUUID());
        when(elementRepository.findById(e.getId())).thenReturn(Optional.of(e));
        when(elementRepository.save(e)).thenReturn(e);
        when(columnRepository.findByLogicalDataElementId(e.getId())).thenReturn(List.of());
        when(aiServiceClient.describeElements(any())).thenReturn(List.of());

        service.recommendDescription(e.getId());

        assertThat(e.getRecommendedDescription()).isNull();
    }

    // ── recommendVocabConcepts ────────────────────────────────────────────

    @Test
    void recommendVocabConcepts_aiReturnsResult_appliesVocabRecommendation() {
        LogicalDataElementEntity e = element(UUID.randomUUID());
        when(elementRepository.findById(e.getId())).thenReturn(Optional.of(e));
        when(elementRepository.save(e)).thenReturn(e);
        when(columnRepository.findByLogicalDataElementId(e.getId())).thenReturn(List.of());
        when(vocabularyRepository.findAll()).thenReturn(List.of());

        AiServiceClient.RecommendedConcept concept = new AiServiceClient.RecommendedConcept(
            "https://schema.org/price", "price", "A price value", "exactMatch", "matches field name");
        AiServiceClient.VocabConceptRecommendation rec =
            new AiServiceClient.VocabConceptRecommendation(e.getId().toString(), List.of(concept));
        when(aiServiceClient.recommendVocabConcepts(any())).thenReturn(List.of(rec));

        LogicalDataElementResponse response = service.recommendVocabConcepts(e.getId());

        assertThat(e.getRecommendedVocabMappings()).isNotNull();
        assertThat(response.id()).isEqualTo(e.getId());
    }

    @Test
    void recommendVocabConcepts_aiReturnsEmptyConcepts_noMappingsSet() {
        LogicalDataElementEntity e = element(UUID.randomUUID());
        when(elementRepository.findById(e.getId())).thenReturn(Optional.of(e));
        when(elementRepository.save(e)).thenReturn(e);
        when(columnRepository.findByLogicalDataElementId(e.getId())).thenReturn(List.of());
        when(vocabularyRepository.findAll()).thenReturn(List.of());

        AiServiceClient.VocabConceptRecommendation rec =
            new AiServiceClient.VocabConceptRecommendation(e.getId().toString(), List.of()); // empty
        when(aiServiceClient.recommendVocabConcepts(any())).thenReturn(List.of(rec));

        service.recommendVocabConcepts(e.getId());

        assertThat(e.getRecommendedVocabMappings()).isNull();
    }

    // ── acceptVocabConcepts ───────────────────────────────────────────────

    @Test
    void acceptVocabConcepts_noRecommendation_throwsNoSuchElement() {
        LogicalDataElementEntity e = element(UUID.randomUUID());
        when(elementRepository.findById(e.getId())).thenReturn(Optional.of(e));

        assertThatThrownBy(() -> service.acceptVocabConcepts(e.getId(), null))
            .isInstanceOf(NoSuchElementException.class);
    }

    @Test
    void acceptVocabConcepts_withVocabMatch_savesMapping() throws Exception {
        LogicalDataElementEntity e = element(UUID.randomUUID());
        List<RecommendedVocabMapping> recs = List.of(
            new RecommendedVocabMapping("https://schema.org/price", "price", "A price", "exactMatch", "match")
        );
        e.setRecommendedVocabMappings(new ObjectMapper().writeValueAsString(recs));

        when(elementRepository.findById(e.getId())).thenReturn(Optional.of(e));
        when(elementRepository.save(e)).thenReturn(e);
        when(columnRepository.findByLogicalDataElementId(e.getId())).thenReturn(List.of());
        when(mappingRepository.findByLogicalElementId(e.getId())).thenReturn(List.of());

        VocabularyEntity vocab = new VocabularyEntity();
        vocab.setId(UUID.randomUUID());
        vocab.setBaseIri("https://schema.org/");
        when(vocabularyRepository.findAll()).thenReturn(List.of(vocab));
        when(mappingRepository.save(any())).thenReturn(new VocabMappingEntity());

        service.acceptVocabConcepts(e.getId(), null);

        verify(mappingRepository).save(any());
        assertThat(e.getRecommendedVocabMappings()).isNull();
    }

    @Test
    void acceptVocabConcepts_withSelectedIris_onlyAcceptsSelected() throws Exception {
        LogicalDataElementEntity e = element(UUID.randomUUID());
        List<RecommendedVocabMapping> recs = List.of(
            new RecommendedVocabMapping("https://schema.org/price", "price", null, "exactMatch", null),
            new RecommendedVocabMapping("https://schema.org/amount", "amount", null, "broadMatch", null)
        );
        e.setRecommendedVocabMappings(new ObjectMapper().writeValueAsString(recs));

        when(elementRepository.findById(e.getId())).thenReturn(Optional.of(e));
        when(elementRepository.save(e)).thenReturn(e);
        when(columnRepository.findByLogicalDataElementId(e.getId())).thenReturn(List.of());
        when(mappingRepository.findByLogicalElementId(e.getId())).thenReturn(List.of());

        VocabularyEntity vocab = new VocabularyEntity();
        vocab.setId(UUID.randomUUID());
        vocab.setBaseIri("https://schema.org/");
        when(vocabularyRepository.findAll()).thenReturn(List.of(vocab));
        when(mappingRepository.save(any())).thenReturn(new VocabMappingEntity());

        service.acceptVocabConcepts(e.getId(), List.of("https://schema.org/price")); // only one

        verify(mappingRepository, times(1)).save(any()); // only one saved
    }

    @Test
    void acceptVocabConcepts_existingIriSkipped() throws Exception {
        LogicalDataElementEntity e = element(UUID.randomUUID());
        List<RecommendedVocabMapping> recs = List.of(
            new RecommendedVocabMapping("https://schema.org/price", "price", null, "exactMatch", null)
        );
        e.setRecommendedVocabMappings(new ObjectMapper().writeValueAsString(recs));

        when(elementRepository.findById(e.getId())).thenReturn(Optional.of(e));
        when(elementRepository.save(e)).thenReturn(e);
        when(columnRepository.findByLogicalDataElementId(e.getId())).thenReturn(List.of());

        VocabMappingEntity existingMapping = mapping(e.getId(), UUID.randomUUID());
        existingMapping.setConceptIri("https://schema.org/price"); // already exists
        when(mappingRepository.findByLogicalElementId(e.getId())).thenReturn(List.of(existingMapping));
        when(vocabularyRepository.findAll()).thenReturn(List.of());

        service.acceptVocabConcepts(e.getId(), null);

        verify(mappingRepository, never()).save(any()); // skipped
    }

    @Test
    void acceptVocabConcepts_noVocabFoundForIri_skipsWithWarn() throws Exception {
        LogicalDataElementEntity e = element(UUID.randomUUID());
        List<RecommendedVocabMapping> recs = List.of(
            new RecommendedVocabMapping("https://unknown.org/price", "price", null, "exactMatch", null)
        );
        e.setRecommendedVocabMappings(new ObjectMapper().writeValueAsString(recs));

        when(elementRepository.findById(e.getId())).thenReturn(Optional.of(e));
        when(elementRepository.save(e)).thenReturn(e);
        when(columnRepository.findByLogicalDataElementId(e.getId())).thenReturn(List.of());
        when(mappingRepository.findByLogicalElementId(e.getId())).thenReturn(List.of());
        when(vocabularyRepository.findAll()).thenReturn(List.of()); // no matching vocab

        service.acceptVocabConcepts(e.getId(), null);

        verify(mappingRepository, never()).save(any()); // skipped — no vocab found
    }

    // ── rejectVocabConcepts ───────────────────────────────────────────────

    @Test
    void rejectVocabConcepts_clearsRecommendation() {
        LogicalDataElementEntity e = element(UUID.randomUUID());
        e.setRecommendedVocabMappings("[{\"conceptIri\":\"https://schema.org/price\"}]");
        when(elementRepository.findById(e.getId())).thenReturn(Optional.of(e));
        when(elementRepository.save(e)).thenReturn(e);
        when(columnRepository.findByLogicalDataElementId(e.getId())).thenReturn(List.of());

        service.rejectVocabConcepts(e.getId());

        assertThat(e.getRecommendedVocabMappings()).isNull();
        verify(elementRepository).save(e);
    }

    // ── recommendPii ──────────────────────────────────────────────────────

    @Test
    void recommendPii_aiReturnsResult_appliesRecommendation() {
        UUID modelId = UUID.randomUUID();
        UUID datasetId = UUID.randomUUID();
        LogicalDataElementEntity e = element(modelId);
        LogicalModelEntity m = model(datasetId);
        DatasetEntity ds = dataset(datasetId);
        ds.setKeywords(List.of("customer", "trade"));

        when(elementRepository.findById(e.getId())).thenReturn(Optional.of(e));
        when(modelRepository.findById(modelId)).thenReturn(Optional.of(m));
        when(datasetRepository.findById(datasetId)).thenReturn(Optional.of(ds));
        when(elementRepository.save(e)).thenReturn(e);
        when(columnRepository.findByLogicalDataElementId(e.getId())).thenReturn(List.of());

        AiServiceClient.ElementPiiResult result =
            new AiServiceClient.ElementPiiResult(e.getId().toString(), true, false, "contains customer name");
        when(aiServiceClient.recommendPii(any())).thenReturn(List.of(result));

        LogicalDataElementResponse response = service.recommendPii(e.getId());

        assertThat(e.getRecommendedIsPersonalInformation()).isTrue();
        assertThat(e.getRecommendedIsDirectIdentifier()).isFalse();
        assertThat(response.id()).isEqualTo(e.getId());
    }

    @Test
    void recommendPii_noModelFound_stillCallsAi() {
        UUID modelId = UUID.randomUUID();
        LogicalDataElementEntity e = element(modelId);

        when(elementRepository.findById(e.getId())).thenReturn(Optional.of(e));
        when(modelRepository.findById(modelId)).thenReturn(Optional.empty()); // no model
        when(elementRepository.save(e)).thenReturn(e);
        when(columnRepository.findByLogicalDataElementId(e.getId())).thenReturn(List.of());
        when(aiServiceClient.recommendPii(any())).thenReturn(List.of());

        service.recommendPii(e.getId());

        verify(aiServiceClient).recommendPii(any());
    }

    // ── acceptPii — partial recommendation ───────────────────────────────

    @Test
    void acceptPii_onlyDirectIdentifierRecommended_setsDirectIdentifier() {
        LogicalDataElementEntity e = element(UUID.randomUUID());
        e.setRecommendedIsDirectIdentifier(true); // only direct id, no personal info
        when(elementRepository.findById(e.getId())).thenReturn(Optional.of(e));
        when(elementRepository.save(e)).thenReturn(e);
        when(columnRepository.findByLogicalDataElementId(e.getId())).thenReturn(List.of());

        service.acceptPii(e.getId());

        assertThat(e.isDirectIdentifier()).isTrue();
        assertThat(e.getRecommendedIsDirectIdentifier()).isNull();
    }

    // ── recommendModelClassifications ─────────────────────────────────────

    @Test
    void recommendModelClassifications_happyPath_completesJob() {
        UUID modelId = UUID.randomUUID();
        UUID jobId = UUID.randomUUID();
        LogicalModelEntity m = model(UUID.randomUUID());
        m.setElements(List.of());
        when(modelRepository.findById(modelId)).thenReturn(Optional.of(m));

        LogicalDataElementEntity e = element(modelId);
        when(elementRepository.findByLogicalModelIdOrderByOrdinalAsc(modelId)).thenReturn(List.of(e));
        when(elementRepository.findAllById(any())).thenReturn(List.of(e));
        when(elementRepository.saveAll(any())).thenReturn(List.of(e));

        AiServiceClient.ElementClassificationResult r =
            new AiServiceClient.ElementClassificationResult(e.getId().toString(), "PUBLIC", "default");
        when(aiServiceClient.classify(any())).thenReturn(List.of(r));

        service.recommendModelClassifications(modelId, jobId);

        verify(jobRegistry).markRunning(jobId);
        verify(jobRegistry).markCompleted(jobId);
        assertThat(e.getRecommendedClassification()).isEqualTo("PUBLIC");
    }

    @Test
    void recommendModelClassifications_emptyElements_completesJobEarly() {
        UUID modelId = UUID.randomUUID();
        UUID jobId = UUID.randomUUID();
        LogicalModelEntity m = model(UUID.randomUUID());
        when(modelRepository.findById(modelId)).thenReturn(Optional.of(m));
        when(elementRepository.findByLogicalModelIdOrderByOrdinalAsc(modelId)).thenReturn(List.of());

        service.recommendModelClassifications(modelId, jobId);

        verify(jobRegistry).markCompleted(jobId);
        verify(aiServiceClient, never()).classify(any());
    }

    @Test
    void recommendModelClassifications_aiThrows_marksJobFailed() {
        UUID modelId = UUID.randomUUID();
        UUID jobId = UUID.randomUUID();
        LogicalModelEntity m = model(UUID.randomUUID());
        when(modelRepository.findById(modelId)).thenReturn(Optional.of(m));

        LogicalDataElementEntity e = element(modelId);
        when(elementRepository.findByLogicalModelIdOrderByOrdinalAsc(modelId)).thenReturn(List.of(e));
        when(aiServiceClient.classify(any())).thenThrow(new RuntimeException("AI down"));

        service.recommendModelClassifications(modelId, jobId);

        verify(jobRegistry).markFailed(eq(jobId), contains("AI down"));
    }

    // ── recommendModelDescriptions ────────────────────────────────────────

    @Test
    void recommendModelDescriptions_happyPath_completesJob() {
        UUID modelId = UUID.randomUUID();
        UUID jobId = UUID.randomUUID();
        LogicalModelEntity m = model(UUID.randomUUID());
        when(modelRepository.findById(modelId)).thenReturn(Optional.of(m));

        LogicalDataElementEntity e = element(modelId);
        when(elementRepository.findByLogicalModelIdOrderByOrdinalAsc(modelId)).thenReturn(List.of(e));
        when(elementRepository.findAllById(any())).thenReturn(List.of(e));
        when(elementRepository.saveAll(any())).thenReturn(List.of(e));

        AiServiceClient.ElementDescriptionResult r =
            new AiServiceClient.ElementDescriptionResult(e.getId().toString(), "A trade amount", "field name");
        when(aiServiceClient.describeElements(any())).thenReturn(List.of(r));

        service.recommendModelDescriptions(modelId, jobId);

        verify(jobRegistry).markCompleted(jobId);
        assertThat(e.getRecommendedDescription()).isEqualTo("A trade amount");
    }

    @Test
    void recommendModelDescriptions_emptyElements_completesJobEarly() {
        UUID modelId = UUID.randomUUID();
        UUID jobId = UUID.randomUUID();
        LogicalModelEntity m = model(UUID.randomUUID());
        when(modelRepository.findById(modelId)).thenReturn(Optional.of(m));
        when(elementRepository.findByLogicalModelIdOrderByOrdinalAsc(modelId)).thenReturn(List.of());

        service.recommendModelDescriptions(modelId, jobId);

        verify(jobRegistry).markCompleted(jobId);
        verify(aiServiceClient, never()).describeElements(any());
    }

    @Test
    void recommendModelDescriptions_aiThrows_marksJobFailed() {
        UUID modelId = UUID.randomUUID();
        UUID jobId = UUID.randomUUID();
        LogicalModelEntity m = model(UUID.randomUUID());
        when(modelRepository.findById(modelId)).thenReturn(Optional.of(m));
        LogicalDataElementEntity e = element(modelId);
        when(elementRepository.findByLogicalModelIdOrderByOrdinalAsc(modelId)).thenReturn(List.of(e));
        when(aiServiceClient.describeElements(any())).thenThrow(new RuntimeException("AI down"));

        service.recommendModelDescriptions(modelId, jobId);

        verify(jobRegistry).markFailed(eq(jobId), any());
    }

    // ── recommendModelPii ─────────────────────────────────────────────────

    @Test
    void recommendModelPii_happyPath_completesJob() {
        UUID modelId = UUID.randomUUID();
        UUID jobId = UUID.randomUUID();
        UUID datasetId = UUID.randomUUID();
        LogicalModelEntity m = model(datasetId);
        when(modelRepository.findById(modelId)).thenReturn(Optional.of(m));
        when(datasetRepository.findById(datasetId)).thenReturn(Optional.empty());

        LogicalDataElementEntity e = element(modelId);
        when(elementRepository.findByLogicalModelIdOrderByOrdinalAsc(modelId)).thenReturn(List.of(e));
        when(elementRepository.findAllById(any())).thenReturn(List.of(e));
        when(elementRepository.saveAll(any())).thenReturn(List.of(e));

        AiServiceClient.ElementPiiResult r =
            new AiServiceClient.ElementPiiResult(e.getId().toString(), true, true, "identifier");
        when(aiServiceClient.recommendPii(any())).thenReturn(List.of(r));

        service.recommendModelPii(modelId, jobId);

        verify(jobRegistry).markCompleted(jobId);
        assertThat(e.getRecommendedIsPersonalInformation()).isTrue();
    }

    @Test
    void recommendModelPii_emptyElements_completesJobEarly() {
        UUID modelId = UUID.randomUUID();
        UUID jobId = UUID.randomUUID();
        LogicalModelEntity m = model(UUID.randomUUID());
        when(modelRepository.findById(modelId)).thenReturn(Optional.of(m));
        when(elementRepository.findByLogicalModelIdOrderByOrdinalAsc(modelId)).thenReturn(List.of());

        service.recommendModelPii(modelId, jobId);

        verify(jobRegistry).markCompleted(jobId);
        verify(aiServiceClient, never()).recommendPii(any());
    }

    @Test
    void recommendModelPii_aiThrows_marksJobFailed() {
        UUID modelId = UUID.randomUUID();
        UUID jobId = UUID.randomUUID();
        UUID datasetId = UUID.randomUUID();
        LogicalModelEntity m = model(datasetId);
        when(modelRepository.findById(modelId)).thenReturn(Optional.of(m));
        when(datasetRepository.findById(datasetId)).thenReturn(Optional.empty());
        LogicalDataElementEntity e = element(modelId);
        when(elementRepository.findByLogicalModelIdOrderByOrdinalAsc(modelId)).thenReturn(List.of(e));
        when(aiServiceClient.recommendPii(any())).thenThrow(new RuntimeException("AI down"));

        service.recommendModelPii(modelId, jobId);

        verify(jobRegistry).markFailed(eq(jobId), any());
    }

    // ── recommendModelVocabConcepts ───────────────────────────────────────

    @Test
    void recommendModelVocabConcepts_happyPath_completesJob() {
        UUID modelId = UUID.randomUUID();
        UUID jobId = UUID.randomUUID();
        LogicalModelEntity m = model(UUID.randomUUID());
        when(modelRepository.findById(modelId)).thenReturn(Optional.of(m));
        when(vocabularyRepository.findAll()).thenReturn(List.of());

        LogicalDataElementEntity e = element(modelId);
        when(elementRepository.findByLogicalModelIdOrderByOrdinalAsc(modelId)).thenReturn(List.of(e));
        when(elementRepository.findAllById(any())).thenReturn(List.of(e));
        when(elementRepository.saveAll(any())).thenReturn(List.of(e));

        AiServiceClient.VocabConceptRecommendation rec =
            new AiServiceClient.VocabConceptRecommendation(e.getId().toString(), List.of());
        when(aiServiceClient.recommendVocabConcepts(any())).thenReturn(List.of(rec));

        service.recommendModelVocabConcepts(modelId, jobId);

        verify(jobRegistry).markCompleted(jobId);
    }

    @Test
    void recommendModelVocabConcepts_emptyElements_completesJobEarly() {
        UUID modelId = UUID.randomUUID();
        UUID jobId = UUID.randomUUID();
        LogicalModelEntity m = model(UUID.randomUUID());
        when(modelRepository.findById(modelId)).thenReturn(Optional.of(m));
        when(elementRepository.findByLogicalModelIdOrderByOrdinalAsc(modelId)).thenReturn(List.of());

        service.recommendModelVocabConcepts(modelId, jobId);

        verify(jobRegistry).markCompleted(jobId);
    }

    @Test
    void recommendModelVocabConcepts_aiThrows_marksJobFailed() {
        UUID modelId = UUID.randomUUID();
        UUID jobId = UUID.randomUUID();
        LogicalModelEntity m = model(UUID.randomUUID());
        when(modelRepository.findById(modelId)).thenReturn(Optional.of(m));
        when(vocabularyRepository.findAll()).thenReturn(List.of());
        LogicalDataElementEntity e = element(modelId);
        when(elementRepository.findByLogicalModelIdOrderByOrdinalAsc(modelId)).thenReturn(List.of(e));
        when(aiServiceClient.recommendVocabConcepts(any())).thenThrow(new RuntimeException("AI down"));

        service.recommendModelVocabConcepts(modelId, jobId);

        verify(jobRegistry).markFailed(eq(jobId), any());
    }

    // ── getSemanticContext — with populated data ───────────────────────────

    @Test
    void getSemanticContext_withModelsAndElements_populatesAllFields() {
        UUID datasetId = UUID.randomUUID();
        LogicalModelEntity m = model(datasetId);

        VocabMappingEntity fibMapping = new VocabMappingEntity();
        fibMapping.setId(UUID.randomUUID());
        fibMapping.setConceptLabel("MonetaryAmount");
        fibMapping.setConceptIri("https://spec.edmcouncil.org/fibo/ontology/FND/Accounting/CurrencyAmount/MonetaryAmount");

        VocabMappingEntity schemaMapping = new VocabMappingEntity();
        schemaMapping.setId(UUID.randomUUID());
        schemaMapping.setConceptLabel("price");
        schemaMapping.setConceptIri("https://schema.org/price");

        LogicalDataElementEntity e = element(m.getId());
        e.setLogicalType("MonetaryAmount");
        e.setVocabMappings(List.of(fibMapping, schemaMapping));

        m.setElements(List.of(e));

        DatasetSemanticTagEntity tag = new DatasetSemanticTagEntity();
        tag.setId(UUID.randomUUID());
        tag.setDatasetId(datasetId);
        tag.setSemanticType("fibo:Account");
        tag.setVocabularyIri("https://spec.edmcouncil.org/fibo");

        when(modelRepository.findByDatasetIdOrderByCreatedAtDesc(datasetId)).thenReturn(List.of(m));
        when(mappingRepository.findSemanticTypesByDatasetId(datasetId)).thenReturn(List.of("fibo:Amount"));
        when(semanticTagRepository.findByDatasetIdOrderByCreatedAtDesc(datasetId)).thenReturn(List.of(tag));

        DatasetSemanticContext result = service.getSemanticContext(datasetId);

        assertThat(result.logicalElementNames()).containsExactly("amount");
        assertThat(result.logicalTypes()).containsExactly("MonetaryAmount");
        assertThat(result.vocabConceptLabels()).contains("MonetaryAmount", "price");
        assertThat(result.fiboConcepts()).containsExactly(
            "https://spec.edmcouncil.org/fibo/ontology/FND/Accounting/CurrencyAmount/MonetaryAmount");
        assertThat(result.acceptedTags()).hasSize(1);
        assertThat(result.acceptedTags().get(0).type()).isEqualTo("fibo:Account");
    }

    @Test
    void getSemanticContext_modelWithNullElements_skips() {
        UUID datasetId = UUID.randomUUID();
        LogicalModelEntity m = model(datasetId);
        m.setElements(null); // null elements → skip

        when(modelRepository.findByDatasetIdOrderByCreatedAtDesc(datasetId)).thenReturn(List.of(m));
        when(mappingRepository.findSemanticTypesByDatasetId(datasetId)).thenReturn(List.of());
        when(semanticTagRepository.findByDatasetIdOrderByCreatedAtDesc(datasetId)).thenReturn(List.of());

        DatasetSemanticContext result = service.getSemanticContext(datasetId);

        assertThat(result.logicalElementNames()).isEmpty();
    }

    @Test
    void getSemanticContext_elementWithNullVocabMappings_skipsVocab() {
        UUID datasetId = UUID.randomUUID();
        LogicalModelEntity m = model(datasetId);

        LogicalDataElementEntity e = element(m.getId());
        e.setVocabMappings(null); // null vocab mappings → skip
        m.setElements(List.of(e));

        when(modelRepository.findByDatasetIdOrderByCreatedAtDesc(datasetId)).thenReturn(List.of(m));
        when(mappingRepository.findSemanticTypesByDatasetId(datasetId)).thenReturn(List.of());
        when(semanticTagRepository.findByDatasetIdOrderByCreatedAtDesc(datasetId)).thenReturn(List.of());

        DatasetSemanticContext result = service.getSemanticContext(datasetId);

        assertThat(result.vocabConceptIris()).isEmpty();
    }

    // ── recommendSemanticContext ──────────────────────────────────────────

    @Test
    void recommendSemanticContext_callsAiWithContextAndDataset() {
        UUID datasetId = UUID.randomUUID();
        DatasetEntity ds = dataset(datasetId);

        when(modelRepository.findByDatasetIdOrderByCreatedAtDesc(datasetId)).thenReturn(List.of());
        when(mappingRepository.findSemanticTypesByDatasetId(datasetId)).thenReturn(List.of());
        when(semanticTagRepository.findByDatasetIdOrderByCreatedAtDesc(datasetId)).thenReturn(List.of());
        when(datasetRepository.findById(datasetId)).thenReturn(Optional.of(ds));

        AiServiceClient.SemanticRecommendationResponse aiResp =
            new AiServiceClient.SemanticRecommendationResponse(List.of(), "some rationale");
        when(aiServiceClient.recommendSemanticContext(any())).thenReturn(aiResp);

        AiServiceClient.SemanticRecommendationResponse result = service.recommendSemanticContext(datasetId);

        assertThat(result.rationale()).isEqualTo("some rationale");
        verify(aiServiceClient).recommendSemanticContext(any());
    }

    @Test
    void recommendSemanticContext_datasetNotFound_throwsNoSuchElement() {
        UUID datasetId = UUID.randomUUID();
        when(modelRepository.findByDatasetIdOrderByCreatedAtDesc(datasetId)).thenReturn(List.of());
        when(mappingRepository.findSemanticTypesByDatasetId(datasetId)).thenReturn(List.of());
        when(semanticTagRepository.findByDatasetIdOrderByCreatedAtDesc(datasetId)).thenReturn(List.of());
        when(datasetRepository.findById(datasetId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.recommendSemanticContext(datasetId))
            .isInstanceOf(NoSuchElementException.class);
    }

    // ── applyElementRequest — non-null classification ─────────────────────

    @Test
    void addElement_withNonNullClassification_setsClassification() {
        LogicalModelEntity m = model(UUID.randomUUID());
        when(modelRepository.findById(m.getId())).thenReturn(Optional.of(m));

        LogicalDataElementRequest req = new LogicalDataElementRequest(
            "amount", "Amount", null, "MonetaryAmount", 0, false, false, true,
            "CONFIDENTIAL", false, false // non-null classification
        );
        LogicalDataElementEntity saved = element(m.getId());
        saved.setClassification("CONFIDENTIAL");
        when(elementRepository.save(any())).thenReturn(saved);
        when(columnRepository.findByLogicalDataElementId(saved.getId())).thenReturn(List.of());

        service.addElement(m.getId(), req);

        ArgumentCaptor<LogicalDataElementEntity> captor = ArgumentCaptor.forClass(LogicalDataElementEntity.class);
        verify(elementRepository).save(captor.capture());
        assertThat(captor.getValue().getClassification()).isEqualTo("CONFIDENTIAL");
    }

    // ── toResponse — null elements ────────────────────────────────────────

    @Test
    void listForDataset_modelWithNullElements_returnsEmptyElementsList() {
        UUID dsId = UUID.randomUUID();
        LogicalModelEntity m = model(dsId);
        // elements deliberately NOT set → null → toResponse null branch
        when(modelRepository.findByDatasetIdOrderByCreatedAtDesc(dsId)).thenReturn(List.of(m));

        List<LogicalModelResponse> result = service.listForDataset(dsId);

        assertThat(result).hasSize(1);
        assertThat(result.get(0).elements()).isEmpty();
    }

    // ── addVocabMapping — publishSemanticUpdate path ──────────────────────

    @Test
    void addVocabMapping_withDatasetFound_publishesSemanticUpdate() {
        UUID datasetId = UUID.randomUUID();
        LogicalModelEntity m = model(datasetId);
        LogicalDataElementEntity e = element(m.getId());

        when(elementRepository.findById(e.getId())).thenReturn(Optional.of(e));
        when(modelRepository.findById(m.getId())).thenReturn(Optional.of(m));

        VocabMappingRequest req = new VocabMappingRequest(UUID.randomUUID(), "https://schema.org/price", "price", null, null);
        VocabMappingEntity saved = mapping(e.getId(), req.vocabularyId());
        when(mappingRepository.save(any())).thenReturn(saved);

        DatasetEntity ds = dataset(datasetId);
        when(datasetRepository.findById(datasetId)).thenReturn(Optional.of(ds));
        when(modelRepository.findByDatasetIdOrderByCreatedAtDesc(datasetId)).thenReturn(List.of(m));
        m.setElements(List.of(e));
        when(mappingRepository.findSemanticTypesByDatasetId(datasetId)).thenReturn(List.of());
        when(semanticTagRepository.findByDatasetIdOrderByCreatedAtDesc(datasetId)).thenReturn(List.of());

        service.addVocabMapping(e.getId(), req);

        verify(eventProducer).publishDatasetChanged(eq("UPDATED"), any(DatasetEntity.class), any(DatasetSemanticContext.class));
    }

    // ── deleteVocabMapping — full path ────────────────────────────────────

    @Test
    void deleteVocabMapping_withFullChain_publishesAndDeletes() {
        UUID mappingId = UUID.randomUUID();
        UUID elementId = UUID.randomUUID();
        UUID modelId   = UUID.randomUUID();
        UUID datasetId = UUID.randomUUID();

        VocabMappingEntity m = new VocabMappingEntity();
        m.setId(mappingId);
        m.setLogicalElementId(elementId);
        when(mappingRepository.findById(mappingId)).thenReturn(Optional.of(m));

        LogicalDataElementEntity e = element(modelId);
        e.setId(elementId);
        when(elementRepository.findById(elementId)).thenReturn(Optional.of(e));

        LogicalModelEntity model = model(datasetId);
        model.setId(modelId);
        when(modelRepository.findById(modelId)).thenReturn(Optional.of(model));

        DatasetEntity ds = dataset(datasetId);
        when(datasetRepository.findById(datasetId)).thenReturn(Optional.of(ds));
        when(modelRepository.findByDatasetIdOrderByCreatedAtDesc(datasetId)).thenReturn(List.of(model));
        model.setElements(List.of(e));
        when(mappingRepository.findSemanticTypesByDatasetId(datasetId)).thenReturn(List.of());
        when(semanticTagRepository.findByDatasetIdOrderByCreatedAtDesc(datasetId)).thenReturn(List.of());

        service.deleteVocabMapping(mappingId);

        verify(mappingRepository).deleteById(mappingId);
        verify(eventProducer).publishDatasetChanged(eq("UPDATED"), any(DatasetEntity.class), any(DatasetSemanticContext.class));
    }

    // ── suggestElementMappings — already-mapped column skipped ────────────

    @Test
    void suggestElementMappings_columnAlreadyMapped_isSkipped() {
        UUID distId  = UUID.randomUUID();
        UUID modelId = UUID.randomUUID();
        UUID schemaId = UUID.nameUUIDFromBytes((distId.toString() + ":schema").getBytes());

        CsvwColumnEntity col = column();
        col.setName("trade_amount");
        col.setLogicalDataElementId(UUID.randomUUID()); // already mapped
        when(columnRepository.findBySchemaIdOrderByOrdinalAsc(schemaId)).thenReturn(List.of(col));
        when(elementRepository.findByLogicalModelIdOrderByOrdinalAsc(modelId)).thenReturn(List.of());

        List<ColumnElementSuggestion> result = service.suggestElementMappings(distId, modelId);

        assertThat(result).isEmpty();
    }

    @Test
    void suggestElementMappings_lowJaccardScore_returnsEmpty() {
        UUID distId  = UUID.randomUUID();
        UUID modelId = UUID.randomUUID();
        UUID schemaId = UUID.nameUUIDFromBytes((distId.toString() + ":schema").getBytes());

        CsvwColumnEntity col = column();
        col.setName("xyz_completely_different");
        col.setLogicalDataElementId(null);
        when(columnRepository.findBySchemaIdOrderByOrdinalAsc(schemaId)).thenReturn(List.of(col));

        LogicalDataElementEntity el = element(modelId);
        el.setName("abcdefghijklmnop"); // very different name
        when(elementRepository.findByLogicalModelIdOrderByOrdinalAsc(modelId)).thenReturn(List.of(el));

        List<ColumnElementSuggestion> result = service.suggestElementMappings(distId, modelId);

        assertThat(result).isEmpty();
    }

    // ── toVocabInput — null vocabMappings branch ──────────────────────────

    @Test
    void recommendVocabConcepts_elementWithNullVocabMappings_handlesGracefully() {
        LogicalDataElementEntity e = element(UUID.randomUUID());
        e.setVocabMappings(null); // triggers null branch in toVocabInput

        when(elementRepository.findById(e.getId())).thenReturn(Optional.of(e));
        when(elementRepository.save(e)).thenReturn(e);
        when(columnRepository.findByLogicalDataElementId(e.getId())).thenReturn(List.of());
        when(vocabularyRepository.findAll()).thenReturn(List.of());
        when(aiServiceClient.recommendVocabConcepts(any())).thenReturn(List.of());

        assertThatCode(() -> service.recommendVocabConcepts(e.getId())).doesNotThrowAnyException();
    }

    // ── toPiiInput — dataset with null keywords ───────────────────────────

    @Test
    void recommendPii_datasetWithNullKeywords_handlesGracefully() {
        UUID modelId = UUID.randomUUID();
        UUID datasetId = UUID.randomUUID();
        LogicalDataElementEntity e = element(modelId);
        LogicalModelEntity m = model(datasetId);

        DatasetEntity ds = dataset(datasetId);
        ds.setKeywords(null); // triggers the ds.getKeywords() == null branch

        when(elementRepository.findById(e.getId())).thenReturn(Optional.of(e));
        when(modelRepository.findById(modelId)).thenReturn(Optional.of(m));
        when(datasetRepository.findById(datasetId)).thenReturn(Optional.of(ds));
        when(elementRepository.save(e)).thenReturn(e);
        when(columnRepository.findByLogicalDataElementId(e.getId())).thenReturn(List.of());
        when(aiServiceClient.recommendPii(any())).thenReturn(List.of());

        assertThatCode(() -> service.recommendPii(e.getId())).doesNotThrowAnyException();
    }

    // ── acceptSemanticTag — dataset triggers publishSemanticUpdate ────────

    @Test
    void acceptSemanticTag_withDataset_publishesUpdate() {
        UUID datasetId = UUID.randomUUID();
        DatasetSemanticTagEntity saved = new DatasetSemanticTagEntity();
        saved.setId(UUID.randomUUID());
        saved.setDatasetId(datasetId);
        saved.setSemanticType("fibo:Customer");
        when(semanticTagRepository.save(any())).thenReturn(saved);

        DatasetEntity ds = dataset(datasetId);
        when(datasetRepository.findById(datasetId)).thenReturn(Optional.of(ds));
        when(modelRepository.findByDatasetIdOrderByCreatedAtDesc(datasetId)).thenReturn(List.of());
        when(mappingRepository.findSemanticTypesByDatasetId(datasetId)).thenReturn(List.of());
        when(semanticTagRepository.findByDatasetIdOrderByCreatedAtDesc(datasetId)).thenReturn(List.of(saved));

        service.acceptSemanticTag(datasetId, new DatasetSemanticTagRequest("fibo:Customer", null));

        verify(eventProducer).publishDatasetChanged(eq("UPDATED"), eq(ds), any(DatasetSemanticContext.class));
    }

    // ── fixtures ──────────────────────────────────────────────────────────

    private DatasetEntity dataset(UUID datasetId) {
        DatasetEntity ds = new DatasetEntity();
        ds.setId(datasetId);
        ds.setTitle("Trade Dataset");
        return ds;
    }

    private VocabularyEntity vocab(String baseIri) {
        VocabularyEntity v = new VocabularyEntity();
        v.setId(UUID.randomUUID());
        v.setBaseIri(baseIri);
        v.setName("Test Vocab");
        return v;
    }

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
