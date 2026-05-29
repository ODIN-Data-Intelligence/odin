package com.odin.catalog.lineage.infrastructure.kafka;

import com.odin.catalog.lineage.infrastructure.jpa.entity.LineageRunEntity;
import com.odin.catalog.shared.kafka.producer.KafkaEventPublisher;
import com.odin.catalog.shared.kafka.topics.CatalogTopics;
import com.odin.catalog.shared.models.events.LineageGraphUpdatedPayload;
import com.odin.catalog.shared.models.openlineage.RunEvent;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.stream.Stream;

@Component
@RequiredArgsConstructor
public class LineageEventProducer {

    private static final Logger log = LoggerFactory.getLogger(LineageEventProducer.class);

    private final KafkaEventPublisher publisher;

    public void publishRunEventReceived(RunEvent event, LineageRunEntity run) {
        publisher.publishAsync(
            CatalogTopics.LINEAGE_RUN_EVENTS,
            run.getRunId(),
            "LineageRunEventReceived",
            null,
            event
        );
        log.info("action=EVENT_PUBLISHED topic={} eventType=LineageRunEventReceived runId={} jobEventType={}",
            CatalogTopics.LINEAGE_RUN_EVENTS, run.getRunId(), event.eventType());
    }

    public void publishGraphUpdated(RunEvent event) {
        List<LineageGraphUpdatedPayload.DatasetRef> inputs = event.inputs() == null ? List.of()
            : event.inputs().stream()
                .map(i -> new LineageGraphUpdatedPayload.DatasetRef(i.namespace(), i.name(), null))
                .toList();

        List<LineageGraphUpdatedPayload.DatasetRef> outputs = event.outputs() == null ? List.of()
            : event.outputs().stream()
                .map(o -> new LineageGraphUpdatedPayload.DatasetRef(o.namespace(), o.name(), null))
                .toList();

        var payload = new LineageGraphUpdatedPayload(
            event.run().runId(),
            event.job().namespace(),
            event.job().name(),
            event.eventType(),
            inputs, outputs,
            OffsetDateTime.now().toString()
        );

        publisher.publishAsync(
            CatalogTopics.LINEAGE_GRAPH_UPDATED,
            event.job().namespace() + ":" + event.job().name(),
            "LineageGraphUpdated",
            null,
            payload
        );
        log.info("action=EVENT_PUBLISHED topic={} eventType=LineageGraphUpdated runId={} inputs={} outputs={}",
            CatalogTopics.LINEAGE_GRAPH_UPDATED, event.run().runId(),
            inputs.size(), outputs.size());
    }
}
