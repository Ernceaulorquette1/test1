package ai.nutriscan.application.service;

/** Se lanza cuando un usuario gratuito supera su cuota diaria de análisis IA. */
public class QuotaExceededException extends RuntimeException {
    public QuotaExceededException(String message) { super(message); }
}
