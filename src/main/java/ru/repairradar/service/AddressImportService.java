package ru.repairradar.service;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import ru.repairradar.config.GarProperties;
import ru.repairradar.dto.ImportResult;
import ru.repairradar.exception.OperationInProgressException;

import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Supplier;
import java.util.random.RandomGenerator;

@Service
@RequiredArgsConstructor
public class AddressImportService {

    private final GarAddressReader reader;
    private final AddressStore store;
    private final GarProperties properties;
    private final AtomicBoolean busy = new AtomicBoolean();

    public ImportResult importAddresses() {
        return runExclusively(() -> store.append(
                reader.readCandidates(properties.directory()), RandomGenerator.getDefault()));
    }

    public void clearAddresses() {
        runExclusively(() -> {
            store.clear();
            return null;
        });
    }

    private <T> T runExclusively(Supplier<T> action) {
        if (!busy.compareAndSet(false, true)) {
            throw new OperationInProgressException();
        }
        try {
            return action.get();
        } finally {
            busy.set(false);
        }
    }
}
