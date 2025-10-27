package io.gendora.controllers;

import io.gendora.services.IDGeneratorService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.Map;

import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(GeneratorController.class)
class GeneratorControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private IDGeneratorService idGeneratorService;

    @Test
    void shouldGenerateIdSuccessfullyWithMetadata() throws Exception {
        // Given
        long generatedId = 1234567890L;
        Map<String, Object> metadata = Map.of(
                "timestampDelta", 1234L,
                "epoch", 1759276800000L,
                "machineID", 42,
                "sequenceID", 10,
                "algorithm", "snowflake"
        );

        when(idGeneratorService.generateId()).thenReturn(generatedId);
        when(idGeneratorService.getMetadata(anyLong())).thenReturn(metadata);

        // When & Then
        mockMvc.perform(post("/generator/ids")
                        .param("include_metadata", "true")
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.id").value(Long.toString(generatedId)))
                .andExpect(jsonPath("$.metadata.timestampDelta").value(1234L))
                .andExpect(jsonPath("$.metadata.epoch").value(1759276800000L))
                .andExpect(jsonPath("$.metadata.machineID").value(42))
                .andExpect(jsonPath("$.metadata.sequenceID").value(10))
                .andExpect(jsonPath("$.metadata.algorithm").value("snowflake"));
    }

    @Test
    void shouldGenerateIdSuccessfullyWithoutMetadata() throws Exception {
        // Given
        long generatedId = 1234567890L;

        when(idGeneratorService.generateId()).thenReturn(generatedId);

        // When & Then
        mockMvc.perform(post("/generator/ids")
                        .param("includeMetadata", "false")
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.id").value(Long.toString(generatedId)))
                .andExpect(jsonPath("$.metadata").doesNotExist());
    }

    @Test
    void shouldDefaultToFalseForIncludeMetadata() throws Exception {
        // Given
        long generatedId = 1234567890L;

        when(idGeneratorService.generateId()).thenReturn(generatedId);

        // When & Then
        mockMvc.perform(post("/generator/ids")
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.id").value(Long.toString(generatedId)))
                .andExpect(jsonPath("$.metadata").doesNotExist());
    }
}

