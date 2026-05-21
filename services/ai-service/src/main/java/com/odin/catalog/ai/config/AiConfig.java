package com.odin.catalog.ai.config;

import dev.langchain4j.model.ollama.OllamaChatModel;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Duration;

@Configuration
public class AiConfig {

    @Bean
    public ChatClient chatClient(ChatModel chatModel) {
        return ChatClient.builder(chatModel).build();
    }

    /**
     * LangChain4j Ollama model used exclusively for tool-calling during dataset context gathering.
     * Distinct from the Spring AI chat model that handles streaming responses.
     */
    @Bean
    public OllamaChatModel langchain4jOllamaModel(
            @Value("${spring.ai.ollama.base-url}") String baseUrl,
            @Value("${spring.ai.ollama.chat.model}") String model) {
        return OllamaChatModel.builder()
            .baseUrl(baseUrl)
            .modelName(model)
            .timeout(Duration.ofSeconds(120))
            .build();
    }
}
