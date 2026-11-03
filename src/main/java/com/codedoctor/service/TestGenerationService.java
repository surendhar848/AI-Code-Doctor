package com.codedoctor.service;

import com.codedoctor.analyzer.LanguageRegistry;
import com.codedoctor.dto.ChatTurn;
import com.codedoctor.dto.CompilerResult;
import com.codedoctor.dto.SourceFile;
import com.codedoctor.dto.TestGenerationResponse;
import com.codedoctor.util.CodeValidator;
import java.util.List;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;

@Service
public class TestGenerationService {

    private final CodeValidator validator;
    private final LanguageRegistry languages;
    private final OllamaService ollama;
    private final PromptService prompts;

    public TestGenerationService(CodeValidator validator, LanguageRegistry languages, OllamaService ollama, PromptService prompts) {
        this.validator = validator;
        this.languages = languages;
        this.ollama = ollama;
        this.prompts = prompts;
    }

    public TestGenerationResponse generate(String language, List<SourceFile> submitted) {
        List<SourceFile> files = validator.normalize(submitted);
        CompilerResult compile = languages.get(language).check(files);

        String user = prompts.prompt("test-prompt")
                + "\n<code_submission>\n" + prompts.plain(files) + "</code_submission>\n"
                + "\n<compiler_output>\n" + PromptService.neutralize(compile.asText()) + "\n</compiler_output>\n";
        TestGenerationResponse r = ollama.chatJson(
                List.of(ChatTurn.system(prompts.prompt("system")), ChatTurn.user(user)),
                prompts.schema("tests"), TestGenerationResponse.class);

        List<SourceFile> cleaned = r.testFiles().stream()
                .filter(f -> f != null && f.content() != null && !f.content().isBlank())
                .map(f -> new SourceFile(f.name(), CodeValidator.stripFences(f.content())))
                .collect(Collectors.toList());

        String note = "Generated tests are NOT executed or verified here (they need JUnit 5 on the classpath). "
                + "Treat them as a starting point: add them to your project, run them, and review the assertions."
                + (compile.success() ? "" : " Warning: the submitted code does not compile yet, so these tests may not match the intended behaviour.");
        return new TestGenerationResponse(r.summary(), cleaned, r.scenarios(), note);
    }
}
