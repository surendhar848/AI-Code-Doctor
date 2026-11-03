package com.codedoctor.dto;

public record CodeIssue(String type, String severity, Integer line, String description, String suggestion) {}
