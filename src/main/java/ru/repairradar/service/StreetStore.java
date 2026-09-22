package ru.repairradar.service;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;
import ru.repairradar.dto.StreetImportResult;
import ru.repairradar.dto.StreetRow;
import ru.repairradar.mapper.StreetMapper;
import ru.repairradar.repository.StreetRepository;

import java.util.List;
import java.util.HashSet;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class StreetStore {

    private final StreetRepository repository;
    private final StreetMapper mapper;

    @Transactional(isolation = Isolation.READ_COMMITTED)
    public StreetImportResult saveAll(List<StreetRow> candidates) {
        repository.lockForWrite();
        var knownGuids = new HashSet<>(repository.findAllStreetGuids());
        int inserted = 0;
        int updated = 0;
        for (StreetRow row : candidates) {
            int affected = repository.upsert(mapper.toEntity(row));
            if (knownGuids.add(row.streetGuid())) {
                inserted += affected;
            } else {
                updated += affected;
            }
        }
        return new StreetImportResult(inserted, updated, repository.count());
    }

    @Transactional
    public void markRepairDataLoaded(UUID streetGuid) {
        repository.markRepairDataLoaded(streetGuid);
    }
}
