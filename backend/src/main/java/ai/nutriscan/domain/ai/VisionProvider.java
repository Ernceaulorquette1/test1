package ai.nutriscan.domain.ai;

/**
 * Puerto de visión artificial (Clean Architecture).
 * Implementaciones: OpenAI Vision, Gemini Vision. El proveedor activo se
 * selecciona con la propiedad {@code nutriscan.ai.provider}, lo que permite
 * reemplazar el modelo sin tocar la lógica de negocio.
 */
public interface VisionProvider {

    /**
     * Analiza la fotografía de una comida.
     *
     * @param imageBytes bytes de la imagen (JPEG/PNG)
     * @param mimeType   tipo MIME de la imagen
     * @return análisis con alimentos detectados, porciones y nutrición estimada
     */
    FoodAnalysis analyze(byte[] imageBytes, String mimeType);

    /** Identificador del proveedor ("openai", "gemini"). */
    String id();
}
