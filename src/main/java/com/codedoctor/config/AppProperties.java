package com.codedoctor.config;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "codedoctor")
public record AppProperties(int maxSourceChars, int maxFiles, Duration compileTimeout, int maxFixAttempts) {}
