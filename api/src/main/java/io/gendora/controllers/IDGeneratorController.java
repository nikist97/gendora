package io.gendora.controllers;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.net.InetAddress;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/generator")
public class IDGeneratorController {

    @PostMapping("/ids")
    public ResponseEntity<Map<String, Object>> processIds() {
        String uuid = UUID.randomUUID().toString();
        String hostname;
        try {
            hostname = InetAddress.getLocalHost().getHostName();
        } catch (Exception e) {
            hostname = "unknown";
        }
        return ResponseEntity.ok(Map.of(
            "uuid", uuid,
            "timestamp", System.currentTimeMillis(),
            "hostname", hostname
        ));
    }
}
