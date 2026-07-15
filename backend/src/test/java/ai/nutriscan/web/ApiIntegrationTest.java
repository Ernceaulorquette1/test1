package ai.nutriscan.web;

import ai.nutriscan.infrastructure.firebase.FirebaseTokenVerifier;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Tests de integración de la API: contexto Spring completo con H2,
 * seguridad JWT real y Firebase/facturación simulados.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class ApiIntegrationTest {

    @Autowired MockMvc mvc;
    @Autowired ObjectMapper mapper;
    @MockBean FirebaseTokenVerifier firebaseVerifier;

    private String login() throws Exception {
        String uid = "uid-" + UUID.randomUUID();
        when(firebaseVerifier.verify(anyString())).thenReturn(
                new FirebaseTokenVerifier.Identity(uid, uid + "@test.nutriscan.ai", "Tester", null));
        var res = mvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"firebaseIdToken\":\"fake-token\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accessToken").isNotEmpty())
                .andExpect(jsonPath("$.profileComplete").value(false))
                .andReturn();
        JsonNode body = mapper.readTree(res.getResponse().getContentAsString());
        return body.get("accessToken").asText();
    }

    @Test
    void rutasProtegidasSinTokenDevuelven401() throws Exception {
        mvc.perform(get("/api/v1/profile")).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/v1/meals/history")).andExpect(status().isUnauthorized());
    }

    @Test
    void loginCreaUsuarioYPermiteAcceso() throws Exception {
        String token = login();
        mvc.perform(get("/api/v1/profile").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.premium").value(false));
    }

    @Test
    void actualizarPerfilCalculaCaloriasObjetivo() throws Exception {
        String token = login();
        // Mifflin-St Jeor: hombre 28a, 70kg, 175cm, moderado, perder peso = 2071
        mvc.perform(put("/api/v1/profile")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                            {"name":"Juan","age":28,"sex":"MALE","weightKg":70,
                             "heightCm":175,"activityLevel":"MODERATE",
                             "goal":"LOSE_WEIGHT","targetWeightKg":65}
                            """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.targetCalories").value(2071))
                .andExpect(jsonPath("$.bmi").value(22.9));
    }

    @Test
    void guardarComidaYConsultarHistorialDelDia() throws Exception {
        String token = login();
        mvc.perform(post("/api/v1/meals")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                            {"mealType":"LUNCH","name":"Pollo con arroz","portion":"1 plato",
                             "total":{"calories":650,"proteinG":45,"carbsG":65,"fatG":18,
                                      "fiberG":6,"sodiumMg":480,"sugarG":4},
                             "items":[{"name":"Pollo","quantity":"150 g",
                                       "nutrition":{"calories":248,"proteinG":46,"carbsG":0,
                                                    "fatG":5,"fiberG":0,"sodiumMg":110,"sugarG":0}}]}
                            """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").isNotEmpty())
                .andExpect(jsonPath("$.items[0].name").value("Pollo"));

        mvc.perform(get("/api/v1/meals/history").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.meals.length()").value(1))
                .andExpect(jsonPath("$.totalCalories").value(650.0))
                .andExpect(jsonPath("$.totals.proteinG").value(45.0));
    }

    @Test
    void usuariosNoAccedenAComidasAjenas() throws Exception {
        String tokenA = login();
        var res = mvc.perform(post("/api/v1/meals")
                        .header("Authorization", "Bearer " + tokenA)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                            {"mealType":"SNACK","name":"Manzana",
                             "total":{"calories":95,"proteinG":0,"carbsG":25,"fatG":0,
                                      "fiberG":4,"sodiumMg":2,"sugarG":19}}
                            """))
                .andExpect(status().isOk())
                .andReturn();
        String mealId = mapper.readTree(res.getResponse().getContentAsString()).get("id").asText();

        String tokenB = login(); // otro usuario
        mvc.perform(delete("/api/v1/meals/" + mealId)
                        .header("Authorization", "Bearer " + tokenB))
                .andExpect(status().isNotFound());
    }

    @Test
    void registrarAguaAcumulaElDia() throws Exception {
        String token = login();
        mvc.perform(post("/api/v1/water")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"ml\":250}"))
                .andExpect(status().isOk());
        var res = mvc.perform(post("/api/v1/water")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"ml\":500}"))
                .andExpect(status().isOk())
                .andReturn();
        JsonNode body = mapper.readTree(res.getResponse().getContentAsString());
        assertThat(body.get("totalMl").asInt()).isEqualTo(750);
        assertThat(body.get("goalMl").asInt()).isEqualTo(2500);
    }

    @Test
    void compraPremiumActivaLaSuscripcion() throws Exception {
        String token = login();
        mvc.perform(post("/api/v1/subscriptions/verify")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"plan\":\"ANNUAL\",\"purchaseToken\":\"tok-sandbox\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.premium").value(true))
                .andExpect(jsonPath("$.plan").value("ANNUAL"));

        mvc.perform(get("/api/v1/profile").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.premium").value(true));
    }

    @Test
    void tokenInvalidoNoAutentica() throws Exception {
        mvc.perform(get("/api/v1/profile")
                        .header("Authorization", "Bearer no-es-un-jwt"))
                .andExpect(status().isUnauthorized());
    }
}
