package io.gendora.controllers;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class GeneratorControllerIntegrationTest {

    @Autowired
    private TestRestTemplate restTemplate;

    @Test
    @SuppressWarnings("unchecked")
    void testGenerateIdsEndpointSuccess() {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        HttpEntity<String> request = new HttpEntity<>("", headers);
        
        ResponseEntity<Map<String, Object>> response = restTemplate.exchange(
            "/generator/ids", 
            HttpMethod.POST, 
            request, 
            (Class<Map<String, Object>>) (Class<?>) Map.class
        );
        
        assertThat(response.getStatusCode().is2xxSuccessful()).isTrue();
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().get("id")).isNotNull();
        
        Map<String, Object> metadata = (Map<String, Object>) response.getBody().get("metadata");
        assertThat(metadata.get("timestampDelta")).isNotNull();
        assertThat(metadata.get("timestamp")).isNotNull();
        assertThat((Integer) metadata.get("machineID")).isBetween(0, 1023);
        assertThat(metadata.get("sequenceID")).isNotNull();
        assertThat(metadata.get("algorithm")).isEqualTo("snowflake");
    }
}
