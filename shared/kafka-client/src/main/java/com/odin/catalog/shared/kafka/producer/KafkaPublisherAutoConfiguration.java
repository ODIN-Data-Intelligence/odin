package com.odin.catalog.shared.kafka.producer;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.kafka.core.KafkaTemplate;

@AutoConfiguration
public class KafkaPublisherAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean
    public KafkaEventPublisher kafkaEventPublisher(
            KafkaTemplate<String, Object> kafkaTemplate,
            @Value("${spring.application.name:unknown-service}") String appName) {
        return new KafkaEventPublisher(kafkaTemplate, appName, "1.0");
    }
}
