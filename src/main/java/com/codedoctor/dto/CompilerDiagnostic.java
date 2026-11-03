package com.codedoctor.dto;

public record CompilerDiagnostic(String file, long line, long column, String kind, String message, String sourceLine) {}
