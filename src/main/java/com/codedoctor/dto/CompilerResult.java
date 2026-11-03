package com.codedoctor.dto;

import java.util.List;

public record CompilerResult(boolean success, boolean timedOut, long durationMs, List<CompilerDiagnostic> diagnostics) {

    /** Plain-text form used inside prompts and reports. */
    public String asText() {
        if (diagnostics.isEmpty()) {
            return success ? "Compilation successful. No errors or warnings." : "Compilation failed without diagnostics.";
        }
        StringBuilder sb = new StringBuilder();
        for (CompilerDiagnostic d : diagnostics) {
            sb.append(d.file()).append(':').append(d.line()).append(": ")
              .append(d.kind().toLowerCase()).append(": ").append(d.message()).append('\n');
            if (d.sourceLine() != null && !d.sourceLine().isBlank()) {
                sb.append("    ").append(d.sourceLine().strip()).append('\n');
            }
        }
        return sb.toString();
    }
}
