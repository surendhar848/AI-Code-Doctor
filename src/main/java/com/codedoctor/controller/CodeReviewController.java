package com.codedoctor.controller;

import com.codedoctor.analyzer.LanguageRegistry;
import com.codedoctor.dto.CodeReviewRequest;
import com.codedoctor.dto.CodeReviewResponse;
import com.codedoctor.dto.CompilerResult;
import com.codedoctor.dto.FilesRequest;
import com.codedoctor.dto.TestGenerationResponse;
import com.codedoctor.service.CodeAnalysisService;
import com.codedoctor.service.OllamaService;
import com.codedoctor.service.TestGenerationService;
import com.codedoctor.util.CodeValidator;
import jakarta.validation.Valid;
import java.util.Map;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api")
public class CodeReviewController {

    private final CodeAnalysisService analysis;
    private final TestGenerationService tests;
    private final LanguageRegistry languages;
    private final CodeValidator validator;
    private final OllamaService ollama;

    public CodeReviewController(CodeAnalysisService analysis, TestGenerationService tests, LanguageRegistry languages,
                                CodeValidator validator, OllamaService ollama) {
        this.analysis = analysis;
        this.tests = tests;
        this.languages = languages;
        this.validator = validator;
        this.ollama = ollama;
    }

    /** Full pipeline: compile -> AI -> verified correction -> save. */
    @PostMapping("/review")
    public CodeReviewResponse review(@Valid @RequestBody CodeReviewRequest request) {
        return analysis.analyze(request);
    }

    /** Compiler only (no AI). Fast, and works even when Ollama is down. */
    @PostMapping("/compile")
    public CompilerResult compile(@Valid @RequestBody FilesRequest request) {
        return languages.get(request.language()).check(validator.normalize(request.files()));
    }

    @PostMapping("/tests")
    public TestGenerationResponse tests(@Valid @RequestBody FilesRequest request) {
        return tests.generate(request.language(), request.files());
    }

    @GetMapping("/status")
    public Map<String, Object> status() {
        return Map.of("ollama", ollama.isAvailable(), "model", ollama.model(), "languages", languages.supported());
    }
}
