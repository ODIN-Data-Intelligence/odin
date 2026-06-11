package com.odin.catalog.inventory.application.vocabulary;

import com.odin.catalog.inventory.api.v1.dto.IriTranslateResponse;
import com.odin.catalog.inventory.api.v1.dto.VocabularyRequest;
import com.odin.catalog.inventory.api.v1.dto.VocabularyResponse;
import com.odin.catalog.inventory.infrastructure.jpa.entity.VocabularyEntity;
import com.odin.catalog.inventory.infrastructure.jpa.repository.VocabMappingRepository;
import com.odin.catalog.inventory.infrastructure.jpa.repository.VocabularyRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class VocabularyServiceTest {

    @Mock VocabularyRepository vocabularyRepository;
    @Mock VocabMappingRepository vocabMappingRepository;

    @InjectMocks VocabularyService service;

    // ── list ──────────────────────────────────────────────────────────────

    @Test
    void list_noType_returnsAll() {
        when(vocabularyRepository.findAll()).thenReturn(List.of(vocab("SKOS", true), vocab("Custom", false)));

        List<VocabularyResponse> result = service.list(null);

        assertThat(result).hasSize(2);
        verify(vocabularyRepository).findAll();
        verify(vocabularyRepository, never()).findByVocabularyType(any());
    }

    @Test
    void list_withType_filtersType() {
        when(vocabularyRepository.findByVocabularyType("financial")).thenReturn(List.of(vocab("FIBO", true)));

        List<VocabularyResponse> result = service.list("financial");

        assertThat(result).hasSize(1);
        assertThat(result.get(0).name()).isEqualTo("FIBO");
        verify(vocabularyRepository).findByVocabularyType("financial");
    }

    // ── get ───────────────────────────────────────────────────────────────

    @Test
    void get_found_returnsVocabularyResponse() {
        VocabularyEntity v = vocab("schema.org", true);
        when(vocabularyRepository.findById(v.getId())).thenReturn(Optional.of(v));

        VocabularyResponse result = service.get(v.getId());

        assertThat(result.id()).isEqualTo(v.getId());
        assertThat(result.name()).isEqualTo("schema.org");
        assertThat(result.isSystem()).isTrue();
    }

    @Test
    void get_notFound_throwsNotFound() {
        UUID id = UUID.randomUUID();
        when(vocabularyRepository.findById(id)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.get(id))
            .isInstanceOf(ResponseStatusException.class)
            .extracting(e -> ((ResponseStatusException) e).getStatusCode())
            .isEqualTo(HttpStatus.NOT_FOUND);
    }

    // ── create ────────────────────────────────────────────────────────────

    @Test
    void create_validRequest_savesWithSystemFalse() {
        VocabularyRequest req = new VocabularyRequest(
            "My Vocab", "mv", "https://example.com/mv#",
            "custom", "A custom vocab", null, "1.0", null);

        VocabularyEntity saved = vocab("My Vocab", false);
        saved.setPrefix("mv");
        when(vocabularyRepository.save(any())).thenReturn(saved);

        VocabularyResponse result = service.create(req);

        ArgumentCaptor<VocabularyEntity> captor = ArgumentCaptor.forClass(VocabularyEntity.class);
        verify(vocabularyRepository).save(captor.capture());
        assertThat(captor.getValue().isSystem()).isFalse();
        assertThat(captor.getValue().getName()).isEqualTo("My Vocab");
        assertThat(captor.getValue().getPrefix()).isEqualTo("mv");
        assertThat(result.name()).isEqualTo("My Vocab");
    }

    // ── update ────────────────────────────────────────────────────────────

    @Test
    void update_systemVocab_throwsConflict() {
        VocabularyEntity system = vocab("schema.org", true);
        when(vocabularyRepository.findById(system.getId())).thenReturn(Optional.of(system));

        assertThatThrownBy(() -> service.update(system.getId(),
            new VocabularyRequest("new", "new", "http://new.com#", null, null, null, null, null)))
            .isInstanceOf(ResponseStatusException.class)
            .extracting(e -> ((ResponseStatusException) e).getStatusCode())
            .isEqualTo(HttpStatus.CONFLICT);
    }

    @Test
    void update_userVocab_appliesChangesAndReturns() {
        VocabularyEntity custom = vocab("Old Name", false);
        when(vocabularyRepository.findById(custom.getId())).thenReturn(Optional.of(custom));
        when(vocabularyRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        VocabularyRequest req = new VocabularyRequest(
            "New Name", "nn", "https://example.com/nn#",
            "custom", "updated", "hints", "2.0", "https://example.com");

        VocabularyResponse result = service.update(custom.getId(), req);

        assertThat(result.name()).isEqualTo("New Name");
        assertThat(custom.getVersion()).isEqualTo("2.0");
    }

    @Test
    void update_notFound_throwsNotFound() {
        UUID id = UUID.randomUUID();
        when(vocabularyRepository.findById(id)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.update(id,
            new VocabularyRequest("n", "n", "http://n.com#", null, null, null, null, null)))
            .isInstanceOf(ResponseStatusException.class)
            .extracting(e -> ((ResponseStatusException) e).getStatusCode())
            .isEqualTo(HttpStatus.NOT_FOUND);
    }

    // ── delete ────────────────────────────────────────────────────────────

    @Test
    void delete_systemVocab_throwsConflict() {
        VocabularyEntity system = vocab("FIBO", true);
        when(vocabularyRepository.findById(system.getId())).thenReturn(Optional.of(system));

        assertThatThrownBy(() -> service.delete(system.getId()))
            .isInstanceOf(ResponseStatusException.class)
            .extracting(e -> ((ResponseStatusException) e).getStatusCode())
            .isEqualTo(HttpStatus.CONFLICT);
    }

    @Test
    void delete_userVocab_deletesFromRepository() {
        VocabularyEntity custom = vocab("My Vocab", false);
        when(vocabularyRepository.findById(custom.getId())).thenReturn(Optional.of(custom));

        service.delete(custom.getId());

        verify(vocabularyRepository).delete(custom);
    }

    @Test
    void delete_notFound_throwsNotFound() {
        UUID id = UUID.randomUUID();
        when(vocabularyRepository.findById(id)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.delete(id))
            .isInstanceOf(ResponseStatusException.class)
            .extracting(e -> ((ResponseStatusException) e).getStatusCode())
            .isEqualTo(HttpStatus.NOT_FOUND);
    }

    // ── translate ─────────────────────────────────────────────────────────

    @Test
    void translate_storedLabel_returnsStoredLabel() {
        String iri = "https://schema.org/Person";
        when(vocabMappingRepository.findLabelByConceptIri(iri)).thenReturn(Optional.of("Person"));

        IriTranslateResponse result = service.translate(iri);

        assertThat(result.iri()).isEqualTo(iri);
        assertThat(result.label()).isEqualTo("Person");
    }

    @Test
    void translate_noStoredLabel_humanizesIriFragment() {
        String iri = "https://schema.org/LoanOrCredit";
        when(vocabMappingRepository.findLabelByConceptIri(iri)).thenReturn(Optional.empty());

        IriTranslateResponse result = service.translate(iri);

        assertThat(result.iri()).isEqualTo(iri);
        assertThat(result.label()).isEqualTo("Loan Or Credit");
    }

    // ── translateBatch ────────────────────────────────────────────────────

    @Test
    void translateBatch_nullList_returnsEmpty() {
        assertThat(service.translateBatch(null)).isEmpty();
    }

    @Test
    void translateBatch_emptyList_returnsEmpty() {
        assertThat(service.translateBatch(List.of())).isEmpty();
    }

    @Test
    void translateBatch_severalIris_returnsAllTranslations() {
        String iri1 = "https://schema.org/Person";
        String iri2 = "https://schema.org/LoanOrCredit";
        when(vocabMappingRepository.findLabelByConceptIri(iri1)).thenReturn(Optional.of("Person"));
        when(vocabMappingRepository.findLabelByConceptIri(iri2)).thenReturn(Optional.empty());

        Map<String, String> result = service.translateBatch(List.of(iri1, iri2));

        assertThat(result).containsEntry(iri1, "Person");
        assertThat(result).containsEntry(iri2, "Loan Or Credit");
    }

    @Test
    void translateBatch_over200Iris_limitsTo200() {
        List<String> iris = java.util.stream.IntStream.range(0, 250)
            .mapToObj(i -> "https://example.com/concept/" + i)
            .toList();
        when(vocabMappingRepository.findLabelByConceptIri(any())).thenReturn(Optional.empty());

        Map<String, String> result = service.translateBatch(iris);

        assertThat(result).hasSize(200);
    }

    // ── helpers ───────────────────────────────────────────────────────────

    static VocabularyEntity vocab(String name, boolean isSystem) {
        VocabularyEntity e = new VocabularyEntity();
        e.setId(UUID.randomUUID());
        e.setName(name);
        e.setPrefix(name.toLowerCase().replaceAll("[^a-z0-9]", ""));
        e.setBaseIri("https://example.com/" + name + "#");
        e.setSystem(isSystem);
        e.setCreatedAt(OffsetDateTime.now());
        return e;
    }
}
