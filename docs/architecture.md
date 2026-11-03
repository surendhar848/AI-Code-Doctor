# Architecture

```
Browser (Thymeleaf + Monaco)
   |  JSON
Controllers  -> CodeReviewController, ChatController, HistoryController, HomeController
   |
Services     -> CodeAnalysisService  (orchestrates a review)
                 |- LanguageRegistry -> LanguageAnalyzer (JavaLanguageAnalyzer -> JavaCompilerService)
                 |- OllamaService + PromptService   (structured JSON output, prompt per mode)
                 |- CodeCorrectionService           (AI fix -> recompile -> retry with compiler feedback)
                 |- HistoryService                  (JPA: code_reviews, code_issues, chat_messages)
              TestGenerationService, ChatService
```

## Review pipeline
1. Validate input (size, file count, file names) - `CodeValidator`.
2. Compile with `javax.tools.JavaCompiler` in a temp directory - real diagnostics.
3. Send code (line-numbered) + compiler output to Ollama with a JSON schema.
4. If the AI returns corrected files, recompile them. If they fail, send the new errors back
   (up to `codedoctor.max-fix-attempts`). The UI shows whether the correction actually compiles.
5. Save the review; the full response is stored as JSON so it re-opens exactly as it was.

If Ollama is down, step 3 is skipped and the compiler result is still returned.

## Adding a language
Implement `LanguageAnalyzer` (e.g. run `python -m py_compile`, `node --check`, `php -l` in a sandbox)
and register it as a Spring `@Component`. Prompts and the UI already take a language id.

## Security notes
- Submitted code is compiled, never executed. Annotation processing is off; the classpath is an empty temp dir.
- Prompt-injection hygiene: code is wrapped in delimiter tags the model is told to treat as data.
- All AI and compiler text is rendered with `textContent` in the UI (no HTML injection).
- Not yet implemented: authentication, rate limiting, per-user history, sandboxed test execution.
  Add them before exposing the app publicly (see README, "Before going public").
