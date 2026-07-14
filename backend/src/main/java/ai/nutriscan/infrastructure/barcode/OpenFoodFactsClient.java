package ai.nutriscan.infrastructure.barcode;

import ai.nutriscan.application.dto.MiscDtos.BarcodeProductResponse;
import ai.nutriscan.application.service.NotFoundException;
import com.fasterxml.jackson.databind.JsonNode;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.math.BigDecimal;

/**
 * Consulta de productos por código de barras contra Open Food Facts.
 * Los resultados se cachean en Redis (los productos cambian rara vez).
 */
@Component
public class OpenFoodFactsClient {

    private final RestClient client = RestClient.builder()
            .baseUrl("https://world.openfoodfacts.org")
            .build();

    @Cacheable(cacheNames = "barcodes", key = "#barcode")
    public BarcodeProductResponse lookup(String barcode) {
        JsonNode root = client.get()
                .uri("/api/v2/product/{code}.json", barcode)
                .retrieve()
                .body(JsonNode.class);

        if (root == null || root.path("status").asInt(0) != 1) {
            throw new NotFoundException("Producto no encontrado para el código " + barcode);
        }
        JsonNode p = root.path("product");
        JsonNode n = p.path("nutriments");
        return new BarcodeProductResponse(
                barcode,
                p.path("product_name").asText("Producto sin nombre"),
                p.path("brands").asText(""),
                p.path("image_front_url").asText(null),
                dec(n, "energy-kcal_100g"),
                dec(n, "proteins_100g"),
                dec(n, "carbohydrates_100g"),
                dec(n, "fat_100g"),
                dec(n, "fiber_100g"),
                dec(n, "sodium_100g").multiply(BigDecimal.valueOf(1000)), // g -> mg
                dec(n, "sugars_100g"));
    }

    private BigDecimal dec(JsonNode n, String field) {
        return BigDecimal.valueOf(n.path(field).asDouble(0));
    }
}
