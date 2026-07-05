package com.odin.catalog.inventory.application.vocabulary;

import com.odin.catalog.inventory.api.v1.dto.IriTranslateResponse;
import com.odin.catalog.inventory.api.v1.dto.VocabularyRequest;
import com.odin.catalog.inventory.api.v1.dto.VocabularyResponse;
import com.odin.catalog.inventory.infrastructure.jpa.entity.VocabularyEntity;
import com.odin.catalog.inventory.infrastructure.jpa.repository.VocabMappingRepository;
import com.odin.catalog.inventory.infrastructure.jpa.repository.VocabularyRepository;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class VocabularyService {

    private static final Logger log = LoggerFactory.getLogger(VocabularyService.class);

    private final VocabularyRepository vocabularyRepository;
    private final VocabMappingRepository vocabMappingRepository;

    @Transactional(readOnly = true)
    public List<VocabularyResponse> list(String type) {
        List<VocabularyEntity> entities = type != null
            ? vocabularyRepository.findByVocabularyType(type)
            : vocabularyRepository.findAll();
        return entities.stream().map(VocabularyResponse::from).toList();
    }

    @Transactional(readOnly = true)
    public VocabularyResponse get(UUID id) {
        return VocabularyResponse.from(findOrThrow(id));
    }

    @Transactional
    public VocabularyResponse create(VocabularyRequest request) {
        VocabularyEntity vocab = new VocabularyEntity();
        applyRequest(vocab, request);
        vocab.setSystem(false);
        VocabularyEntity saved = vocabularyRepository.save(vocab);
        log.info("action=CREATE_VOCABULARY vocabularyId={} name={}", saved.getId(), saved.getName());
        return VocabularyResponse.from(saved);
    }

    @Transactional
    public VocabularyResponse update(UUID id, VocabularyRequest request) {
        VocabularyEntity vocab = findOrThrow(id);
        if (vocab.isSystem()) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                "System vocabularies cannot be modified. Clone to a custom vocabulary first.");
        }
        applyRequest(vocab, request);
        VocabularyEntity saved = vocabularyRepository.save(vocab);
        log.info("action=UPDATE_VOCABULARY vocabularyId={} name={}", saved.getId(), saved.getName());
        return VocabularyResponse.from(saved);
    }

    @Transactional
    public void delete(UUID id) {
        VocabularyEntity vocab = findOrThrow(id);
        if (vocab.isSystem()) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                "System vocabularies cannot be deleted.");
        }
        vocabularyRepository.delete(vocab);
        log.info("action=DELETE_VOCABULARY vocabularyId={} name={}", id, vocab.getName());
    }

    @Transactional(readOnly = true)
    public IriTranslateResponse translate(String iri) {
        String label = vocabMappingRepository.findLabelByConceptIri(iri)
            .orElseGet(() -> IriUtils.humanize(iri));
        return new IriTranslateResponse(iri, label);
    }

    @Transactional(readOnly = true)
    public Map<String, String> translateBatch(List<String> iris) {
        if (iris == null || iris.isEmpty()) return Map.of();
        List<String> limited = iris.size() > 200 ? iris.subList(0, 200) : iris;
        Map<String, String> result = new LinkedHashMap<>();
        for (String iri : limited) {
            result.put(iri, vocabMappingRepository.findLabelByConceptIri(iri)
                .orElseGet(() -> IriUtils.humanize(iri)));
        }
        return result;
    }

    private VocabularyEntity findOrThrow(UUID id) {
        return vocabularyRepository.findById(id)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Vocabulary not found: " + id));
    }

    private void applyRequest(VocabularyEntity e, VocabularyRequest req) {
        e.setName(req.name());
        e.setPrefix(req.prefix());
        e.setBaseIri(req.baseIri());
        e.setVocabularyType(req.vocabularyType());
        e.setDescription(req.description());
        e.setConceptHints(req.conceptHints());
        e.setVersion(req.version());
        e.setHomepage(req.homepage());
    }
}
