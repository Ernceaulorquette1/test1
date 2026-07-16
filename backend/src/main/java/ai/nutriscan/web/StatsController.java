package ai.nutriscan.web;

import ai.nutriscan.application.dto.StatsDtos.*;
import ai.nutriscan.application.service.StatsService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/stats")
@Tag(name = "Estadísticas")
public class StatsController {

    private final StatsService stats;

    public StatsController(StatsService stats) { this.stats = stats; }

    @GetMapping
    @Operation(summary = "Series de calorías/macros y peso (range: week|month|year)")
    public StatsResponse get(@RequestParam(defaultValue = "week") String range) {
        return stats.forRange(range);
    }

    @PostMapping("/weight")
    @Operation(summary = "Registra el peso de hoy")
    public void logWeight(@RequestBody LogWeightRequest req) {
        stats.logWeight(req.weightKg());
    }
}
