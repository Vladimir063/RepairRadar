package ru.repairradar.service;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.repairradar.repository.ProgramHouseRepository;
import ru.repairradar.repository.ProgramJobRepository;
import ru.repairradar.repository.ProgramWorkRepository;

@Service
@RequiredArgsConstructor
public class ProgramDataCleaner {

    private final ProgramWorkRepository works;
    private final ProgramHouseRepository houses;
    private final ProgramJobRepository jobs;

    @Transactional
    public void clear() {
        works.deleteAllInBatch();
        houses.deleteAllInBatch();
        jobs.deleteAllInBatch();
    }
}