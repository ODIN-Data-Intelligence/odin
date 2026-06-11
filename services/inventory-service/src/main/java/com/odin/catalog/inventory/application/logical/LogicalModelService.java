package com.odin.catalog.inventory.application.logical;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.odin.catalog.inventory.api.v1.dto.*;
import com.odin.catalog.inventory.infrastructure.ai.AiServiceClient;
import com.odin.catalog.inventory.infrastructure.ai.AiServiceClient.ElementClassificationInput;
import com.odin.catalog.inventory.infrastructure.ai.AiServiceClient.ElementClassificationResult;
import com.odin.catalog.inventory.infrastructure.ai.AiServiceClient.ElementVocabInput;
import com.odin.catalog.inventory.infrastructure.ai.AiServiceClient.VocabConceptRecommendation;
import com.odin.catalog.inventory.infrastructure.ai.AiServiceClient.VocabInfo;
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
    private final VocabularyRepository vocabularyRepository;
    private final AiServiceClient aiServiceClient;
    private final ObjectMapper objectMapper;
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
        LogicalModelResponse result = toResponse(modelRepository.save(entity));
        log.info("action=LOGICAL_MODEL_CREATED modelId={} datasetId={} name={}", result.id(), datasetId, request.name());
        return result;
    }

    @Transactional
    public LogicalModelResponse updateStatus(UUID id, String newStatus) {
        LogicalModelEntity entity = findModelOrThrow(id);
        String previousStatus = entity.getStatus();
        entity.setStatus(newStatus);
        LogicalModelResponse result = toResponse(modelRepository.save(entity));
        log.info("action=LOGICAL_MODEL_STATUS_CHANGED modelId={} datasetId={} from={} to={}",
            id, entity.getDatasetId(), previousStatus, newStatus);
        if ("published".equals(newStatus)) {
            publishSemanticUpdate(entity.getDatasetId());
        }
        return result;
    }

    @Transactional
    public void delete(UUID id) {
        modelRepository.deleteById(id);
        log.info("action=LOGICAL_MODEL_DELETED modelId={}", id);
    }

    @Transactional
    public LogicalDataElementResponse addElement(UUID modelId, LogicalDataElementRequest request) {
        findModelOrThrow(modelId);
        LogicalDataElementEntity entity = new LogicalDataElementEntity();
        entity.setLogicalModelId(modelId);
        applyElementRequest(entity, request);
        LogicalDataElementResponse result = toElementResponse(elementRepository.save(entity));
        log.info("action=ELEMENT_CREATED elementId={} modelId={} name={}", result.id(), modelId, request.name());
        return result;
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
        LogicalDataElementResponse result = toElementResponse(elementRepository.save(entity));
        log.info("action=ELEMENT_UPDATED elementId={} name={}", elementId, request.name());
        return result;
    }

    @Transactional
    public LogicalDataElementResponse bindPhysicalColumn(UUID elementId, UUID physicalColumnId) {
        findElementOrThrow(elementId);
        columnRepository.findById(physicalColumnId)
            .orElseThrow(() -> new NoSuchElementException("CsvwColumn not found: " + physicalColumnId));
        columnRepository.bindLogicalElement(physicalColumnId, elementId);
        log.info("action=COLUMN_BOUND elementId={} physicalColumnId={}", elementId, physicalColumnId);
        return toElementResponse(findElementOrThrow(elementId));
    }

    @Transactional
    public LogicalDataElementResponse unbindPhysicalColumn(UUID elementId) {
        findElementOrThrow(elementId);
        columnRepository.unbindLogicalElement(elementId);
        log.info("action=COLUMN_UNBOUND elementId={}", elementId);
        return toElementResponse(findElementOrThrow(elementId));
    }

    @Transactional
    public void deleteElement(UUID elementId) {
        elementRepository.deleteById(elementId);
        log.info("action=ELEMENT_DELETED elementId={}", elementId);
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
        log.info("action=VOCAB_MAPPING_ADDED mappingId={} elementId={} conceptIri={} matchType={}",
            response.id(), elementId, request.conceptIri(), entity.getMatchType());
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
        log.info("action=VOCAB_MAPPING_DELETED mappingId={}", mappingId);
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
        log.info("action=CLASSIFICATION_RECOMMEND_START elementId={}", elementId);
        TransactionTemplate readTx = new TransactionTemplate(transactionManager);
        readTx.setReadOnly(true);
        ElementClassificationInput input = readTx.execute(
            s -> toClassificationInput(findElementOrThrow(elementId)));

        List<ElementClassificationResult> results = aiServiceClient.classify(List.of(input));

        return new TransactionTemplate(transactionManager).execute(s -> {
            LogicalDataElementEntity entity = findElementOrThrow(elementId);
            results.stream().filter(r -> elementId.toString().equals(r.elementId())).findFirst()
                .ifPresent(r -> {
                    applyRecommendation(entity, r);
                    log.info("action=CLASSIFICATION_RECOMMENDED elementId={} classification={}", elementId, r.classification());
                });
            return toElementResponse(elementRepository.save(entity));
        });
    }

    // No @Transactional here: same split-transaction pattern for bulk recommendation.
    @Async
    public void recommendModelClassifications(UUID modelId, UUID jobId) {
        log.info("action=BULK_CLASSIFICATION_START modelId={} jobId={}", modelId, jobId);
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

            log.info("action=BULK_CLASSIFICATION_COMPLETE modelId={} jobId={} resultCount={}", modelId, jobId, results.size());
            jobRegistry.markCompleted(jobId);
        } catch (Exception e) {
            log.error("action=BULK_CLASSIFICATION_FAILED modelId={} jobId={} error={}", modelId, jobId, e.getMessage(), e);
            jobRegistry.markFailed(jobId, e.getMessage());
        }
    }

    // No @Transactional here: same split-transaction pattern as bulk classification.
    @Async
    public void recommendModelDescriptions(UUID modelId, UUID jobId) {
        log.info("action=BULK_DESCRIPTION_START modelId={} jobId={}", modelId, jobId);
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

            List<AiServiceClient.ElementDescriptionResult> results = aiServiceClient.describeElements(inputs);
            Map<String, AiServiceClient.ElementDescriptionResult> byId = results.stream()
                .collect(Collectors.toMap(AiServiceClient.ElementDescriptionResult::elementId, r -> r));

            new TransactionTemplate(transactionManager).execute(s -> {
                List<UUID> ids = inputs.stream().map(i -> UUID.fromString(i.elementId())).toList();
                List<LogicalDataElementEntity> entities = elementRepository.findAllById(ids);
                entities.forEach(e -> {
                    AiServiceClient.ElementDescriptionResult r = byId.get(e.getId().toString());
                    if (r != null) {
                        e.setRecommendedDescription(r.description());
                        e.setDescriptionReasoning(r.reasoning());
                        e.setDescriptionRecommendedAt(OffsetDateTime.now());
                    }
                });
                elementRepository.saveAll(entities);
                return null;
            });

            log.info("action=BULK_DESCRIPTION_COMPLETE modelId={} jobId={} resultCount={}", modelId, jobId, results.size());
            jobRegistry.markCompleted(jobId);
        } catch (Exception e) {
            log.error("action=BULK_DESCRIPTION_FAILED modelId={} jobId={} error={}", modelId, jobId, e.getMessage(), e);
            jobRegistry.markFailed(jobId, e.getMessage());
        }
    }

    @Transactional
    public LogicalDataElementResponse acceptClassification(UUID elementId) {
        LogicalDataElementEntity entity = findElementOrThrow(elementId);
        if (entity.getRecommendedClassification() == null) {
            throw new NoSuchElementException("No pending recommendation for element: " + elementId);
        }
        String classification = entity.getRecommendedClassification();
        entity.setClassification(classification);
        entity.setRecommendedClassification(null);
        entity.setClassificationReasoning(null);
        entity.setClassificationRecommendedAt(null);
        log.info("action=CLASSIFICATION_ACCEPTED elementId={} classification={}", elementId, classification);
        return toElementResponse(elementRepository.save(entity));
    }

    @Transactional
    public LogicalDataElementResponse rejectClassification(UUID elementId) {
        LogicalDataElementEntity entity = findElementOrThrow(elementId);
        entity.setRecommendedClassification(null);
        entity.setClassificationReasoning(null);
        entity.setClassificationRecommendedAt(null);
        log.info("action=CLASSIFICATION_REJECTED elementId={}", elementId);
        return toElementResponse(elementRepository.save(entity));
    }

    public LogicalDataElementResponse recommendDescription(UUID elementId) {
        log.info("action=DESCRIPTION_RECOMMEND_START elementId={}", elementId);
        TransactionTemplate readTx = new TransactionTemplate(transactionManager);
        readTx.setReadOnly(true);
        AiServiceClient.ElementClassificationInput input = readTx.execute(
            s -> toClassificationInput(findElementOrThrow(elementId)));

        List<AiServiceClient.ElementDescriptionResult> results = aiServiceClient.describeElements(List.of(input));

        return new TransactionTemplate(transactionManager).execute(s -> {
            LogicalDataElementEntity entity = findElementOrThrow(elementId);
            results.stream().filter(r -> elementId.toString().equals(r.elementId())).findFirst()
                .ifPresent(r -> {
                    entity.setRecommendedDescription(r.description());
                    entity.setDescriptionReasoning(r.reasoning());
                    entity.setDescriptionRecommendedAt(OffsetDateTime.now());
                    log.info("action=DESCRIPTION_RECOMMENDED elementId={}", elementId);
                });
            return toElementResponse(elementRepository.save(entity));
        });
    }

    @Transactional
    public LogicalDataElementResponse acceptDescription(UUID elementId) {
        LogicalDataElementEntity entity = findElementOrThrow(elementId);
        if (entity.getRecommendedDescription() == null) {
            throw new NoSuchElementException("No pending description recommendation for element: " + elementId);
        }
        entity.setDescription(entity.getRecommendedDescription());
        entity.setRecommendedDescription(null);
        entity.setDescriptionReasoning(null);
        entity.setDescriptionRecommendedAt(null);
        log.info("action=DESCRIPTION_ACCEPTED elementId={}", elementId);
        return toElementResponse(elementRepository.save(entity));
    }

    @Transactional
    public LogicalDataElementResponse rejectDescription(UUID elementId) {
        LogicalDataElementEntity entity = findElementOrThrow(elementId);
        entity.setRecommendedDescription(null);
        entity.setDescriptionReasoning(null);
        entity.setDescriptionRecommendedAt(null);
        log.info("action=DESCRIPTION_REJECTED elementId={}", elementId);
        return toElementResponse(elementRepository.save(entity));
    }

    // ── Vocabulary concept recommendations ───────────────────────────────────

    public LogicalDataElementResponse recommendVocabConcepts(UUID elementId) {
        log.info("action=VOCAB_RECOMMEND_START elementId={}", elementId);
        TransactionTemplate readTx = new TransactionTemplate(transactionManager);
        readTx.setReadOnly(true);
        ElementVocabInput input = readTx.execute(s -> toVocabInput(findElementOrThrow(elementId)));

        List<VocabConceptRecommendation> results = aiServiceClient.recommendVocabConcepts(List.of(input));

        return new TransactionTemplate(transactionManager).execute(s -> {
            LogicalDataElementEntity entity = findElementOrThrow(elementId);
            results.stream().filter(r -> elementId.toString().equals(r.elementId())).findFirst()
                .ifPresent(r -> applyVocabRecommendation(entity, r));
            return toElementResponse(elementRepository.save(entity));
        });
    }

    @Transactional
    public LogicalDataElementResponse acceptVocabConcepts(UUID elementId, List<String> selectedIris) {
        LogicalDataElementEntity entity = findElementOrThrow(elementId);
        if (entity.getRecommendedVocabMappings() == null) {
            throw new NoSuchElementException("No pending vocabulary recommendation for element: " + elementId);
        }
        List<RecommendedVocabMapping> recommendations = parseVocabRecommendations(entity.getRecommendedVocabMappings());
        Set<String> iriFilter = (selectedIris != null && !selectedIris.isEmpty())
            ? new java.util.HashSet<>(selectedIris) : null;
        List<VocabularyEntity> vocabs = vocabularyRepository.findAll();
        Set<String> existingIris = mappingRepository.findByLogicalElementId(elementId)
            .stream().map(VocabMappingEntity::getConceptIri).collect(Collectors.toSet());

        int accepted = 0;
        for (RecommendedVocabMapping rec : recommendations) {
            if (iriFilter != null && !iriFilter.contains(rec.conceptIri())) continue;
            if (existingIris.contains(rec.conceptIri())) continue;
            VocabMappingEntity mapping = new VocabMappingEntity();
            mapping.setLogicalElementId(elementId);
            mapping.setConceptIri(rec.conceptIri());
            mapping.setConceptLabel(rec.conceptLabel());
            mapping.setConceptDefinition(rec.conceptDefinition());
            mapping.setMatchType(rec.matchType() != null ? rec.matchType() : "exactMatch");
            UUID vocabId = findVocabularyIdForIri(rec.conceptIri(), vocabs);
            if (vocabId == null) {
                log.warn("Skipping vocab mapping — no vocabulary found for IRI prefix: {}", rec.conceptIri());
                continue;
            }
            mapping.setVocabularyId(vocabId);
            mappingRepository.save(mapping);
            accepted++;
        }
        entity.setRecommendedVocabMappings(null);
        entity.setVocabMappingReasoning(null);
        entity.setVocabMappingRecommendedAt(null);
        log.info("action=VOCAB_CONCEPTS_ACCEPTED elementId={} accepted={} of={}", elementId, accepted, recommendations.size());
        return toElementResponse(elementRepository.save(entity));
    }

    @Transactional
    public LogicalDataElementResponse rejectVocabConcepts(UUID elementId) {
        LogicalDataElementEntity entity = findElementOrThrow(elementId);
        entity.setRecommendedVocabMappings(null);
        entity.setVocabMappingReasoning(null);
        entity.setVocabMappingRecommendedAt(null);
        log.info("action=VOCAB_CONCEPTS_REJECTED elementId={}", elementId);
        return toElementResponse(elementRepository.save(entity));
    }

    @Async
    public void recommendModelPii(UUID modelId, UUID jobId) {
        log.info("action=BULK_PII_START modelId={} jobId={}", modelId, jobId);
        try {
            jobRegistry.markRunning(jobId);

            TransactionTemplate readTx = new TransactionTemplate(transactionManager);
            readTx.setReadOnly(true);
            List<AiServiceClient.ElementPiiInput> inputs = readTx.execute(s -> {
                findModelOrThrow(modelId);
                return elementRepository.findByLogicalModelIdOrderByOrdinalAsc(modelId)
                    .stream().map(this::toPiiInput).toList();
            });
            if (inputs == null || inputs.isEmpty()) { jobRegistry.markCompleted(jobId); return; }

            List<AiServiceClient.ElementPiiResult> results = aiServiceClient.recommendPii(inputs);
            Map<String, AiServiceClient.ElementPiiResult> byId = results.stream()
                .collect(Collectors.toMap(AiServiceClient.ElementPiiResult::elementId, r -> r, (a, b) -> a));

            new TransactionTemplate(transactionManager).execute(s -> {
                List<UUID> ids = inputs.stream().map(i -> UUID.fromString(i.elementId())).toList();
                List<LogicalDataElementEntity> entities = elementRepository.findAllById(ids);
                entities.forEach(e -> {
                    AiServiceClient.ElementPiiResult r = byId.get(e.getId().toString());
                    if (r != null) {
                        e.setRecommendedIsPersonalInformation(r.isPersonalInformation());
                        e.setRecommendedIsDirectIdentifier(r.isDirectIdentifier());
                        e.setPiiRecommendationReasoning(r.reasoning());
                        e.setPiiRecommendedAt(OffsetDateTime.now());
                    }
                });
                elementRepository.saveAll(entities);
                return null;
            });

            log.info("action=BULK_PII_COMPLETE modelId={} jobId={} resultCount={}", modelId, jobId, results.size());
            jobRegistry.markCompleted(jobId);
        } catch (Exception e) {
            log.error("action=BULK_PII_FAILED modelId={} jobId={} error={}", modelId, jobId, e.getMessage(), e);
            jobRegistry.markFailed(jobId, e.getMessage());
        }
    }

    @Async
    public void recommendModelVocabConcepts(UUID modelId, UUID jobId) {
        log.info("action=BULK_VOCAB_START modelId={} jobId={}", modelId, jobId);
        try {
            jobRegistry.markRunning(jobId);

            TransactionTemplate readTx = new TransactionTemplate(transactionManager);
            readTx.setReadOnly(true);
            List<ElementVocabInput> inputs = readTx.execute(s -> {
                findModelOrThrow(modelId);
                return elementRepository.findByLogicalModelIdOrderByOrdinalAsc(modelId)
                    .stream().map(this::toVocabInput).toList();
            });
            if (inputs == null || inputs.isEmpty()) { jobRegistry.markCompleted(jobId); return; }

            List<VocabConceptRecommendation> results = aiServiceClient.recommendVocabConcepts(inputs);
            Map<String, VocabConceptRecommendation> byId = results.stream()
                .collect(Collectors.toMap(VocabConceptRecommendation::elementId, r -> r, (a, b) -> a));

            new TransactionTemplate(transactionManager).execute(s -> {
                List<UUID> ids = inputs.stream().map(i -> UUID.fromString(i.elementId())).toList();
                List<LogicalDataElementEntity> entities = elementRepository.findAllById(ids);
                entities.forEach(e -> {
                    VocabConceptRecommendation r = byId.get(e.getId().toString());
                    if (r != null) applyVocabRecommendation(e, r);
                });
                elementRepository.saveAll(entities);
                return null;
            });

            log.info("action=BULK_VOCAB_COMPLETE modelId={} jobId={} resultCount={}", modelId, jobId, results.size());
            jobRegistry.markCompleted(jobId);
        } catch (Exception e) {
            log.error("action=BULK_VOCAB_FAILED modelId={} jobId={} error={}", modelId, jobId, e.getMessage(), e);
            jobRegistry.markFailed(jobId, e.getMessage());
        }
    }

    // ── PII indicator recommendations ────────────────────────────────────────

    public LogicalDataElementResponse recommendPii(UUID elementId) {
        log.info("action=PII_RECOMMEND_START elementId={}", elementId);
        TransactionTemplate readTx = new TransactionTemplate(transactionManager);
        readTx.setReadOnly(true);
        AiServiceClient.ElementPiiInput input = readTx.execute(s -> toPiiInput(findElementOrThrow(elementId)));

        List<AiServiceClient.ElementPiiResult> results = aiServiceClient.recommendPii(List.of(input));

        return new TransactionTemplate(transactionManager).execute(s -> {
            LogicalDataElementEntity entity = findElementOrThrow(elementId);
            results.stream().filter(r -> elementId.toString().equals(r.elementId())).findFirst()
                .ifPresent(r -> {
                    entity.setRecommendedIsPersonalInformation(r.isPersonalInformation());
                    entity.setRecommendedIsDirectIdentifier(r.isDirectIdentifier());
                    entity.setPiiRecommendationReasoning(r.reasoning());
                    entity.setPiiRecommendedAt(OffsetDateTime.now());
                    log.info("action=PII_RECOMMENDED elementId={} personalInfo={} directId={}",
                        elementId, r.isPersonalInformation(), r.isDirectIdentifier());
                });
            return toElementResponse(elementRepository.save(entity));
        });
    }

    @Transactional
    public LogicalDataElementResponse acceptPii(UUID elementId) {
        LogicalDataElementEntity entity = findElementOrThrow(elementId);
        if (entity.getRecommendedIsPersonalInformation() == null && entity.getRecommendedIsDirectIdentifier() == null) {
            throw new NoSuchElementException("No pending PII recommendation for element: " + elementId);
        }
        if (entity.getRecommendedIsPersonalInformation() != null)
            entity.setPersonalInformation(entity.getRecommendedIsPersonalInformation());
        if (entity.getRecommendedIsDirectIdentifier() != null)
            entity.setDirectIdentifier(entity.getRecommendedIsDirectIdentifier());
        entity.setRecommendedIsPersonalInformation(null);
        entity.setRecommendedIsDirectIdentifier(null);
        entity.setPiiRecommendationReasoning(null);
        entity.setPiiRecommendedAt(null);
        log.info("action=PII_ACCEPTED elementId={} personalInfo={} directId={}",
            elementId, entity.isPersonalInformation(), entity.isDirectIdentifier());
        return toElementResponse(elementRepository.save(entity));
    }

    @Transactional
    public LogicalDataElementResponse rejectPii(UUID elementId) {
        LogicalDataElementEntity entity = findElementOrThrow(elementId);
        entity.setRecommendedIsPersonalInformation(null);
        entity.setRecommendedIsDirectIdentifier(null);
        entity.setPiiRecommendationReasoning(null);
        entity.setPiiRecommendedAt(null);
        log.info("action=PII_REJECTED elementId={}", elementId);
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
        log.info("action=SEMANTIC_TAG_ACCEPTED tagId={} datasetId={} type={}", tag.getId(), datasetId, request.type());
        return toTagResponse(tag);
    }

    @Transactional
    public void deleteSemanticTag(UUID datasetId, UUID tagId) {
        semanticTagRepository.findByIdAndDatasetId(tagId, datasetId)
            .orElseThrow(() -> new NoSuchElementException("Semantic tag not found: " + tagId));
        semanticTagRepository.deleteById(tagId);
        publishSemanticUpdate(datasetId);
        log.info("action=SEMANTIC_TAG_DELETED tagId={} datasetId={}", tagId, datasetId);
    }

    @Transactional(readOnly = true)
    public AiServiceClient.SemanticRecommendationResponse recommendSemanticContext(UUID datasetId) {
        log.info("action=SEMANTIC_RECOMMEND_START datasetId={}", datasetId);
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

    private ElementVocabInput toVocabInput(LogicalDataElementEntity e) {
        List<VocabularyEntity> vocabs = vocabularyRepository.findAll();
        List<VocabInfo> vocabInfos = vocabs.stream()
            .map(v -> new VocabInfo(v.getPrefix(), v.getBaseIri(), v.getName(), v.getConceptHints()))
            .toList();
        List<String> existingIris = e.getVocabMappings() == null ? List.of()
            : e.getVocabMappings().stream().map(VocabMappingEntity::getConceptIri).filter(Objects::nonNull).toList();
        List<String> existingLabels = e.getVocabMappings() == null ? List.of()
            : e.getVocabMappings().stream().map(VocabMappingEntity::getConceptLabel).filter(Objects::nonNull).toList();
        return new ElementVocabInput(
            e.getId().toString(), e.getName(), e.getLabel(),
            e.getLogicalType(), e.getDescription(),
            existingIris, existingLabels, vocabInfos
        );
    }

    private void applyVocabRecommendation(LogicalDataElementEntity entity, VocabConceptRecommendation rec) {
        if (rec.concepts() == null || rec.concepts().isEmpty()) return;
        try {
            List<RecommendedVocabMapping> mappings = rec.concepts().stream()
                .map(c -> new RecommendedVocabMapping(
                    c.conceptIri(), c.conceptLabel(), c.conceptDefinition(), c.matchType(), c.reasoning()))
                .toList();
            entity.setRecommendedVocabMappings(objectMapper.writeValueAsString(mappings));
            entity.setVocabMappingReasoning(rec.concepts().get(0).reasoning());
            entity.setVocabMappingRecommendedAt(OffsetDateTime.now());
            log.info("action=VOCAB_RECOMMENDED elementId={} conceptCount={}", entity.getId(), mappings.size());
        } catch (Exception e) {
            log.warn("Failed to serialize vocab recommendations for element {}: {}", entity.getId(), e.getMessage());
        }
    }

    private List<RecommendedVocabMapping> parseVocabRecommendations(String json) {
        if (json == null || json.isBlank()) return List.of();
        try {
            return objectMapper.readValue(json, new TypeReference<List<RecommendedVocabMapping>>() {});
        } catch (Exception e) {
            log.warn("Failed to parse vocab recommendations JSON: {}", e.getMessage());
            return List.of();
        }
    }

    private UUID findVocabularyIdForIri(String iri, List<VocabularyEntity> vocabs) {
        return vocabs.stream()
            .filter(v -> iri != null && v.getBaseIri() != null && iri.startsWith(v.getBaseIri()))
            .findFirst()
            .map(VocabularyEntity::getId)
            .orElse(null);
    }

    private AiServiceClient.ElementPiiInput toPiiInput(LogicalDataElementEntity e) {
        List<String> iris = e.getVocabMappings() == null ? List.of()
            : e.getVocabMappings().stream().map(VocabMappingEntity::getConceptIri).filter(Objects::nonNull).toList();
        List<String> labels = e.getVocabMappings() == null ? List.of()
            : e.getVocabMappings().stream().map(VocabMappingEntity::getConceptLabel).filter(Objects::nonNull).toList();
        // Fetch dataset context for richer PII inference
        LogicalModelEntity model = modelRepository.findById(e.getLogicalModelId()).orElse(null);
        String datasetTitle = null;
        List<String> datasetKeywords = List.of();
        if (model != null) {
            datasetRepository.findById(model.getDatasetId()).ifPresent(ds -> {
                // captured below — need effectively-final workaround via array
            });
            var ds = datasetRepository.findById(model.getDatasetId()).orElse(null);
            if (ds != null) {
                datasetTitle = ds.getTitle();
                datasetKeywords = ds.getKeywords() != null ? ds.getKeywords() : List.of();
            }
        }
        return new AiServiceClient.ElementPiiInput(
            e.getId().toString(), e.getName(), e.getLabel(),
            e.getLogicalType(), e.getDescription(), iris, labels,
            datasetTitle, datasetKeywords
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
        entity.setPersonalInformation(req.isPersonalInformation());
        entity.setDirectIdentifier(req.isDirectIdentifier());
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
        List<VocabMappingResponse> mappings = mappingRepository.findByLogicalElementId(e.getId())
            .stream().map(this::toMappingResponse).toList();
        List<UUID> physicalColumnIds = columnRepository.findByLogicalDataElementId(e.getId())
            .stream().map(CsvwColumnEntity::getId).toList();
        List<RecommendedVocabMapping> recommendedVocab = parseVocabRecommendations(e.getRecommendedVocabMappings());
        return new LogicalDataElementResponse(
            e.getId(), e.getLogicalModelId(), e.getName(), e.getLabel(),
            e.getDescription(), e.getLogicalType(), e.getOrdinal(),
            e.isRequired(), e.isIdentifier(), e.isNullable(),
            physicalColumnIds, mappings, e.getCreatedAt(), e.getUpdatedAt(),
            e.getClassification(), e.getRecommendedClassification(),
            e.getClassificationReasoning(), e.getClassificationRecommendedAt(),
            e.getRecommendedDescription(), e.getDescriptionReasoning(), e.getDescriptionRecommendedAt(),
            recommendedVocab.isEmpty() ? null : recommendedVocab,
            e.getVocabMappingReasoning(), e.getVocabMappingRecommendedAt(),
            e.isPersonalInformation(), e.isDirectIdentifier(),
            e.getRecommendedIsPersonalInformation(), e.getRecommendedIsDirectIdentifier(),
            e.getPiiRecommendationReasoning(), e.getPiiRecommendedAt()
        );
    }

    VocabMappingResponse toMappingResponse(VocabMappingEntity m) {
        return new VocabMappingResponse(
            m.getId(), m.getVocabularyId(), m.getConceptIri(),
            m.getConceptLabel(), m.getConceptDefinition(), m.getMatchType(), m.getCreatedAt()
        );
    }
}
