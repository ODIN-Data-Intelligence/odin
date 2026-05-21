package com.odin.catalog.shared.kafka.consumer;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;

@AutoConfiguration
public class KafkaConsumerAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean
    public KafkaEventConsumer kafkaEventConsumer(ObjectMapper objectMapper) {
        return new KafkaEventConsumer(objectMapper);
    }
}
