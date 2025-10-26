package io.gendora.controllers;

import io.gendora.services.IDGeneratorService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping("/generator")
public class GeneratorController {

    private final IDGeneratorService idGeneratorService;

    @Autowired
    public GeneratorController(IDGeneratorService idGeneratorService) {
        this.idGeneratorService = idGeneratorService;
    }

    @PostMapping("/ids")
    public ResponseEntity<Map<String, Object>> generateID() {
        long id = idGeneratorService.generateId();
        Map<String, Object> metadata = idGeneratorService.getMetadata(id);

        return ResponseEntity.ok(Map.of(
            "id", Long.toString(id),
            "metadata", metadata
        ));
    }
}
