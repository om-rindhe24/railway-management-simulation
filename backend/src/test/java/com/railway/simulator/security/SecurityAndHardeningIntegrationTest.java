package com.railway.simulator.security;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.httpBasic;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@TestPropertySource(properties = {
        "security.api-key=test-secret-api-key",
        "security.admin.username=testadmin",
        "security.admin.password=testpassword",
        "spring.h2.console.enabled=false"
})
class SecurityAndHardeningIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    @DisplayName("Public GET endpoints stay open without authentication")
    void testPublicGetEndpoints_openWithoutAuth() throws Exception {
        mockMvc.perform(get("/api/platforms"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));

        mockMvc.perform(get("/api/trains"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));

        mockMvc.perform(get("/api/simulation/state"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));

        mockMvc.perform(get("/api/stations"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));

        mockMvc.perform(get("/api/history"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));
    }

    @Test
    @DisplayName("Destructive endpoint without authentication returns 401 Unauthorized")
    void testDestructiveEndpoint_unauthenticatedReturns401() throws Exception {
        mockMvc.perform(post("/api/simulation/start"))
                .andExpect(status().isUnauthorized());

        mockMvc.perform(post("/api/platforms/1/close")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"reason\":\"test\"}"))
                .andExpect(status().isUnauthorized());

        mockMvc.perform(post("/api/allocations/run"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("Destructive endpoint with valid X-API-KEY succeeds")
    void testDestructiveEndpoint_withValidApiKey_succeeds() throws Exception {
        mockMvc.perform(post("/api/simulation/start")
                        .header("X-API-KEY", "test-secret-api-key"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));
    }

    @Test
    @DisplayName("Destructive endpoint with invalid X-API-KEY returns 401 Unauthorized")
    void testDestructiveEndpoint_withInvalidApiKey_returns401() throws Exception {
        mockMvc.perform(post("/api/simulation/start")
                        .header("X-API-KEY", "wrong-key"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("Destructive endpoint with valid HTTP Basic Auth succeeds")
    void testDestructiveEndpoint_withValidBasicAuth_succeeds() throws Exception {
        mockMvc.perform(post("/api/simulation/pause")
                        .with(httpBasic("testadmin", "testpassword")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));
    }

    @Test
    @DisplayName("H2 Web Console is not exposed / returns 404 or 403 when disabled in default profile")
    void testH2Console_notExposedInDefaultProfile() throws Exception {
        mockMvc.perform(get("/h2-console"))
                .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("Input validation: invalid stationCode returns 400 Bad Request, not 500")
    void testInvalidStationCode_returns400() throws Exception {
        mockMvc.perform(get("/api/platforms").param("stationCode", "INVALID_XYZ"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false));

        mockMvc.perform(get("/api/stations/UNKNOWN_STN"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false));
    }

    @Test
    @DisplayName("Input validation: simulation speed out of bounds returns 400 Bad Request")
    void testInvalidSimulationSpeed_returns400() throws Exception {
        mockMvc.perform(post("/api/simulation/speed")
                        .header("X-API-KEY", "test-secret-api-key")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"speed\": 99}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false));

        mockMvc.perform(post("/api/simulation/speed")
                        .header("X-API-KEY", "test-secret-api-key")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"speed\": 0}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false));
    }
}
