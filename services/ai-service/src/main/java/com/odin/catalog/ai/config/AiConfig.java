package com.odin.catalog.ai.config;

import dev.langchain4j.model.ollama.OllamaChatModel;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.ollama.api.OllamaApi;
import org.springframework.ai.ollama.api.OllamaOptions;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

import java.time.Duration;

@Configuration
public class AiConfig {

    private static final Duration OLLAMA_READ_TIMEOUT = Duration.ofMinutes(5);
    private static final Duration OLLAMA_CONNECT_TIMEOUT = Duration.ofSeconds(30);

    @Bean
    public OllamaApi ollamaApi(@Value("${spring.ai.ollama.base-url}") String baseUrl) {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setReadTimeout(OLLAMA_READ_TIMEOUT);
        factory.setConnectTimeout(OLLAMA_CONNECT_TIMEOUT);
        return OllamaApi.builder()
            .baseUrl(baseUrl)
            .restClientBuilder(RestClient.builder().requestFactory(factory))
            .build();
    }

    @Bean
    public ChatClient chatClient(
            OllamaApi ollamaApi,
            @Value("${spring.ai.ollama.chat.model}") String model) {
        org.springframework.ai.ollama.OllamaChatModel chatModel =
            org.springframework.ai.ollama.OllamaChatModel.builder()
                .ollamaApi(ollamaApi)
                .defaultOptions(OllamaOptions.builder().model(model).build())
                .build();
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
            .timeout(Duration.ofMinutes(5))
            .build();
    }
}
