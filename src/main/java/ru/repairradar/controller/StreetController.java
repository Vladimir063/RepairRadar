package ru.repairradar.controller;

import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import ru.repairradar.dto.StreetImportResult;
import ru.repairradar.service.StreetImportService;

@RestController
@RequestMapping("/api/streets")
@RequiredArgsConstructor
public class StreetController {

    private final StreetImportService service;

    @PostMapping("/import")
    public StreetImportResult importStreets() {
        return service.importStreets();
    }
}
