package com.codedoctor.exception;

public class CompilerUnavailableException extends RuntimeException {
    public CompilerUnavailableException(String message) { super(message); }
    public CompilerUnavailableException(String message, Throwable cause) { super(message, cause); }
}
