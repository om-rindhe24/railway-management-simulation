package com.railway.simulator.controller;

import com.railway.simulator.controller.dto.PlatformClosureRequest;
import com.railway.simulator.controller.dto.TrainDelayRequest;
import com.railway.simulator.domain.Platform;
import com.railway.simulator.domain.Train;
import com.railway.simulator.repository.PlatformRepository;
import com.railway.simulator.repository.TrainRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
public class WhatIfStudioIsolationIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private TrainRepository trainRepository;

    @Autowired
    private PlatformRepository platformRepository;

    @Autowired
    private ObjectMapper objectMapper;

    private static final String API_KEY = "railway-dev-key-2026";

    @Test
    @DisplayName("What-If delay preview executes on an isolated copy without altering live train state")
    void testTrainDelayWhatIfRunsOnIsolatedCopy() throws Exception {
        Train train = trainRepository.findAll().stream()
                .filter(t -> "BVI".equalsIgnoreCase(t.getTargetStationCode()))
                .findFirst()
                .orElseThrow();

        int initialDelay = train.getDelayMinutes() != null ? train.getDelayMinutes() : 0;
        int injectedDelay = 25;

        TrainDelayRequest req = new TrainDelayRequest(injectedDelay, "What-If isolated test");

        // Execute preview with API key
        mockMvc.perform(post("/api/allocations/what-if/trains/" + train.getTrainId() + "/delay")
                        .header("X-API-KEY", API_KEY)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.isolatedCopy").value(true))
                .andExpect(jsonPath("$.data.diffs").isArray())
                .andExpect(jsonPath("$.data.diffs[0].afterDelayMinutes").isNumber());

        // CRITICAL CHECK: Verify live train in database is UNCHANGED
        Train liveTrainAfterPreview = trainRepository.findById(train.getTrainId()).orElseThrow();
        assertEquals(initialDelay, liveTrainAfterPreview.getDelayMinutes() != null ? liveTrainAfterPreview.getDelayMinutes() : 0,
                "Live train delay must NOT be changed by isolated What-If preview");

        // Now verify applying to live simulation updates live train state
        mockMvc.perform(put("/api/trains/" + train.getTrainId() + "/delay")
                        .header("X-API-KEY", API_KEY)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isOk());

        Train liveTrainAfterApply = trainRepository.findById(train.getTrainId()).orElseThrow();
        assertEquals(injectedDelay, liveTrainAfterApply.getDelayMinutes(),
                "Live train delay must be updated only after explicit live application");
    }

    @Test
    @DisplayName("What-If platform closure preview executes on an isolated copy without closing live platform")
    void testPlatformClosureWhatIfRunsOnIsolatedCopy() throws Exception {
        Platform platform = platformRepository.findByStationCodeOrderByPlatformNumberAsc("BVI").stream()
                .filter(p -> !Boolean.TRUE.equals(p.getIsClosed()))
                .findFirst()
                .orElseThrow();

        PlatformClosureRequest req = new PlatformClosureRequest("Isolated track maintenance test");

        // Execute closure preview with API key
        mockMvc.perform(post("/api/allocations/what-if/platforms/" + platform.getPlatformId() + "/close")
                        .header("X-API-KEY", API_KEY)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.isolatedCopy").value(true))
                .andExpect(jsonPath("$.data.reallocatedCount").isNumber());

        // CRITICAL CHECK: Verify live platform in database is STILL OPEN
        Platform livePlatform = platformRepository.findById(platform.getPlatformId()).orElseThrow();
        assertFalse(Boolean.TRUE.equals(livePlatform.getIsClosed()),
                "Live platform must remain OPEN during isolated What-If preview");
    }

    @Test
    @DisplayName("What-If preview endpoints reject unauthorized requests without API key")
    void testWhatIfEndpointsRequireApiKey() throws Exception {
        Train train = trainRepository.findAll().get(0);
        TrainDelayRequest req = new TrainDelayRequest(15, "Unauthorized test");

        mockMvc.perform(post("/api/allocations/what-if/trains/" + train.getTrainId() + "/delay")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isUnauthorized());
    }
}

