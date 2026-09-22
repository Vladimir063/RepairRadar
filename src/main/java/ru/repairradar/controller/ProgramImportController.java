package ru.repairradar.controller;

import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import ru.repairradar.dto.ProgramJobView;
import ru.repairradar.service.ProgramImportService;

import java.net.URI;
import java.util.UUID;

@RestController
@RequestMapping("/api/program-imports")
@RequiredArgsConstructor
public class ProgramImportController {

    private final ProgramImportService service;

    @PostMapping
    public ResponseEntity<StartedJob> start() {
        UUID id = service.start();
        return ResponseEntity.accepted().location(URI.create("/api/program-imports/" + id)).body(new StartedJob(id));
    }

    @GetMapping("/{id}")
    public ProgramJobView get(@PathVariable UUID id) {
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