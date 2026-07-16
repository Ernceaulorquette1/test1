package ai.nutriscan.infrastructure.firebase;

/** Puerto de verificación de identidad. Permite testear sin Firebase real. */
public interface FirebaseTokenVerifier {

    record Identity(String uid, String email, String name, String photoUrl) {}

    /**
     * Verifica un Firebase ID token y devuelve la identidad del usuario.
     * @throws org.springframework.security.authentication.BadCredentialsException si es inválido
     */
    Identity verify(String idToken);
}
