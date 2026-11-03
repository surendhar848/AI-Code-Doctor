package com.codedoctor.analyzer;

import com.codedoctor.dto.CompilerResult;
import com.codedoctor.dto.SourceFile;
import com.codedoctor.service.JavaCompilerService;
import java.util.List;
import org.springframework.stereotype.Component;

@Component
public class JavaLanguageAnalyzer implements LanguageAnalyzer {

    private final JavaCompilerService compiler;

    public JavaLanguageAnalyzer(JavaCompilerService compiler) {
        this.compiler = compiler;
    }

    @Override
    public String language() {
        return "java";
    }

    @Override
    public CompilerResult check(List<SourceFile> files) {
        return compiler.compile(files);
    }
}
