package com.codedoctor.util;

import com.codedoctor.config.AppProperties;
import com.codedoctor.dto.SourceFile;
import com.codedoctor.exception.InvalidSourceException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.springframework.stereotype.Component;

/** Validates and normalises everything that enters the analysis pipeline. */
@Component
public class CodeValidator {

    private static final Pattern FILE_NAME = Pattern.compile("[A-Za-z_$][A-Za-z0-9_$]*\\.java");
    private static final Pattern PUBLIC_TYPE = Pattern.compile(
            "public\\s+(?:(?:final|abstract|sealed|non-sealed|strictfp)\\s+)*(?:class|interface|enum|record)\\s+([A-Za-z_$][A-Za-z0-9_$]*)");
    private static final Pattern FENCE = Pattern.compile("^\\s*```[a-zA-Z]*\\s*\\n|\\n\\s*```\\s*$");

    private final AppProperties props;

    public CodeValidator(AppProperties props) {
        this.props = props;
    }

    /** Checks size/count/names and fills in missing file names. Returns a clean copy. */
    public List<SourceFile> normalize(List<SourceFile> files) {
        if (files == null || files.isEmpty()) {
            throw new InvalidSourceException("Submit at least one source file.");
        }
        if (files.size() > props.maxFiles()) {
            throw new InvalidSourceException("Too many files. The limit is " + props.maxFiles() + ".");
        }
        List<SourceFile> out = new ArrayList<>();
        Set<String> seen = new HashSet<>();
        int total = 0;
        for (SourceFile f : files) {
            String content = f == null || f.content() == null ? "" : f.content();
            if (content.isBlank()) {
                throw new InvalidSourceException("A submitted file is empty.");
            }
            if (content.indexOf('\0') >= 0) {
                throw new InvalidSourceException("Binary content is not accepted. Submit plain-text .java files.");
            }
            total += content.length();
            if (total > props.maxSourceChars()) {
                throw new InvalidSourceException("Source is too large. The limit is " + props.maxSourceChars() + " characters in total.");
            }
            String name = f.name() == null ? "" : f.name().trim();
            if (files.size() == 1) {
                // A single pasted snippet: the public type decides the file name, otherwise keep a safe default.
                name = inferFileName(content, name);
            }
            if (!FILE_NAME.matcher(name).matches()) {
                throw new InvalidSourceException("Invalid file name '" + name + "'. Use names like Main.java.");
            }
            if (!seen.add(name)) {
                throw new InvalidSourceException("Duplicate file name: " + name);
            }
            out.add(new SourceFile(name, content));
        }
        return out;
    }

    public String inferFileName(String content, String fallback) {
        Matcher m = PUBLIC_TYPE.matcher(content);
        if (m.find()) {
            return m.group(1) + ".java";
        }
        return FILE_NAME.matcher(fallback == null ? "" : fallback).matches() ? fallback : "Main.java";
    }

    /** Merges files returned by the model over the originals (unchanged files are kept). */
    public List<SourceFile> mergeCorrected(List<SourceFile> originals, List<SourceFile> corrected) {
        Map<String, SourceFile> merged = new LinkedHashMap<>();
        originals.forEach(o -> merged.put(o.name(), o));
        for (SourceFile c : corrected) {
            if (c == null || c.content() == null || c.content().isBlank()) {
                continue;
            }
            String name = c.name() == null ? "" : c.name().trim();
            if (!merged.containsKey(name)) {
                if (originals.size() == 1 && corrected.size() == 1) {
                    name = originals.get(0).name();
                } else if (!FILE_NAME.matcher(name).matches()) {
                    continue;
                }
            }
            merged.put(name, new SourceFile(name, stripFences(c.content())));
        }
        return new ArrayList<>(merged.values());
    }

    public static String stripFences(String text) {
        return FENCE.matcher(text == null ? "" : text).replaceAll("");
    }
}
