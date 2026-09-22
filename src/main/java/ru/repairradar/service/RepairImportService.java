package ru.repairradar.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import ru.repairradar.dto.RepairJobStatus;
import ru.repairradar.dto.RepairJobView;
import ru.repairradar.repository.StreetRepository;
import ru.repairradar.utility.ReservoirSampler;

import java.util.List;
import java.util.UUID;
import java.util.random.RandomGenerator;

@Service
@RequiredArgsConstructor
@Slf4j
public class RepairImportService {

    private final StreetRepository streets;
    private final RepairOperationGuard guard;
    private final RepairJobJournal journal;
    private final RepairImportWorker worker;
    private final RepairDataCleaner cleaner;

    public UUID start() {
        var lease = guard.acquire();
        UUID jobId = null;
        try {
            List<UUID> selected = selectStreets();
            jobId = journal.create(selected);
            log.info("selected {}", selected);
            worker.run(jobId, selected, lease);
            return jobId;
        } catch (RuntimeException e) {
            try {
                if (jobId != null) {
                    journal.terminated(jobId, RepairJobStatus.FAILED, e);
                }
            } finally {
                lease.close();
            }
            throw e;
        }
    }

    public RepairJobView get(UUID id) {
        return journal.get(id);
    }

    public void clear() {
        try (var lease = guard.acquire()) {
            cleaner.clear();
        }
    }

    private List<UUID> selectStreets() {
        var sampler = new ReservoirSampler<UUID>(20, RandomGenerator.getDefault());
        streets.findUnloadedStreetGuids().forEach(sampler::accept);
        return sampler.values();
    }
}
