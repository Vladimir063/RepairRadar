package ru.repairradar.service;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;
import ru.repairradar.dto.AddressRow;
import ru.repairradar.dto.ImportResult;
import ru.repairradar.mapper.AddressMapper;
import ru.repairradar.repository.AddressRepository;
import ru.repairradar.utility.ReservoirSampler;

import java.util.HashSet;
import java.util.List;
import java.util.random.RandomGenerator;

@Service
@RequiredArgsConstructor
public class AddressStore {

    private static final int SAMPLE_SIZE = 1000;

    private final AddressRepository repository;
    private final AddressMapper mapper;

    @Transactional(isolation = Isolation.READ_COMMITTED)
    public ImportResult append(List<AddressRow> candidates, RandomGenerator random) {
        repository.lockForWrite();
        var excluded = new HashSet<>(repository.findAllHouseGuids());
        var sample = new ReservoirSampler<AddressRow>(SAMPLE_SIZE, random);
        for (var row : candidates) {
            if (excluded.add(row.houseGuid())) {
                sample.accept(row);
            }
        }
        int inserted = 0;
        for (var row : sample.values()) {
            inserted += repository.insertIfAbsent(mapper.toEntity(row));
        }
        return new ImportResult(SAMPLE_SIZE, inserted, repository.count(), inserted < SAMPLE_SIZE);
    }

    @Transactional(isolation = Isolation.READ_COMMITTED)
    public void clear() {
        repository.lockForWrite();
        repository.deleteAllInBatch();
    }
}
