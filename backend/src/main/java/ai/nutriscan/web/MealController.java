package ai.nutriscan.web;

import ai.nutriscan.application.dto.MealDtos.*;
import ai.nutriscan.application.service.MealService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/meals")
@Tag(name = "Comidas", description = "Análisis IA de fotos, registro e historial")
public class MealController {

    private final MealService meals;

    public MealController(MealService meals) { this.meals = meals; }

    @PostMapping(value = "/analyze", consumes = "multipart/form-data")
    @Operation(summary = "Analiza la foto de una comida con IA (calorías y macros)")
    public AnalyzeResponse analyze(@RequestParam("image") MultipartFile image) throws IOException {
        String mime = image.getContentType() == null ? "image/jpeg" : image.getContentType();
        return meals.analyze(image.getBytes(), mime);
    }

    @PostMapping
    @Operation(summary = "Guarda una comida (desayuno/almuerzo/cena/snack) en el historial")
    public MealResponse save(@Valid @RequestBody SaveMealRequest req) {
        return meals.save(req);
    }

    @GetMapping("/history")
    @Operation(summary = "Historial de un día, con totales del día")
    public DayHistoryResponse history(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date) {
        return meals.dayHistory(date != null ? date : LocalDate.now(ZoneOffset.UTC), ZoneOffset.UTC);
    }

    @DeleteMapping("/{id}")
    public void delete(@PathVariable UUID id) { meals.delete(id); }
}
