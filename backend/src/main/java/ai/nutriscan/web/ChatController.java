package ai.nutriscan.web;

import ai.nutriscan.application.dto.MiscDtos.*;
import ai.nutriscan.application.service.ChatService;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/chat")
@Tag(name = "Chat IA", description = "Asistente nutricional conversacional")
public class ChatController {

    private final ChatService chat;

    public ChatController(ChatService chat) { this.chat = chat; }

    @PostMapping
    public ChatResponse send(@Valid @RequestBody ChatRequest req) {
        return new ChatResponse(chat.send(req.message()));
    }

    @GetMapping("/history")
    public List<ChatHistoryEntry> history(@RequestParam(defaultValue = "50") int limit) {
        return chat.history(Math.min(limit, 200));
    }
}
