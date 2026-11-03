package com.codedoctor.analyzer;

import com.codedoctor.dto.CompilerResult;
import com.codedoctor.dto.SourceFile;
import java.util.List;

/**
 * One implementation per supported language. The rest of the application only talks to this
 * interface, so Python / JavaScript / PHP can be added later without touching the AI or UI layers.
 */
public interface LanguageAnalyzer {

    /** Lower-case language id, e.g. "java". */
    String language();

    /** Runs the language's own tooling (compiler, syntax checker, linter) and returns real diagnostics. */
    CompilerResult check(List<SourceFile> files);
}
