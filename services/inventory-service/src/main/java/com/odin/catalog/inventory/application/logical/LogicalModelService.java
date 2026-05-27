package com.odin.catalog.inventory.application.logical;

import com.odin.catalog.inventory.api.v1.dto.*;
import com.odin.catalog.inventory.infrastructure.ai.AiServiceClient;
import com.odin.catalog.inventory.infrastructure.ai.AiServiceClient.ElementClassificationInput;
import com.odin.catalog.inventory.infrastructure.ai.AiServiceClient.ElementClassificationResult;
import com.odin.catalog.inventory.infrastructure.jpa.entity.*;
import com.odin.catalog.inventory.infrastructure.jpa.repository.*;
import com.odin.catalog.inventory.infrastructure.kafka.CatalogEventProducer;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.OffsetDateTime;
import java.util.*;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class LogicalModelService {

    private static final Logger log = LoggerFactory.getLogger(LogicalModelService.class);

    private final LogicalModelRepository modelRepository;
    private final LogicalDataElementRepository elementRepository;
    private final VocabMappingRepository mappingRepository;
    private final CsvwColumnRepository columnRepository;
    private final DatasetRepository datasetRepository;
    private final DatasetSemanticTagRepository semanticTagRepository;
    private final CatalogEventProducer eventProducer;
    private final AiServiceClient aiServiceClient;
    private final PlatformTransactionManager transactionManager;
    private final BulkRecommendationJobRegistry jobRegistry;

    @Transactional(readOnly = true)
    public List<LogicalModelResponse> listForDataset(UUID datasetId) {
        return modelRepository.findByDatasetIdOrderByCreatedAtDesc(datasetId)
            .stream().map(this::toResponse).toList();
    }

    @Transactional(readOnly = true)
    public LogicalModelResponse get(UUID id) {
        return toResponse(findModelOrThrow(id));
    }

    @Transactional
    public LogicalModelResponse create(UUID datasetId, LogicalModelRequest request) {
        LogicalModelEntity entity = new LogicalModelEntity();
        entity.setDatasetId(datasetId);
        entity.setName(request.name());
        entity.setDescription(request.description());
        if (request.version() != null) entity.setVersion(request.version());
        return toResponse(modelRepository.save(entity));
    }

    @Transactional
    public LogicalModelResponse updateStatus(UUID id, String newStatus) {
        LogicalModelEntity entity = findModelOrThrow(id);
        entity.setStatus(newStatus);
        LogicalModelResponse result = toResponse(modelRepository.save(entity));
        if ("published".equals(newStatus)) {
            publishSemanticUpdate(entity.getDatasetId());
        }
        return result;
    }

    @Transactional
    public void delete(UUID id) {
        modelRepository.deleteById(id);
    }

    @Transactional
    public LogicalDataElementResponse addElement(UUID modelId, LogicalDataElementRequest request) {
        findModelOrThrow(modelId);
        LogicalDataElementEntity entity = new LogicalDataElementEntity();
        entity.setLogicalModelId(modelId);
        applyElementRequest(entity, request);
        return toElementResponse(elementRepository.save(entity));
    }

    @Transactional(readOnly = true)
    public List<LogicalDataElementResponse> listElements(UUID modelId) {
        return elementRepository.findByLogicalModelIdOrderByOrdinalAsc(modelId)
            .stream().map(this::toElementResponse).toList();
    }

    @Transactional
    public LogicalDataElementResponse updateElement(UUID elementId, LogicalDataElementRequest request) {
        LogicalDataElementEntity entity = findElementOrThrow(elementId);
        applyElementRequest(entity, request);
        return toElementResponse(elementRepository.save(entity));
    }

    @Transactional
    public LogicalDataElementResponse bindPhysicalColumn(UUID elementId, UUID physicalColumnId) {
        findElementOrThrow(elementId);
        columnRepository.findById(physicalColumnId)
            .orElseThrow(() -> new NoSuchElementException("CsvwColumn not found: " + physicalColumnId));
        columnRepository.bindLogicalElement(physicalColumnId, elementId);
        return toElementResponse(findElementOrThrow(elementId));
    }

    @Transactional
    public LogicalDataElementResponse unbindPhysicalColumn(UUID elementId) {
        findElementOrThrow(elementId);
        columnRepository.unbindLogicalElement(elementId);
        return toElementResponse(findElementOrThrow(elementId));
    }

    @Transactional
    public void deleteElement(UUID elementId) {
        elementRepository.deleteById(elementId);
    }

    @Transactional
    public VocabMappingResponse addVocabMapping(UUID elementId, VocabMappingRequest request) {
        LogicalDataElementEntity element = findElementOrThrow(elementId);
        VocabMappingEntity entity = new VocabMappingEntity();
        entity.setLogicalElementId(elementId);
        entity.setVocabularyId(request.vocabularyId());
        entity.setConceptIri(request.conceptIri());
        entity.setConceptLabel(request.conceptLabel());
        entity.setConceptDefinition(request.conceptDefinition());
        entity.setMatchType(request.matchType() != null ? request.matchType() : "exactMatch");
        VocabMappingResponse response = toMappingResponse(mappingRepository.save(entity));
        modelRepository.findById(element.getLogicalModelId())
            .map(LogicalModelEntity::getDatasetId)
            .ifPresent(this::publishSemanticUpdate);
        return response;
    }

    @Transactional(readOnly = true)
    public List<VocabMappingResponse> listVocabMappings(UUID elementId) {
        return mappingRepository.findByLogicalElementId(elementId)
            .stream().map(this::toMappingResponse).toList();
    }

    @Transactional
    public void deleteVocabMapping(UUID mappingId) {
        UUID datasetId = mappingRepository.findById(mappingId)
            .flatMap(m -> elementRepository.findById(m.getLogicalElementId()))
            .flatMap(e -> modelRepository.findById(e.getLogicalModelId()))
            .map(LogicalModelEntity::getDatasetId)
            .orElse(null);
        mappingRepository.deleteById(mappingId);
        if (datasetId != null) publishSemanticUpdate(datasetId);
    }

    @Transactional(readOnly = true)
    public List<ColumnElementSuggestion> suggestElementMappings(UUID distributionId, UUID modelId) {
        // Schema is stored under a deterministic key — same derivation used by DistributionsController
        UUID schemaId = UUID.nameUUIDFromBytes((distributionId.toString() + ":schema").getBytes());
        List<CsvwColumnEntity> columns = columnRepository.findBySchemaIdOrderByOrdinalAsc(schemaId);
        List<LogicalDataElementEntity> elements = elementRepository.findByLogicalModelIdOrderByOrdinalAsc(modelId);

        if (columns.isEmpty() || elements.isEmpty()) return List.of();

        List<ColumnElementSuggestion> suggestions = new ArrayList<>();
        for (CsvwColumnEntity col : columns) {
            if (col.getLogicalDataElementId() != null) continue; // already mapped
            bestMatch(col, elements).ifPresent(suggestions::add);
        }
        return suggestions;
    }

    private Optional<ColumnElementSuggestion> bestMatch(
            CsvwColumnEntity col, List<LogicalDataElementEntity> elements) {
        Set<String> colTokens = tokenize(col.getName());
        double bestScore = 0.0;
        LogicalDataElementEntity bestEl = null;
        for (LogicalDataElementEntity el : elements) {
            double score = jaccard(colTokens, tokenize(el.getName()));
            if (score > bestScore) { bestScore = score; bestEl = el; }
        }
        if (bestEl == null || bestScore < 0.3) return Optional.empty();
        return Optional.of(new ColumnElementSuggestion(
                col.getId(), col.getName(),
                bestEl.getId(), bestEl.getName(),
                Math.round(bestScore * 100.0) / 100.0));
    }

    private static final Pattern SPLIT = Pattern.compile("[_\\-\\s]+|(?<=[a-z])(?=[A-Z])");

    private Set<String> tokenize(String name) {
        Set<String> tokens = new HashSet<>();
        for (String t : SPLIT.split(name)) {
            String lower = t.toLowerCase();
            if (!lower.isBlank()) tokens.add(lower);
        }
        return tokens;
    }

    private double jaccard(Set<String> a, Set<String> b) {
        if (a.isEmpty() || b.isEmpty()) return 0.0;
        Set<String> union = new HashSet<>(a);
        union.addAll(b);
        Set<String> intersection = new HashSet<>(a);
        intersection.retainAll(b);
        return (double) intersection.size() / union.size();
    }

    // No @Transactional here: the read and write phases use their own short transactions
    // so the DB connection is released before the (potentially multi-minute) AI call.
    public LogicalDataElementResponse recommendClassification(UUID elementId) {
        TransactionTemplate readTx = new TransactionTemplate(transactionManager);
        readTx.setReadOnly(true);
        ElementClassificationInput input = readTx.execute(
            s -> toClassificationInput(findElementOrThrow(elementId)));

        List<ElementClassificationResult> results = aiServiceClient.classify(List.of(input));

        return new TransactionTemplate(transactionManager).execute(s -> {
            LogicalDataElementEntity entity = findElementOrThrow(elementId);
            results.stream().filter(r -> elementId.toString().equals(r.elementId())).findFirst()
                .ifPresent(r -> applyRecommendation(entity, r));
            return toElementResponse(elementRepository.save(entity));
        });
    }

    // No @Transactional here: same split-transaction pattern for bulk recommendation.
    @Async
    public void recommendModelClassifications(UUID modelId, UUID jobId) {
        try {
            jobRegistry.markRunning(jobId);

            TransactionTemplate readTx = new TransactionTemplate(transactionManager);
            readTx.setReadOnly(true);
            List<ElementClassificationInput> inputs = readTx.execute(s -> {
                findModelOrThrow(modelId);
                return elementRepository.findByLogicalModelIdOrderByOrdinalAsc(modelId)
                    .stream().map(this::toClassificationInput).toList();
            });
            if (inputs == null || inputs.isEmpty()) {
                jobRegistry.markCompleted(jobId);
                return;
            }

            List<ElementClassificationResult> results = aiServiceClient.classify(inputs);
            Map<String, ElementClassificationResult> byId = results.stream()
                .collect(Collectors.toMap(ElementClassificationResult::elementId, r -> r));

            new TransactionTemplate(transactionManager).execute(s -> {
                List<UUID> ids = inputs.stream().map(i -> UUID.fromString(i.elementId())).toList();
                List<LogicalDataElementEntity> entities = elementRepository.findAllById(ids);
                entities.forEach(e -> {
                    ElementClassificationResult r = byId.get(e.getId().toString());
                    if (r != null) applyRecommendation(e, r);
                });
                elementRepository.saveAll(entities);
                return null;
            });

            jobRegistry.markCompleted(jobId);
        } catch (Exception e) {
            jobRegistry.markFailed(jobId, e.getMessage());
        }
    }

    @Transactional
    public LogicalDataElementResponse acceptClassification(UUID elementId) {
        LogicalDataElementEntity entity = findElementOrThrow(elementId);
        if (entity.getRecommendedClassification() == null) {
            throw new NoSuchElementException("No pending recommendation for element: " + elementId);
        }
        entity.setClassification(entity.getRecommendedClassification());
        entity.setRecommendedClassification(null);
        entity.setClassificationReasoning(null);
        entity.setClassificationRecommendedAt(null);
        return toElementResponse(elementRepository.save(entity));
    }

    @Transactional
    public LogicalDataElementResponse rejectClassification(UUID elementId) {
        LogicalDataElementEntity entity = findElementOrThrow(elementId);
        entity.setRecommendedClassification(null);
        entity.setClassificationReasoning(null);
        entity.setClassificationRecommendedAt(null);
        return toElementResponse(elementRepository.save(entity));
    }

    @Transactional(readOnly = true)
    public DatasetSemanticContext getSemanticContext(UUID datasetId) {
        List<LogicalModelEntity> models = modelRepository.findByDatasetIdOrderByCreatedAtDesc(datasetId);
        List<String> semanticTypes = mappingRepository.findSemanticTypesByDatasetId(datasetId);
        List<String> vocabConceptLabels = new ArrayList<>();
        List<String> vocabConceptIris = new ArrayList<>();
        List<String> fiboConcepts = new ArrayList<>();
        List<String> logicalElementNames = new ArrayList<>();
        List<String> logicalTypes = new ArrayList<>();

        for (LogicalModelEntity model : models) {
            if (model.getElements() == null) continue;
            for (LogicalDataElementEntity el : model.getElements()) {
                if (el.getName() != null) logicalElementNames.add(el.getName());
                if (el.getLogicalType() != null) logicalTypes.add(el.getLogicalType());
                if (el.getVocabMappings() == null) continue;
                for (VocabMappingEntity m : el.getVocabMappings()) {
                    if (m.getConceptLabel() != null) vocabConceptLabels.add(m.getConceptLabel());
                    if (m.getConceptIri() != null) {
                        vocabConceptIris.add(m.getConceptIri());
                        if (m.getConceptIri().contains("edmcouncil.org/fibo")) {
                            fiboConcepts.add(m.getConceptIri());
                        }
                    }
                }
            }
        }

        List<DatasetSemanticContext.AcceptedTag> acceptedTags =
            semanticTagRepository.findByDatasetIdOrderByCreatedAtDesc(datasetId)
                .stream()
                .map(t -> new DatasetSemanticContext.AcceptedTag(t.getId(), t.getSemanticType(), t.getVocabularyIri()))
                .toList();

        return new DatasetSemanticContext(
            semanticTypes,
            vocabConceptLabels.stream().distinct().toList(),
            vocabConceptIris.stream().distinct().toList(),
            fiboConcepts.stream().distinct().toList(),
            logicalElementNames.stream().distinct().toList(),
            logicalTypes.stream().distinct().toList(),
            acceptedTags
        );
    }

    @Transactional
    public DatasetSemanticTagResponse acceptSemanticTag(UUID datasetId, DatasetSemanticTagRequest request) {
        DatasetSemanticTagEntity tag = new DatasetSemanticTagEntity();
        tag.setDatasetId(datasetId);
        tag.setSemanticType(request.type());
        tag.setVocabularyIri(request.vocabularyIri());
        tag = semanticTagRepository.save(tag);
        publishSemanticUpdate(datasetId);
        return toTagResponse(tag);
    }

    @Transactional
    public void deleteSemanticTag(UUID datasetId, UUID tagId) {
        semanticTagRepository.findByIdAndDatasetId(tagId, datasetId)
            .orElseThrow(() -> new NoSuchElementException("Semantic tag not found: " + tagId));
        semanticTagRepository.deleteById(tagId);
        publishSemanticUpdate(datasetId);
    }

    @Transactional(readOnly = true)
    public AiServiceClient.SemanticRecommendationResponse recommendSemanticContext(UUID datasetId) {
        DatasetSemanticContext ctx = getSemanticContext(datasetId);
        var dataset = datasetRepository.findById(datasetId)
            .orElseThrow(() -> new NoSuchElementException("Dataset not found: " + datasetId));

        var request = new AiServiceClient.SemanticRecommendationRequest(
            datasetId.toString(),
            dataset.getTitle(),
            dataset.getDescription(),
            dataset.getKeywords(),
            dataset.getThemes(),
            ctx.logicalElementNames(),
            ctx.logicalTypes(),
            ctx.vocabConceptLabels(),
            ctx.vocabConceptIris()
        );
        return aiServiceClient.recommendSemanticContext(request);
    }

    private void publishSemanticUpdate(UUID datasetId) {
        try {
            datasetRepository.findById(datasetId).ifPresent(dataset -> {
                DatasetSemanticContext ctx = getSemanticContext(datasetId);
                // Merge accepted tags into semanticTypes for downstream consumers
                List<String> mergedTypes = new ArrayList<>(ctx.semanticTypes());
                ctx.acceptedTags().stream().map(DatasetSemanticContext.AcceptedTag::type)
                    .filter(t -> !mergedTypes.contains(t))
                    .forEach(mergedTypes::add);
                DatasetSemanticContext enriched = new DatasetSemanticContext(
                    mergedTypes,
                    ctx.vocabConceptLabels(), ctx.vocabConceptIris(), ctx.fiboConcepts(),
                    ctx.logicalElementNames(), ctx.logicalTypes(), ctx.acceptedTags()
                );
                eventProducer.publishDatasetChanged("UPDATED", dataset, enriched);
            });
        } catch (Exception e) {
            log.warn("Failed to publish semantic update for dataset {}: {}", datasetId, e.getMessage());
        }
    }

    private DatasetSemanticTagResponse toTagResponse(DatasetSemanticTagEntity t) {
        return new DatasetSemanticTagResponse(
            t.getId(), t.getDatasetId(), t.getSemanticType(), t.getVocabularyIri(), t.getCreatedAt()
        );
    }

    private ElementClassificationInput toClassificationInput(LogicalDataElementEntity e) {
        List<String> iris = e.getVocabMappings() == null ? List.of()
            : e.getVocabMappings().stream().map(VocabMappingEntity::getConceptIri).filter(Objects::nonNull).toList();
        List<String> labels = e.getVocabMappings() == null ? List.of()
            : e.getVocabMappings().stream().map(VocabMappingEntity::getConceptLabel).filter(Objects::nonNull).toList();
        return new ElementClassificationInput(
            e.getId().toString(), e.getName(), e.getLabel(),
            e.getLogicalType(), e.getDescription(), iris, labels
        );
    }

    private void applyRecommendation(LogicalDataElementEntity entity, ElementClassificationResult result) {
        entity.setRecommendedClassification(result.classification());
        entity.setClassificationReasoning(result.reasoning());
        entity.setClassificationRecommendedAt(OffsetDateTime.now());
    }

    private void applyElementRequest(LogicalDataElementEntity entity, LogicalDataElementRequest req) {
        entity.setName(req.name());
        entity.setLabel(req.label());
        entity.setDescription(req.description());
        entity.setLogicalType(req.logicalType());
        entity.setOrdinal(req.ordinal());
        entity.setRequired(req.isRequired());
        entity.setIdentifier(req.isIdentifier());
        entity.setNullable(req.isNullable());
        if (req.classification() != null) entity.setClassification(req.classification());
    }

    private LogicalModelEntity findModelOrThrow(UUID id) {
        return modelRepository.findById(id)
            .orElseThrow(() -> new NoSuchElementException("LogicalModel not found: " + id));
    }

    private LogicalDataElementEntity findElementOrThrow(UUID id) {
        return elementRepository.findById(id)
            .orElseThrow(() -> new NoSuchElementException("LogicalDataElement not found: " + id));
    }

    private LogicalModelResponse toResponse(LogicalModelEntity m) {
        List<LogicalDataElementResponse> elements = m.getElements() == null ? List.of()
            : m.getElements().stream().map(this::toElementResponse).toList();
        return new LogicalModelResponse(
            m.getId(), m.getDatasetId(), m.getName(), m.getDescription(),
            m.getVersion(), m.getStatus(), elements, m.getCreatedAt(), m.getUpdatedAt()
        );
    }

    LogicalDataElementResponse toElementResponse(LogicalDataElementEntity e) {
        List<VocabMappingResponse> mappings = e.getVocabMappings() == null ? List.of()
            : e.getVocabMappings().stream().map(this::toMappingResponse).toList();
        List<UUID> physicalColumnIds = columnRepository.findByLogicalDataElementId(e.getId())
            .stream().map(CsvwColumnEntity::getId).toList();
        return new LogicalDataElementResponse(
            e.getId(), e.getLogicalModelId(), e.getName(), e.getLabel(),
            e.getDescription(), e.getLogicalType(), e.getOrdinal(),
            e.isRequired(), e.isIdentifier(), e.isNullable(),
            physicalColumnIds, mappings, e.getCreatedAt(), e.getUpdatedAt(),
            e.getClassification(), e.getRecommendedClassification(),
            e.getClassificationReasoning(), e.getClassificationRecommendedAt()
        );
    }

    VocabMappingResponse toMappingResponse(VocabMappingEntity m) {
        return new VocabMappingResponse(
            m.getId(), m.getVocabularyId(), m.getConceptIri(),
            m.getConceptLabel(), m.getConceptDefinition(), m.getMatchType(), m.getCreatedAt()
        );
    }
}
