package ai.nutriscan.web;

import ai.nutriscan.application.dto.MiscDtos.*;
import ai.nutriscan.application.service.WaterService;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/water")
@Tag(name = "Agua")
public class WaterController {

    private final WaterService water;

    public WaterController(WaterService water) { this.water = water; }

    @GetMapping("/today")
    public WaterDayResponse today() { return water.today(); }

    @PostMapping
    public WaterDayResponse add(@Valid @RequestBody AddWaterRequest req) {
        return water.add(req.ml());
    }
}
