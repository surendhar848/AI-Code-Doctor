package com.codedoctor.service;

import com.codedoctor.analyzer.LanguageAnalyzer;
import com.codedoctor.analyzer.LanguageRegistry;
import com.codedoctor.dto.AiReview;
import com.codedoctor.dto.AnalysisMode;
import com.codedoctor.dto.ChatTurn;
import com.codedoctor.dto.CodeReviewRequest;
import com.codedoctor.dto.CodeReviewResponse;
import com.codedoctor.dto.CompilerResult;
import com.codedoctor.dto.SourceFile;
import com.codedoctor.exception.OllamaException;
import com.codedoctor.util.CodeValidator;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * Orchestrates one review: validate -> compile -> AI analysis -> verify correction -> save.
 * The compiler result is always produced first and is never replaced by AI guesses.
 */
@Service
public class CodeAnalysisService {

    private static final Logger log = LoggerFactory.getLogger(CodeAnalysisService.class);

    static final String DISCLAIMER = "AI findings are suggestions, not facts. Only the compiler output is verified. "
            + "A corrected version that compiles is not proven to behave correctly - review it and run your tests.";

    private final CodeValidator validator;
    private final LanguageRegistry languages;
    private final OllamaService ollama;
    private final PromptService prompts;
    private final CodeCorrectionService correction;
    private final HistoryService history;

    public CodeAnalysisService(CodeValidator validator, LanguageRegistry languages, OllamaService ollama,
                               PromptService prompts, CodeCorrectionService correction, HistoryService history) {
        this.validator = validator;
        this.languages = languages;
        this.ollama = ollama;
        this.prompts = prompts;
        this.correction = correction;
        this.history = history;
    }

    public CodeReviewResponse analyze(CodeReviewRequest req) {
        String language = req.language() == null || req.language().isBlank() ? "java" : req.language().trim().toLowerCase();
        LanguageAnalyzer analyzer = languages.get(language);
        List<SourceFile> files = validator.normalize(req.files());
        AnalysisMode mode = req.mode() == null ? AnalysisMode.DEBUG : req.mode();

        // 1. Ground truth first: the real compiler.
        CompilerResult original = analyzer.check(files);

        // 2. Ask the model to explain / review, with the compiler output as evidence.
        AiReview ai;
        String aiError = null;
        try {
            String user = prompts.prompt(mode) + "\n" + prompts.analysisMessage(files, original, req.errorText(), req.question());
            ai = ollama.chatJson(
                    List.of(ChatTurn.system(prompts.prompt("system")), ChatTurn.user(user)),
                    prompts.schema("review"), AiReview.class);
        } catch (OllamaException e) {
            // Degrade gracefully: the compiler diagnostics are still valuable on their own.
            log.warn("AI step failed: {}", e.getMessage());
            aiError = e.getMessage();
            ai = AiReview.unavailable(aiError);
        }

        // 3. If the model proposed changes, prove they compile (and retry with compiler feedback if not).
        List<SourceFile> corrected = List.of();
        CompilerResult correctedCompilation = null;
        boolean verified = false;
        int attempts = 0;
        if (!ai.correctedFiles().isEmpty()) {
            CodeCorrectionService.FixResult fix = correction.fixAndVerify(language, files, ai);
            corrected = fix.files();
            correctedCompilation = fix.compilation();
            verified = fix.verified();
            attempts = fix.attempts();
        }

        String title = req.title() != null && !req.title().isBlank() ? req.title().trim() : files.get(0).name() + " review";
        CodeReviewResponse response = new CodeReviewResponse(null, title, language, mode, files, original, ai, aiError,
                corrected, correctedCompilation, verified, attempts, DISCLAIMER);

        if (req.save() == null || req.save()) {
            try {
                response = history.save(response);
            } catch (RuntimeException e) {
                log.error("Review could not be saved; returning it unsaved", e);
            }
        }
        return response;
    }
}
