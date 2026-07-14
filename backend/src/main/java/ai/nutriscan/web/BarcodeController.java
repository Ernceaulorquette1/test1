package ai.nutriscan.web;

import ai.nutriscan.application.dto.MiscDtos.BarcodeProductResponse;
import ai.nutriscan.infrastructure.barcode.OpenFoodFactsClient;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/barcode")
@Tag(name = "Código de barras")
public class BarcodeController {

    private final OpenFoodFactsClient foodFacts;

    public BarcodeController(OpenFoodFactsClient foodFacts) { this.foodFacts = foodFacts; }

    @GetMapping("/{barcode}")
    @Operation(summary = "Información nutricional por código de barras (por 100 g)")
    public BarcodeProductResponse lookup(@PathVariable String barcode) {
        return foodFacts.lookup(barcode.replaceAll("[^0-9]", ""));
    }
}
