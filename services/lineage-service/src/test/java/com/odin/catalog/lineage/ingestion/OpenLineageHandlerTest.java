package com.odin.catalog.lineage.ingestion;

import com.odin.catalog.lineage.infrastructure.age.AgeGraphRepository;
import com.odin.catalog.lineage.infrastructure.jpa.entity.*;
import com.odin.catalog.lineage.infrastructure.jpa.repository.*;
import com.odin.catalog.lineage.infrastructure.kafka.LineageEventProducer;
import com.odin.catalog.shared.models.openlineage.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class OpenLineageHandlerTest {

    @Mock LineageJobRepository jobRepository;
    @Mock LineageDatasetRepository datasetRepository;
    @Mock LineageRunRepository runRepository;
    @Mock LineageRunEventRepository eventRepository;
    @Mock AgeGraphRepository ageGraph;
    @Mock LineageEventProducer eventProducer;

    @InjectMocks OpenLineageHandler handler;

    // ── START: upserts job, run, persists event ───────────────────────────

    @Test
    void handle_startEvent_upsertsJobAndRunAndPersistsEvent() {
        RunEvent event = event("START", "trading", "enrichment_job", List.of(), List.of());

        LineageJobEntity job = job("trading", "enrichment_job");
        LineageRunEntity run = run(job.getId(), event.run().runId());

        when(jobRepository.insertIfAbsent("trading", "enrichment_job")).thenReturn(1);
        when(jobRepository.findByNamespaceAndName("trading", "enrichment_job")).thenReturn(Optional.of(job));
        when(runRepository.findByRunId(event.run().runId())).thenReturn(Optional.empty());
        when(runRepository.save(any())).thenReturn(run);

        handler.handle(event);

        verify(jobRepository).insertIfAbsent("trading", "enrichment_job");
        verify(ageGraph).mergeJobNode("trading", "enrichment_job");
        verify(runRepository).save(any());
        verify(eventRepository).save(any());
        verify(eventProducer).publishRunEventReceived(event, run);
        verify(eventProducer).publishGraphUpdated(event);
    }

    @Test
    void handle_existingJob_doesNotSaveOrMergeAgain() {
        RunEvent event = event("START", "trading", "enrichment_job", List.of(), List.of());
        LineageJobEntity existingJob = job("trading", "enrichment_job");
        LineageRunEntity run = run(existingJob.getId(), event.run().runId());

        when(jobRepository.insertIfAbsent("trading", "enrichment_job")).thenReturn(0);
        when(jobRepository.findByNamespaceAndName("trading", "enrichment_job")).thenReturn(Optional.of(existingJob));
        when(runRepository.findByRunId(event.run().runId())).thenReturn(Optional.empty());
        when(runRepository.save(any())).thenReturn(run);

        handler.handle(event);

        verify(ageGraph, never()).mergeJobNode(any(), any());
    }

    @Test
    void handle_existingRun_doesNotSaveRunAgain() {
        RunEvent event = event("START", "trading", "job", List.of(), List.of());
        LineageJobEntity job = job("trading", "job");
        LineageRunEntity existingRun = run(job.getId(), event.run().runId());

        when(jobRepository.findByNamespaceAndName(any(), any())).thenReturn(Optional.of(job));
        when(runRepository.findByRunId(event.run().runId())).thenReturn(Optional.of(existingRun));

        handler.handle(event);

        verify(runRepository, never()).save(any());
    }

    // ── START with inputs/outputs ─────────────────────────────────────────

    @Test
    void handle_startWithInputsAndOutputs_mergesReadWriteEdges() {
        InputDataset input = new InputDataset("reference", "securities_master", null, null);
        OutputDataset output = new OutputDataset("trading", "executed_trades", null, null);
        RunEvent event = event("START", "trading", "enrichment_job", List.of(input), List.of(output));

        LineageJobEntity job = job("trading", "enrichment_job");
        LineageRunEntity run = run(job.getId(), event.run().runId());

        when(jobRepository.findByNamespaceAndName(any(), any())).thenReturn(Optional.of(job));
        when(runRepository.findByRunId(any())).thenReturn(Optional.empty());
        when(runRepository.save(any())).thenReturn(run);
        when(datasetRepository.findByNamespaceAndName(any(), any())).thenReturn(Optional.of(new LineageDatasetEntity()));

        handler.handle(event);

        verify(ageGraph).mergeDatasetNode("reference", "securities_master");
        verify(ageGraph).mergeReadByEdge("reference", "securities_master", "trading", "enrichment_job");
        verify(ageGraph).mergeDatasetNode("trading", "executed_trades");
        verify(ageGraph).mergeWritesToEdge("trading", "enrichment_job", "trading", "executed_trades");
        // START event — no DERIVED_FROM
        verify(ageGraph, never()).mergeDerivedFromEdge(any(), any(), any(), any());
    }

    // ── COMPLETE: creates DERIVED_FROM edges ──────────────────────────────

    @Test
    void handle_completeEvent_createsDerivedFromForAllInputOutputPairs() {
        InputDataset in1 = new InputDataset("reference", "securities_master", null, null);
        InputDataset in2 = new InputDataset("reference", "fx_rates", null, null);
        OutputDataset out = new OutputDataset("trading", "executed_trades", null, null);
        RunEvent event = event("COMPLETE", "trading", "enrichment_job", List.of(in1, in2), List.of(out));

        LineageJobEntity job = job("trading", "enrichment_job");
        LineageRunEntity run = run(job.getId(), event.run().runId());

        when(jobRepository.findByNamespaceAndName(any(), any())).thenReturn(Optional.of(job));
        when(runRepository.findByRunId(any())).thenReturn(Optional.empty());
        when(runRepository.save(any())).thenReturn(run);
        when(datasetRepository.findByNamespaceAndName(any(), any())).thenReturn(Optional.of(new LineageDatasetEntity()));

        handler.handle(event);

        // 2 inputs × 1 output = 2 DERIVED_FROM edges
        verify(ageGraph).mergeDerivedFromEdge("reference", "securities_master", "trading", "executed_trades");
        verify(ageGraph).mergeDerivedFromEdge("reference", "fx_rates", "trading", "executed_trades");
    }

    @Test
    void handle_completeEvent_nullInputs_noDerivedFromEdges() {
        OutputDataset out = new OutputDataset("trading", "executed_trades", null, null);
        RunEvent event = new RunEvent(
            "COMPLETE", OffsetDateTime.now().toString(),
            new Run(UUID.randomUUID().toString(), null),
            new Job("trading", "job", null),
            null, List.of(out), "producer", "schema"
        );

        LineageJobEntity job = job("trading", "job");
        LineageRunEntity run = run(job.getId(), event.run().runId());

        when(jobRepository.findByNamespaceAndName(any(), any())).thenReturn(Optional.of(job));
        when(runRepository.findByRunId(any())).thenReturn(Optional.empty());
        when(runRepository.save(any())).thenReturn(run);
        when(datasetRepository.findByNamespaceAndName(any(), any())).thenReturn(Optional.of(new LineageDatasetEntity()));

        handler.handle(event);

        verify(ageGraph, never()).mergeDerivedFromEdge(any(), any(), any(), any());
    }

    @Test
    void handle_completeEvent_nullOutputs_noDerivedFromEdges() {
        InputDataset in = new InputDataset("reference", "securities", null, null);
        RunEvent event = new RunEvent(
            "COMPLETE", OffsetDateTime.now().toString(),
            new Run(UUID.randomUUID().toString(), null),
            new Job("trading", "job", null),
            List.of(in), null, "producer", "schema"
        );

        LineageJobEntity job = job("trading", "job");
        LineageRunEntity run = run(job.getId(), event.run().runId());

        when(jobRepository.findByNamespaceAndName(any(), any())).thenReturn(Optional.of(job));
        when(runRepository.findByRunId(any())).thenReturn(Optional.empty());
        when(runRepository.save(any())).thenReturn(run);
        when(datasetRepository.findByNamespaceAndName(any(), any())).thenReturn(Optional.of(new LineageDatasetEntity()));

        handler.handle(event);

        verify(ageGraph, never()).mergeDerivedFromEdge(any(), any(), any(), any());
    }

    // ── new dataset is saved ──────────────────────────────────────────────

    @Test
    void handle_newDataset_savesDatasetEntity() {
        InputDataset input = new InputDataset("reference", "securities", null, null);
        RunEvent event = event("START", "trading", "job", List.of(input), List.of());

        LineageJobEntity job = job("trading", "job");
        LineageRunEntity run = run(job.getId(), event.run().runId());

        when(jobRepository.findByNamespaceAndName(any(), any())).thenReturn(Optional.of(job));
        when(runRepository.findByRunId(any())).thenReturn(Optional.empty());
        when(runRepository.save(any())).thenReturn(run);
        when(datasetRepository.findByNamespaceAndName("reference", "securities"))
            .thenReturn(Optional.of(new LineageDatasetEntity()));

        handler.handle(event);

        verify(datasetRepository).insertIfAbsent("reference", "securities");
    }

    // ── invalid event time falls back to now ──────────────────────────────

    @Test
    void handle_invalidEventTime_fallsBackToNow() {
        RunEvent event = new RunEvent(
            "START", "not-a-valid-datetime",
            new Run(UUID.randomUUID().toString(), null),
            new Job("ns", "job", null),
            List.of(), List.of(), "producer", "schema-url"
        );

        LineageJobEntity job = job("ns", "job");
        LineageRunEntity run = run(job.getId(), event.run().runId());

        when(jobRepository.findByNamespaceAndName(any(), any())).thenReturn(Optional.of(job));
        when(runRepository.findByRunId(any())).thenReturn(Optional.empty());
        when(runRepository.save(any())).thenReturn(run);

        handler.handle(event);

        verify(eventRepository).save(argThat(e -> e.getEventTime() != null));
    }

    // ── fixtures ──────────────────────────────────────────────────────────

    private RunEvent event(String type, String jobNs, String jobName,
                            List<InputDataset> inputs, List<OutputDataset> outputs) {
        return new RunEvent(
            type, OffsetDateTime.now().toString(),
            new Run(UUID.randomUUID().toString(), null),
            new Job(jobNs, jobName, null),
            inputs, outputs, "test-producer", "https://openlineage.io/spec"
        );
    }

    private LineageJobEntity job(String namespace, String name) {
        LineageJobEntity j = new LineageJobEntity();
        j.setId(UUID.randomUUID());
        j.setNamespace(namespace);
        j.setName(name);
        return j;
    }

    private LineageRunEntity run(UUID jobId, String runId) {
        LineageRunEntity r = new LineageRunEntity();
        r.setId(UUID.randomUUID());
        r.setJobId(jobId);
        r.setRunId(runId);
        return r;
    }
}
