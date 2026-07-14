package ai.nutriscan.infrastructure.ai;

import ai.nutriscan.domain.ai.VisionProvider;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;

/**
 * Selecciona el proveedor de visión activo según {@code nutriscan.ai.provider}.
 * Cambiar de OpenAI a Gemini (o a un modelo propio) no requiere tocar código
 * de negocio: basta con registrar otra implementación de VisionProvider.
 */
@Configuration
public class AiProviderConfig {

    @Bean
    @Primary
    public VisionProvider activeVisionProvider(
            @Value("${nutriscan.ai.provider}") String provider,
            @Qualifier("openaiVision") VisionProvider openai,
            @Qualifier("geminiVision") VisionProvider gemini) {
        return switch (provider.toLowerCase()) {
            case "gemini" -> gemini;
            case "openai" -> openai;
            default -> throw new IllegalArgumentException("Proveedor IA desconocido: " + provider);
        };
    }
}
