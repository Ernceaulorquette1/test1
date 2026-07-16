package ai.nutriscan.domain.ai;

import java.util.List;

/** Puerto del asistente nutricional conversacional (Chat IA). */
public interface NutritionAssistant {

    record Turn(String role, String content) {}

    /**
     * Genera la respuesta del asistente dado el contexto del usuario
     * (perfil resumido) y el historial reciente de la conversación.
     */
    String reply(String userContext, List<Turn> history, String userMessage);
}
