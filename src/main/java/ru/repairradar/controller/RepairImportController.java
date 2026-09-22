package ru.repairradar.controller;

import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import ru.repairradar.dto.RepairJobView;
import ru.repairradar.service.RepairImportService;

import java.net.URI;
import java.util.UUID;

@RestController
@RequestMapping("/api/repair-imports")
@RequiredArgsConstructor
public class RepairImportController {

    private final RepairImportService service;

    @PostMapping
    public ResponseEntity<StartedJob> start() {
        UUID id = service.start();
        return ResponseEntity.accepted().location(URI.create("/api/repair-imports/" + id)).body(new StartedJob(id));
    }

    @GetMapping("/{id}")
    public RepairJobView get(@PathVariable UUID id) {
        return service.get(id);
    }

    @DeleteMapping
    public ResponseEntity<Void> clear() {
        service.clear();
        return ResponseEntity.noContent().build();
    }

    public record StartedJob(UUID jobId) {
    }
}
