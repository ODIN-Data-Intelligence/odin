package com.odin.catalog.shared.kafka.consumer;

public class KafkaDeserializationException extends RuntimeException {

    public KafkaDeserializationException(String message, Throwable cause) {
        super(message, cause);
    }
}
