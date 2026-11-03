package com.codedoctor.service;

import com.codedoctor.dto.ChatMessageView;
import com.codedoctor.dto.ChatRequest;
import com.codedoctor.dto.ChatResponse;
import com.codedoctor.dto.ChatTurn;
import com.codedoctor.dto.CodeReviewResponse;
import com.codedoctor.model.ChatMessage;
import com.codedoctor.repository.ChatMessageRepository;
import java.util.ArrayList;
import java.util.List;
import org.springframework.stereotype.Service;

/** Follow-up questions about a saved review. The review's code and compiler output are the context. */
@Service
public class ChatService {

    private static final int HISTORY_TURNS = 10;

    private final OllamaService ollama;
    private final PromptService prompts;
    private final HistoryService history;
    private final ChatMessageRepository messages;

    public ChatService(OllamaService ollama, PromptService prompts, HistoryService history, ChatMessageRepository messages) {
        this.ollama = ollama;
        this.prompts = prompts;
        this.history = history;
        this.messages = messages;
    }

    public ChatResponse ask(ChatRequest req) {
        String system = prompts.prompt("chat-prompt");
        List<ChatTurn> turns = new ArrayList<>();

        if (req.reviewId() != null) {
            CodeReviewResponse r = history.get(req.reviewId());
            system += "\n\n<code_submission>\n" + prompts.plain(r.originalFiles()) + "</code_submission>\n"
                    + "<compiler_output>\n" + PromptService.neutralize(r.originalCompilation().asText()) + "\n</compiler_output>\n"
                    + "<previous_review_summary>\n" + PromptService.neutralize(r.ai().summary()) + "\n</previous_review_summary>\n";
            List<ChatMessage> prior = messages.findByReviewIdOrderByCreatedAtAscIdAsc(req.reviewId());
            int from = Math.max(0, prior.size() - HISTORY_TURNS);
            for (ChatMessage m : prior.subList(from, prior.size())) {
                turns.add(new ChatTurn(m.getRole().toLowerCase(), m.getMessage()));
            }
        }
        turns.add(0, ChatTurn.system(system));
        turns.add(ChatTurn.user(req.message()));

        String reply = ollama.chat(turns, null);

        if (req.reviewId() != null) {
            messages.save(new ChatMessage(req.reviewId(), "USER", req.message()));
            messages.save(new ChatMessage(req.reviewId(), "ASSISTANT", reply));
        }
        return new ChatResponse(reply);
    }

    public List<ChatMessageView> historyFor(Long reviewId) {
        return messages.findByReviewIdOrderByCreatedAtAscIdAsc(reviewId).stream()
                .map(m -> new ChatMessageView(m.getRole().toLowerCase(), m.getMessage()))
                .toList();
    }
}
