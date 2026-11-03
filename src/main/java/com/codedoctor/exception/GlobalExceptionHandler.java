package com.codedoctor.exception;

import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/** Every API error is returned as RFC 7807 JSON; the UI shows the "detail" field. */
@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @ExceptionHandler(InvalidSourceException.class)
    ProblemDetail invalid(InvalidSourceException e) {
        return problem(HttpStatus.BAD_REQUEST, "Invalid submission", e.getMessage());
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    ProblemDetail validation(MethodArgumentNotValidException e) {
        String detail = e.getBindingResult().getFieldErrors().stream()
                .map(f -> f.getField() + ": " + f.getDefaultMessage())
                .collect(Collectors.joining("; "));
        return problem(HttpStatus.BAD_REQUEST, "Invalid request", detail);
    }

    @ExceptionHandler(ReviewNotFoundException.class)
    ProblemDetail notFound(ReviewNotFoundException e) {
        return problem(HttpStatus.NOT_FOUND, "Review not found", e.getMessage());
    }

    @ExceptionHandler(OllamaException.class)
    ProblemDetail ollama(OllamaException e) {
        log.warn("Ollama problem: {}", e.getMessage());
        return problem(HttpStatus.BAD_GATEWAY, "AI service problem", e.getMessage());
    }

    @ExceptionHandler(CompilerUnavailableException.class)
    ProblemDetail compiler(CompilerUnavailableException e) {
        log.error("Compiler problem", e);
        return problem(HttpStatus.INTERNAL_SERVER_ERROR, "Compiler problem", e.getMessage());
    }

    @ExceptionHandler(Exception.class)
    ProblemDetail other(Exception e) {
        log.error("Unexpected error", e);
        return problem(HttpStatus.INTERNAL_SERVER_ERROR, "Unexpected error",
                "Something went wrong on the server. Check the application log for details.");
    }

    private ProblemDetail problem(HttpStatus status, String title, String detail) {
        ProblemDetail pd = ProblemDetail.forStatusAndDetail(status, detail);
        pd.setTitle(title);
        return pd;
    }
}
