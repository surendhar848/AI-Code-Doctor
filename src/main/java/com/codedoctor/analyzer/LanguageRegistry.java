package com.codedoctor.analyzer;

import com.codedoctor.exception.InvalidSourceException;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.stereotype.Component;

@Component
public class LanguageRegistry {

    private final Map<String, LanguageAnalyzer> analyzers;

    public LanguageRegistry(List<LanguageAnalyzer> analyzers) {
        this.analyzers = analyzers.stream().collect(Collectors.toMap(LanguageAnalyzer::language, Function.identity()));
    }

    public LanguageAnalyzer get(String language) {
        String key = language == null || language.isBlank() ? "java" : language.trim().toLowerCase();
        LanguageAnalyzer analyzer = analyzers.get(key);
        if (analyzer == null) {
            throw new InvalidSourceException("Language '" + language + "' is not supported yet. Supported: " + analyzers.keySet());
        }
        return analyzer;
    }

    public Set<String> supported() {
        return analyzers.keySet();
    }
}
