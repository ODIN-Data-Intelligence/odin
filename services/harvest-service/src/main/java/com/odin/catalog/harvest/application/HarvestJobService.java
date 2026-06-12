package com.odin.catalog.harvest.application;

import com.odin.catalog.harvest.api.v1.dto.HarvestJobRequest;
import com.odin.catalog.harvest.api.v1.dto.HarvestJobResponse;
import com.odin.catalog.harvest.api.v1.dto.HarvestRunResponse;
import com.odin.catalog.harvest.batch.HarvestJobLauncher;
import com.odin.catalog.harvest.domain.run.HarvestRun;
import com.odin.catalog.harvest.infrastructure.jpa.entity.HarvestJobEntity;
import com.odin.catalog.harvest.infrastructure.jpa.entity.HarvestRunEntity;
import com.odin.catalog.harvest.infrastructure.jpa.repository.HarvestJobRepository;
import com.odin.catalog.harvest.infrastructure.jpa.repository.HarvestRunRepository;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class HarvestJobService {

    private static final Logger log = LoggerFactory.getLogger(HarvestJobService.class);

    private final HarvestJobRepository jobRepository;
    private final HarvestRunRepository runRepository;
    private final HarvestSourceService sourceService;
    private final HarvestJobLauncher launcher;

    @Transactional(readOnly = true)
    public List<HarvestJobResponse> list(UUID sourceId) {
        List<HarvestJobEntity> jobs = sourceId != null
            ? jobRepository.findBySourceId(sourceId)
            : jobRepository.findAll();
        return jobs.stream().map(this::toResponse).toList();
    }

    @Transactional(readOnly = true)
    public HarvestJobResponse get(UUID id) {
        return jobRepository.findById(id)
            .map(this::toResponse)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Harvest job not found: " + id));
    }

    @Transactional
    public HarvestJobResponse create(HarvestJobRequest request) {
        log.info("action=CREATE_JOB sourceId={} name={}", request.sourceId(), request.name());
        HarvestJobEntity job = new HarvestJobEntity();
        job.setSourceId(request.sourceId());
        job.setName(request.name());
        job.setScheduleCron(request.scheduleCron());
        job.setFullRefresh(request.fullRefresh());
        job.setEnabled(request.enabled());
        return toResponse(jobRepository.save(job));
    }

    @Transactional
    public HarvestJobResponse update(UUID id, HarvestJobRequest request) {
        log.info("action=UPDATE_JOB id={}", id);
        HarvestJobEntity job = jobRepository.findById(id)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Harvest job not found: " + id));
        job.setName(request.name());
        job.setScheduleCron(request.scheduleCron());
        job.setFullRefresh(request.fullRefresh());
        job.setEnabled(request.enabled());
        return toResponse(jobRepository.save(job));
    }

    @Transactional
    public void delete(UUID id) {
        log.info("action=DELETE_JOB id={}", id);
        if (!jobRepository.existsById(id)) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Harvest job not found: " + id);
        }
        jobRepository.deleteById(id);
    }

    @Transactional
    public HarvestRunResponse trigger(UUID id) {
        log.info("action=TRIGGER_JOB jobId={}", id);
        HarvestJobEntity job = jobRepository.findById(id)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Harvest job not found: " + id));
        HarvestRunEntity run = new HarvestRunEntity();
        run.setJobId(id);
        run.setSourceId(job.getSourceId());
        run.setStatus("pending");
        run.setTriggeredBy("api");
        run.setFullRefresh(job.isFullRefresh());
        HarvestRunEntity saved = runRepository.save(run);

        HarvestRun domainRun = new HarvestRun(
            saved.getId(), saved.getJobId(), saved.getSourceId(),
            saved.getStatus(), saved.getTriggeredBy(), saved.getStartedAt(), saved.isFullRefresh()
        );
        launcher.launch(domainRun, sourceService.toSource(sourceService.findOrThrow(job.getSourceId())));

        return toRunResponse(saved);
    }

    private HarvestJobResponse toResponse(HarvestJobEntity e) {
        return new HarvestJobResponse(
            e.getId(), e.getSourceId(), e.getName(), e.getScheduleCron(),
            e.isFullRefresh(), e.isEnabled(), e.getCreatedAt(), e.getUpdatedAt());
    }

    private HarvestRunResponse toRunResponse(HarvestRunEntity e) {
        return new HarvestRunResponse(
            e.getId(), e.getJobId(), e.getSourceId(), e.getStatus(), e.getTriggeredBy(),
            e.getStartedAt(), e.getCompletedAt(),
            e.getEntitiesDiscovered(), e.getEntitiesCreated(), e.getEntitiesUpdated(), e.getEntitiesFailed(),
            e.getErrorMessage(), e.isFullRefresh(), e.getCreatedAt());
    }
}
