package ai.nutriscan.application.service;

import ai.nutriscan.application.dto.MiscDtos.ChatHistoryEntry;
import ai.nutriscan.domain.ai.NutritionAssistant;
import ai.nutriscan.domain.model.ChatMessage;
import ai.nutriscan.domain.model.ChatRole;
import ai.nutriscan.domain.model.User;
import ai.nutriscan.domain.repository.ChatMessageRepository;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

@Service
public class ChatService {

    private static final int CONTEXT_TURNS = 10;

    private final ChatMessageRepository messages;
    private final CurrentUserService currentUser;
    private final NutritionAssistant assistant;

    public ChatService(ChatMessageRepository messages, CurrentUserService currentUser,
                       NutritionAssistant assistant) {
        this.messages = messages;
        this.currentUser = currentUser;
        this.assistant = assistant;
    }

    @Transactional
    public String send(String message) {
        User user = currentUser.require();

        var recent = messages.findByUserIdOrderByCreatedAtDesc(user.getId(), PageRequest.of(0, CONTEXT_TURNS));
        List<NutritionAssistant.Turn> history = new ArrayList<>(recent.stream()
                .sorted(Comparator.comparing(ChatMessage::getCreatedAt))
                .map(m -> new NutritionAssistant.Turn(m.getRole().name().toLowerCase(), m.getContent()))
                .toList());

        String reply = assistant.reply(userContext(user), history, message);

        messages.save(ChatMessage.of(user, ChatRole.USER, message));
        messages.save(ChatMessage.of(user, ChatRole.ASSISTANT, reply));
        return reply;
    }

    @Transactional(readOnly = true)
    public List<ChatHistoryEntry> history(int limit) {
        User user = currentUser.require();
        return messages.findByUserIdOrderByCreatedAtDesc(user.getId(), PageRequest.of(0, limit)).stream()
                .sorted(Comparator.comparing(ChatMessage::getCreatedAt))
                .map(m -> new ChatHistoryEntry(m.getRole().name(), m.getContent(), m.getCreatedAt()))
                .toList();
    }

    private String userContext(User u) {
        StringBuilder sb = new StringBuilder("Perfil del usuario: ");
        if (u.getName() != null) sb.append("nombre ").append(u.getName()).append(", ");
        if (u.getAge() != null) sb.append(u.getAge()).append(" años, ");
        if (u.getSex() != null) sb.append("sexo ").append(u.getSex()).append(", ");
        if (u.getWeightKg() != null) sb.append(u.getWeightKg()).append(" kg, ");
        if (u.getHeightCm() != null) sb.append(u.getHeightCm()).append(" cm, ");
        if (u.getGoal() != null) sb.append("objetivo ").append(u.getGoal()).append(", ");
        if (u.getTargetCalories() != null) sb.append("meta ").append(u.getTargetCalories()).append(" kcal/día");
        return sb.toString();
    }
}
