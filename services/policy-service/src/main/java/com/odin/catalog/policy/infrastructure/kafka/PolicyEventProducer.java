package com.odin.catalog.policy.infrastructure.kafka;

import com.odin.catalog.shared.kafka.producer.KafkaEventPublisher;
import com.odin.catalog.shared.kafka.topics.CatalogTopics;
import com.odin.catalog.shared.models.policy.PolicyEvaluationResultPayload;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class PolicyEventProducer {

    private static final Logger log = LoggerFactory.getLogger(PolicyEventProducer.class);

    private final KafkaEventPublisher publisher;

    public void publishEvaluationCompleted(PolicyEvaluationResultPayload payload) {
        try {
            publisher.publishAsync(
                CatalogTopics.POLICY_EVALUATIONS_COMPLETED,
                payload.datasetId(),
                "PolicyEvaluationCompleted",
                payload.tenantId(),
                payload
            );
            log.info("action=EVALUATION_EVENT_PUBLISHED topic={} datasetId={} granted={}",
                CatalogTopics.POLICY_EVALUATIONS_COMPLETED, payload.datasetId(), payload.granted());
        } catch (Exception e) {
            log.error("action=EVALUATION_EVENT_PUBLISH_FAILED topic={} datasetId={} error={}",
                CatalogTopics.POLICY_EVALUATIONS_COMPLETED, payload.datasetId(), e.getMessage(), e);
        }
    }
}
