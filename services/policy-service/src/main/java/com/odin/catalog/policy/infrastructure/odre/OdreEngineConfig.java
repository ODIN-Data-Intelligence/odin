package com.odin.catalog.policy.infrastructure.odre;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class OdreEngineConfig {

    @Bean
    public OdreEngine odreEngine(ObjectMapper objectMapper) {
        return new OdreEngine(objectMapper);
    }
}
