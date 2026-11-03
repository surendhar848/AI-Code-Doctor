package com.codedoctor.exception;

public class ReviewNotFoundException extends RuntimeException {
    public ReviewNotFoundException(Long id) { super("Review " + id + " does not exist."); }
}
