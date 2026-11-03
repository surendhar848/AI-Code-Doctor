package com.codedoctor.service;

import com.codedoctor.analyzer.LanguageAnalyzer;
import com.codedoctor.analyzer.LanguageRegistry;
import com.codedoctor.config.AppProperties;
import com.codedoctor.dto.AiReview;
import com.codedoctor.dto.ChatTurn;
import com.codedoctor.dto.CompilerResult;
import com.codedoctor.dto.SourceFile;
import com.codedoctor.exception.OllamaException;
import com.codedoctor.util.CodeValidator;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * Closes the loop: original -> AI correction -> RECOMPILE -> (if still broken) feed the new compiler
 * errors back to the model and try again, up to {@code codedoctor.max-fix-attempts} candidates.
 */
@Service
public class CodeCorrectionService {

    private static final Logger log = LoggerFactory.getLogger(CodeCorrectionService.class);

    public record FixResult(List<SourceFile> files, CompilerResult compilation, boolean verified, int attempts) {}

    private final LanguageRegistry languages;
    private final OllamaService ollama;
    private final PromptService prompts;
    private final CodeValidator validator;
    private final AppProperties props;

    public CodeCorrectionService(LanguageRegistry languages, OllamaService ollama, PromptService prompts,
                                 CodeValidator validator, AppProperties props) {
        this.languages = languages;
        this.ollama = ollama;
        this.prompts = prompts;
        this.validator = validator;
        this.props = props;
    }

    public FixResult fixAndVerify(String language, List<SourceFile> originals, AiReview firstAnswer) {
        LanguageAnalyzer analyzer = languages.get(language);
        List<SourceFile> candidate = validator.mergeCorrected(originals, firstAnswer.correctedFiles());
        CompilerResult result = null;
        int attempts = 0;

        while (attempts < Math.max(1, props.maxFixAttempts())) {
            attempts++;
            result = analyzer.check(candidate);
            if (result.success()) {
                return new FixResult(candidate, result, true, attempts);
            }
            if (attempts >= props.maxFixAttempts()) {
                break;
            }
            log.info("Correction attempt {} still fails to compile; asking the model again", attempts);
            List<SourceFile> next = askForAnotherFix(originals, candidate, result);
            if (next.isEmpty()) {
                break;
            }
            candidate = next;
        }
        return new FixResult(candidate, result, false, attempts);
    }

    private List<SourceFile> askForAnotherFix(List<SourceFile> originals, List<SourceFile> failedCandidate, CompilerResult errors) {
        String user = prompts.prompt("fix-prompt")
                + "\n<code_submission>\n" + prompts.numbered(failedCandidate) + "</code_submission>\n\n"
                + "<compiler_output>\n" + PromptService.neutralize(errors.asText()) + "\n</compiler_output>\n";
        try {
            AiReview retry = ollama.chatJson(
                    List.of(ChatTurn.system(prompts.prompt("system")), ChatTurn.user(user)),
                    prompts.schema("review"), AiReview.class);
            if (retry.correctedFiles().isEmpty()) {
                return List.of();
            }
            return validator.mergeCorrected(failedCandidate, retry.correctedFiles());
        } catch (OllamaException e) {
            log.warn("Could not get another fix: {}", e.getMessage());
            return List.of();
        }
    }
}
