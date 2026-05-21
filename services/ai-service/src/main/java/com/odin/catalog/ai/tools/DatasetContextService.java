package com.odin.catalog.ai.tools;

import dev.langchain4j.model.ollama.OllamaChatModel;
import dev.langchain4j.service.AiServices;
import dev.langchain4j.service.SystemMessage;
import dev.langchain4j.service.UserMessage;
import dev.langchain4j.service.V;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.stereotype.Service;

@Service
public class DatasetContextService {

    private static final Logger log = LoggerFactory.getLogger(DatasetContextService.class);

    interface DatasetContextAgent {
        @SystemMessage("""
            You are a data catalog assistant. Use the available tools to retrieve complete \
            information about the requested dataset and return a concise, factual summary. \
            Always call getDatasetInfo first, then getLogicalElementsWithVocabulary. \
            Do not add commentary — return only the retrieved facts.
            """)
        String gatherContext(@UserMessage @V("prompt") String prompt);
    }

    private final DatasetContextAgent agent;

    public DatasetContextService(OllamaChatModel ollamaChatModel, DatasetContextTool tool) {
        this.agent = AiServices.builder(DatasetContextAgent.class)
            .chatLanguageModel(ollamaChatModel)
            .tools(tool)
            .build();
    }

    /**
     * Runs the tool-chaining agent to retrieve structured dataset context.
     * MDC conversationId must be set by the caller before invoking this method.
     * Returns empty string on any failure so the caller can fall back gracefully.
     */
    public String buildFocusedContext(String datasetId, String userQuery, String conversationId) {
        // Ensure MDC is set for tool-call threads spawned by the LangChain4j agent
        MDC.put("conversationId", conversationId);
        try {
            log.info("step=TOOL_CHAIN_INVOKE datasetId={} agent=DatasetContextAgent", datasetId);
            String result = agent.gatherContext(
                "Retrieve information for dataset ID: " + datasetId +
                ". The user is asking: " + userQuery
            );
            log.info("step=TOOL_CHAIN_COMPLETE datasetId={} result.length={}", datasetId, result.length());
            return result;
        } catch (Exception e) {
            log.warn("step=TOOL_CHAIN_ERROR datasetId={} error={}", datasetId, e.getMessage());
            return "";
        } finally {
            MDC.remove("conversationId");
        }
    }
}
