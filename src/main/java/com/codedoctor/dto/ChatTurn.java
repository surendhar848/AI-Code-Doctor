package com.codedoctor.dto;

public record ChatTurn(String role, String content) {
    public static ChatTurn system(String c) { return new ChatTurn("system", c); }
    public static ChatTurn user(String c) { return new ChatTurn("user", c); }
    public static ChatTurn assistant(String c) { return new ChatTurn("assistant", c); }
}
