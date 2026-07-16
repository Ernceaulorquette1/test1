package ai.nutriscan.application.service;

import ai.nutriscan.domain.model.User;
import ai.nutriscan.domain.repository.UserRepository;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import java.util.UUID;

/** Resuelve el usuario autenticado a partir del JWT del contexto de seguridad. */
@Service
public class CurrentUserService {

    private final UserRepository users;

    public CurrentUserService(UserRepository users) { this.users = users; }

    public User require() {
        var auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || auth.getPrincipal() == null) {
            throw new NotFoundException("Usuario no autenticado");
        }
        UUID id = UUID.fromString(auth.getPrincipal().toString());
        return users.findById(id).orElseThrow(() -> new NotFoundException("Usuario no encontrado"));
    }
}
