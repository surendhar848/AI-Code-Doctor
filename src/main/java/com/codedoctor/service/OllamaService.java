package com.codedoctor.service;

import com.codedoctor.config.OllamaProperties;
import com.codedoctor.dto.ChatTurn;
import com.codedoctor.exception.OllamaException;
import com.codedoctor.util.CodeValidator;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

/** Thin client for Ollama's /api/chat endpoint, with JSON-schema constrained output. */
@Service
public class OllamaService {

    private static final Logger log = LoggerFactory.getLogger(OllamaService.class);

    private final RestClient client;
    private final OllamaProperties props;
    private final ObjectMapper mapper;

    public OllamaService(RestClient ollamaRestClient, OllamaProperties props, ObjectMapper mapper) {
        this.client = ollamaRestClient;
        this.props = props;
        this.mapper = mapper;
    }

    public String model() {
        return props.model();
    }

    /** Plain chat completion; pass a JSON schema as {@code format} for structured output (or null). */
    public String chat(List<ChatTurn> messages, JsonNode format) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("model", props.model());
        body.put("stream", false);
        body.put("messages", messages);
        if (format != null) {
            body.put("format", format);
        }
        body.put("options", Map.of("temperature", props.temperature(), "num_ctx", props.numCtx()));

        long start = System.currentTimeMillis();
        try {
            JsonNode root = client.post().uri("/api/chat")
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(body)
                    .retrieve()
                    .body(JsonNode.class);
            String content = root == null ? "" : root.path("message").path("content").asText("");
            if (content.isBlank()) {
                throw new OllamaException("Ollama returned an empty response.");
            }
            log.info("Ollama answered in {} ms", System.currentTimeMillis() - start);
            return content;
        } catch (HttpClientErrorException.NotFound e) {
            throw new OllamaException("Model '" + props.model() + "' is not installed. Run: ollama pull " + props.model(), e);
        } catch (RestClientException e) {
            throw new OllamaException("Cannot get an answer from Ollama at " + props.baseUrl()
                    + ". Is it running? (" + e.getMessage() + ")", e);
        }
    }

    /** Structured chat: the model must answer with JSON matching {@code schema}; parsed into {@code type}. */
    public <T> T chatJson(List<ChatTurn> messages, JsonNode schema, Class<T> type) {
        String content = chat(messages, schema);
        try {
            return mapper.readValue(CodeValidator.stripFences(content).trim(), type);
        } catch (JsonProcessingException e) {
            log.warn("Invalid JSON from model: {}", content);
            throw new OllamaException("The model returned an answer that is not valid JSON. Try again, or use a larger model.", e);
        }
    }

    public boolean isAvailable() {
        try {
            client.get().uri("/api/tags").retrieve().toBodilessEntity();
            return true;
        } catch (RestClientException e) {
            return false;
        }
    }
}
