package com.odin.catalog.inventory.infrastructure.ai;

public class AiServiceUnavailableException extends RuntimeException {
    public AiServiceUnavailableException(String message, Throwable cause) {
        super(message, cause);
    }
}
