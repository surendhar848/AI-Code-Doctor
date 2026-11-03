package com.codedoctor.controller;

import com.codedoctor.dto.ChatMessageView;
import com.codedoctor.dto.ChatRequest;
import com.codedoctor.dto.ChatResponse;
import com.codedoctor.service.ChatService;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/chat")
public class ChatController {

    private final ChatService chat;

    public ChatController(ChatService chat) {
        this.chat = chat;
    }

    @PostMapping
    public ChatResponse ask(@Valid @RequestBody ChatRequest request) {
        return chat.ask(request);
    }

    @GetMapping("/{reviewId}")
    public List<ChatMessageView> history(@PathVariable Long reviewId) {
        return chat.historyFor(reviewId);
    }
}
