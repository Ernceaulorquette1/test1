package ai.nutriscan.web;

import ai.nutriscan.application.dto.ProfileDtos.*;
import ai.nutriscan.application.service.ProfileService;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/profile")
@Tag(name = "Perfil")
public class ProfileController {

    private final ProfileService profiles;

    public ProfileController(ProfileService profiles) { this.profiles = profiles; }

    @GetMapping
    public ProfileResponse get() { return profiles.get(); }

    @PutMapping
    public ProfileResponse update(@Valid @RequestBody UpdateProfileRequest req) {
        return profiles.update(req);
    }
}
