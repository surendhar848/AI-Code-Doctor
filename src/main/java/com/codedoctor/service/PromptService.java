package com.codedoctor.service;

import com.codedoctor.dto.AnalysisMode;
import com.codedoctor.dto.CompilerResult;
import com.codedoctor.dto.SourceFile;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Service;

/** Loads prompt templates and JSON schemas from the classpath and builds the user messages. */
@Service
public class PromptService {

    private final Map<String, String> cache = new ConcurrentHashMap<>();
    private final ObjectMapper mapper;

    public PromptService(ObjectMapper mapper) {
        this.mapper = mapper;
    }

    public String prompt(String name) {
        return cache.computeIfAbsent("prompt:" + name, k -> read("prompts/" + name + ".txt"));
    }

    public String prompt(AnalysisMode mode) {
        return prompt(mode.promptName());
    }

    public JsonNode schema(String name) {
        try {
            return mapper.readTree(cache.computeIfAbsent("schema:" + name, k -> read("schemas/" + name + ".json")));
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    /** Code with line numbers so the model can cite lines accurately. */
    public String numbered(List<SourceFile> files) {
        StringBuilder sb = new StringBuilder();
        for (SourceFile f : files) {
            sb.append("=== File: ").append(f.name()).append(" ===\n");
            int n = 1;
            for (String line : f.content().lines().toList()) {
                sb.append(n++).append(" | ").append(neutralize(line)).append('\n');
            }
        }
        return sb.toString();
    }

    public String plain(List<SourceFile> files) {
        StringBuilder sb = new StringBuilder();
        for (SourceFile f : files) {
            sb.append("=== File: ").append(f.name()).append(" ===\n").append(neutralize(f.content())).append('\n');
        }
        return sb.toString();
    }

    public String analysisMessage(List<SourceFile> files, CompilerResult compiler, String errorText, String question) {
        StringBuilder sb = new StringBuilder();
        sb.append("<code_submission>\n").append(numbered(files)).append("</code_submission>\n\n");
        sb.append("<compiler_output>\n").append(neutralize(compiler.asText())).append("\n</compiler_output>\n");
        if (errorText != null && !errorText.isBlank()) {
            sb.append("\n<user_supplied_error>\n").append(neutralize(errorText)).append("\n</user_supplied_error>\n");
        }
        if (question != null && !question.isBlank()) {
            sb.append("\n<user_question>\n").append(neutralize(question)).append("\n</user_question>\n");
        }
        return sb.toString();
    }

    /** Stops submitted text from closing our delimiter tags (basic prompt-injection hygiene). */
    public static String neutralize(String s) {
        return s == null ? "" : s.replaceAll("(?i)<(/?)(code_submission|compiler_output|user_supplied_error|user_question)", "<\\\\$1$2");
    }

    private String read(String path) {
        try (InputStream in = new ClassPathResource(path).getInputStream()) {
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new UncheckedIOException("Missing resource: " + path, e);
        }
    }
}
