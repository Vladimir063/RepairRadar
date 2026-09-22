package ru.repairradar.service;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import ru.repairradar.config.GarProperties;
import ru.repairradar.dto.StreetImportResult;
import ru.repairradar.exception.OperationInProgressException;

import java.util.concurrent.atomic.AtomicBoolean;

@Service
@RequiredArgsConstructor
public class StreetImportService {

    private final GarStreetReader reader;
    private final StreetStore store;
    private final GarProperties properties;
    private final AtomicBoolean busy = new AtomicBoolean();

    public StreetImportResult importStreets() {
        if (!busy.compareAndSet(false, true)) {
            throw new OperationInProgressException();
        }
        try {
            return store.saveAll(reader.readCandidates(properties.directory()));
        } finally {
            busy.set(false);
        }
    }
}
