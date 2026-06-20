package com.odin.catalog.harvest.application;

import com.odin.catalog.harvest.api.v1.dto.HarvestJobRequest;
import com.odin.catalog.harvest.api.v1.dto.HarvestJobResponse;
import com.odin.catalog.harvest.api.v1.dto.HarvestRunResponse;
import com.odin.catalog.harvest.batch.HarvestJobLauncher;
import com.odin.catalog.harvest.infrastructure.jpa.entity.HarvestJobEntity;
import com.odin.catalog.harvest.infrastructure.jpa.entity.HarvestRunEntity;
import com.odin.catalog.harvest.infrastructure.jpa.repository.HarvestJobRepository;
import com.odin.catalog.harvest.infrastructure.jpa.repository.HarvestRunRepository;
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
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class HarvestJobServiceTest {

    static final UUID SOURCE = UUID.fromString("00000000-0000-0000-0000-000000000010");

    @Mock HarvestJobRepository jobRepository;
    @Mock HarvestRunRepository runRepository;
    @Mock HarvestSourceService sourceService;
    @Mock HarvestJobLauncher launcher;

    @InjectMocks HarvestJobService service;

    // ── list ──────────────────────────────────────────────────────────────

    @Test
    void list_noSourceId_returnsAll() {
        when(jobRepository.findAll()).thenReturn(List.of(job(SOURCE, "Daily"), job(SOURCE, "Weekly")));

        List<HarvestJobResponse> result = service.list(null);

        assertThat(result).hasSize(2);
        verify(jobRepository).findAll();
        verify(jobRepository, never()).findBySourceId(any());
    }

    @Test
    void list_withSourceId_filtersBySource() {
        when(jobRepository.findBySourceId(SOURCE)).thenReturn(List.of(job(SOURCE, "Daily")));

        List<HarvestJobResponse> result = service.list(SOURCE);

        assertThat(result).hasSize(1);
        assertThat(result.get(0).name()).isEqualTo("Daily");
        verify(jobRepository).findBySourceId(SOURCE);
        verify(jobRepository, never()).findAll();
    }

    // ── get ───────────────────────────────────────────────────────────────

    @Test
    void get_found_returnsMappedResponse() {
        HarvestJobEntity j = job(SOURCE, "My Job");
        when(jobRepository.findById(j.getId())).thenReturn(Optional.of(j));

        HarvestJobResponse result = service.get(j.getId());

        assertThat(result.id()).isEqualTo(j.getId());
        assertThat(result.name()).isEqualTo("My Job");
        assertThat(result.sourceId()).isEqualTo(SOURCE);
    }

    @Test
    void get_notFound_throwsNotFound() {
        UUID id = UUID.randomUUID();
        when(jobRepository.findById(id)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.get(id))
            .isInstanceOf(ResponseStatusException.class)
            .extracting(e -> ((ResponseStatusException) e).getStatusCode())
            .isEqualTo(HttpStatus.NOT_FOUND);
    }

    // ── create ────────────────────────────────────────────────────────────

    @Test
    void create_validRequest_savesAllFields() {
        HarvestJobRequest req = new HarvestJobRequest(SOURCE, "Nightly Sync", "0 2 * * *", true, true);
        HarvestJobEntity saved = job(SOURCE, "Nightly Sync");
        saved.setScheduleCron("0 2 * * *");
        saved.setFullRefresh(true);
        when(jobRepository.save(any())).thenReturn(saved);

        HarvestJobResponse result = service.create(req);

        ArgumentCaptor<HarvestJobEntity> captor = ArgumentCaptor.forClass(HarvestJobEntity.class);
        verify(jobRepository).save(captor.capture());
        HarvestJobEntity entity = captor.getValue();
        assertThat(entity.getSourceId()).isEqualTo(SOURCE);
        assertThat(entity.getName()).isEqualTo("Nightly Sync");
        assertThat(entity.getScheduleCron()).isEqualTo("0 2 * * *");
        assertThat(entity.isFullRefresh()).isTrue();
        assertThat(entity.isEnabled()).isTrue();
        assertThat(result.name()).isEqualTo("Nightly Sync");
    }

    // ── update ────────────────────────────────────────────────────────────

    @Test
    void update_found_appliesChanges() {
        HarvestJobEntity existing = job(SOURCE, "Old Name");
        when(jobRepository.findById(existing.getId())).thenReturn(Optional.of(existing));
        when(jobRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        HarvestJobRequest req = new HarvestJobRequest(SOURCE, "New Name", "0 6 * * *", false, false);

        HarvestJobResponse result = service.update(existing.getId(), req);

        assertThat(result.name()).isEqualTo("New Name");
        assertThat(existing.getScheduleCron()).isEqualTo("0 6 * * *");
        assertThat(existing.isFullRefresh()).isFalse();
        assertThat(existing.isEnabled()).isFalse();
    }

    @Test
    void update_notFound_throwsNotFound() {
        UUID id = UUID.randomUUID();
        when(jobRepository.findById(id)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.update(id,
            new HarvestJobRequest(SOURCE, "n", null, false, true)))
            .isInstanceOf(ResponseStatusException.class)
            .extracting(e -> ((ResponseStatusException) e).getStatusCode())
            .isEqualTo(HttpStatus.NOT_FOUND);
    }

    // ── delete ────────────────────────────────────────────────────────────

    @Test
    void delete_found_deletesById() {
        UUID id = UUID.randomUUID();
        when(jobRepository.existsById(id)).thenReturn(true);

        service.delete(id);

        verify(jobRepository).deleteById(id);
    }

    @Test
    void delete_notFound_throwsNotFound() {
        UUID id = UUID.randomUUID();
        when(jobRepository.existsById(id)).thenReturn(false);

        assertThatThrownBy(() -> service.delete(id))
            .isInstanceOf(ResponseStatusException.class)
            .extracting(e -> ((ResponseStatusException) e).getStatusCode())
            .isEqualTo(HttpStatus.NOT_FOUND);
        verify(jobRepository, never()).deleteById(any());
    }

    // ── trigger ───────────────────────────────────────────────────────────

    @Test
    void trigger_found_createsRunWithCorrectFields() {
        HarvestJobEntity j = job(SOURCE, "Daily");
        j.setFullRefresh(true);
        when(jobRepository.findById(j.getId())).thenReturn(Optional.of(j));

        HarvestRunEntity savedRun = run(j.getId(), SOURCE);
        when(runRepository.saveAndFlush(any())).thenReturn(savedRun);

        HarvestRunResponse result = service.trigger(j.getId());

        ArgumentCaptor<HarvestRunEntity> captor = ArgumentCaptor.forClass(HarvestRunEntity.class);
        verify(runRepository).saveAndFlush(captor.capture());
        HarvestRunEntity entity = captor.getValue();
        assertThat(entity.getJobId()).isEqualTo(j.getId());
        assertThat(entity.getSourceId()).isEqualTo(SOURCE);
        assertThat(entity.getStatus()).isEqualTo("pending");
        assertThat(entity.getTriggeredBy()).isEqualTo("api");
        assertThat(entity.isFullRefresh()).isTrue();
        assertThat(result.jobId()).isEqualTo(j.getId());
    }

    @Test
    void trigger_notFound_throwsNotFound() {
        UUID id = UUID.randomUUID();
        when(jobRepository.findById(id)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.trigger(id))
            .isInstanceOf(ResponseStatusException.class)
            .extracting(e -> ((ResponseStatusException) e).getStatusCode())
            .isEqualTo(HttpStatus.NOT_FOUND);
    }

    // ── helpers ───────────────────────────────────────────────────────────

    static HarvestJobEntity job(UUID sourceId, String name) {
        HarvestJobEntity e = new HarvestJobEntity();
        e.setId(UUID.randomUUID());
        e.setSourceId(sourceId);
        e.setName(name);
        e.setEnabled(true);
        e.setCreatedAt(OffsetDateTime.now());
        e.setUpdatedAt(OffsetDateTime.now());
        return e;
    }

    static HarvestRunEntity run(UUID jobId, UUID sourceId) {
        HarvestRunEntity e = new HarvestRunEntity();
        e.setId(UUID.randomUUID());
        e.setJobId(jobId);
        e.setSourceId(sourceId);
        e.setStatus("pending");
        e.setTriggeredBy("api");
        e.setCreatedAt(OffsetDateTime.now());
        return e;
    }
}
