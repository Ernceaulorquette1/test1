package ai.nutriscan.infrastructure.ai;

import ai.nutriscan.domain.ai.FoodAnalysis;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class JsonAnalysisParserTest {

    private static final String VALID = """
            {"dishName":"Pollo a la plancha con arroz","portion":"1 plato grande",
             "confidence":0.92,
             "total":{"calories":650,"proteinG":45,"carbsG":65,"fatG":18,"fiberG":6,"sodiumMg":480,"sugarG":4},
             "items":[{"name":"Pechuga de pollo","quantity":"150 g",
                       "nutrition":{"calories":248,"proteinG":46,"carbsG":0,"fatG":5,"fiberG":0,"sodiumMg":110,"sugarG":0}}]}
            """;

    @Test
    void parseaRespuestaValida() {
        FoodAnalysis a = JsonAnalysisParser.parse(VALID);
        assertThat(a.dishName()).isEqualTo("Pollo a la plancha con arroz");
        assertThat(a.total().calories()).isEqualByComparingTo(BigDecimal.valueOf(650));
        assertThat(a.items()).hasSize(1);
        assertThat(a.items().get(0).name()).isEqualTo("Pechuga de pollo");
    }

    @Test
    void toleraBloqueMarkdown() {
        FoodAnalysis a = JsonAnalysisParser.parse("```json\n" + VALID + "\n```");
        assertThat(a.dishName()).isEqualTo("Pollo a la plancha con arroz");
    }

    @Test
    void fallaConJsonInvalido() {
        assertThatThrownBy(() -> JsonAnalysisParser.parse("no soy json"))
                .isInstanceOf(IllegalStateException.class);
    }
}
