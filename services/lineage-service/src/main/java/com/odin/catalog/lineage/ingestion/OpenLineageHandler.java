package com.odin.catalog.lineage.ingestion;

import com.odin.catalog.lineage.infrastructure.age.AgeGraphRepository;
import com.odin.catalog.lineage.infrastructure.jpa.entity.*;
import com.odin.catalog.lineage.infrastructure.jpa.repository.*;
import com.odin.catalog.lineage.infrastructure.kafka.LineageEventProducer;
import com.odin.catalog.shared.models.openlineage.*;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.List;

@Service
@RequiredArgsConstructor
public class OpenLineageHandler {

    private static final Logger log = LoggerFactory.getLogger(OpenLineageHandler.class);

    private final LineageJobRepository jobRepository;
    private final LineageDatasetRepository datasetRepository;
    private final LineageRunRepository runRepository;
    private final LineageRunEventRepository eventRepository;
    private final AgeGraphRepository ageGraph;
    private final LineageEventProducer eventProducer;

    @Transactional
    public void handle(RunEvent event) {
        log.debug("Processing RunEvent type={} job={}/{} run={}",
            event.eventType(), event.job().namespace(), event.job().name(), event.run().runId());

        // Upsert job
        LineageJobEntity job = upsertJob(event.job());

        // Upsert run
        LineageRunEntity run = upsertRun(event.run(), job.getId(), event);

        // Persist the run event
        LineageRunEventEntity runEvent = new LineageRunEventEntity();
        runEvent.setRunId(run.getId());
        runEvent.setEventType(event.eventType());
        runEvent.setEventTime(parseTime(event.eventTime()));
        runEvent.setProducer(event.producer());
        runEvent.setSchemaUrl(event.schemaURL());
        eventRepository.save(runEvent);

        // Process input datasets
        if (event.inputs() != null) {
            for (InputDataset input : event.inputs()) {
                LineageDatasetEntity ds = upsertDataset(input.namespace(), input.name());
                ageGraph.mergeDatasetNode(input.namespace(), input.name());
                ageGraph.mergeReadByEdge(input.namespace(), input.name(),
                    event.job().namespace(), event.job().name());
            }
        }

        // Process output datasets
        if (event.outputs() != null) {
            for (OutputDataset output : event.outputs()) {
                LineageDatasetEntity ds = upsertDataset(output.namespace(), output.name());
                ageGraph.mergeDatasetNode(output.namespace(), output.name());
                ageGraph.mergeWritesToEdge(event.job().namespace(), event.job().name(),
                    output.namespace(), output.name());
            }
        }

        // On COMPLETE: create DERIVED_FROM edges between all input/output pairs
        if ("COMPLETE".equalsIgnoreCase(event.eventType())
                && event.inputs() != null && event.outputs() != null) {
            for (InputDataset input : event.inputs()) {
                for (OutputDataset output : event.outputs()) {
                    ageGraph.mergeDerivedFromEdge(
                        input.namespace(), input.name(),
                        output.namespace(), output.name()
                    );
                }
            }
        }

        // Notify downstream services
        eventProducer.publishRunEventReceived(event, run);
        eventProducer.publishGraphUpdated(event);
    }

    private LineageJobEntity upsertJob(Job job) {
        return jobRepository.findByNamespaceAndName(job.namespace(), job.name())
            .orElseGet(() -> {
                LineageJobEntity entity = new LineageJobEntity();
                entity.setNamespace(job.namespace());
                entity.setName(job.name());
                ageGraph.mergeJobNode(job.namespace(), job.name());
                return jobRepository.save(entity);
            });
    }

    private LineageRunEntity upsertRun(Run run, java.util.UUID jobId, RunEvent event) {
        return runRepository.findByRunId(run.runId())
            .orElseGet(() -> {
                LineageRunEntity entity = new LineageRunEntity();
                entity.setRunId(run.runId());
                entity.setJobId(jobId);
                return runRepository.save(entity);
            });
    }

    private LineageDatasetEntity upsertDataset(String namespace, String name) {
        return datasetRepository.findByNamespaceAndName(namespace, name)
            .orElseGet(() -> {
                LineageDatasetEntity entity = new LineageDatasetEntity();
                entity.setNamespace(namespace);
                entity.setName(name);
                return datasetRepository.save(entity);
            });
    }

    private OffsetDateTime parseTime(String iso) {
        try {
            return OffsetDateTime.parse(iso);
        } catch (Exception e) {
            return OffsetDateTime.now();
        }
    }
}
