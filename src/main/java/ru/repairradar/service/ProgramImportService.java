package ru.repairradar.service;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import ru.repairradar.dto.ProgramJobView;
import ru.repairradar.dto.RepairJobStatus;
import ru.repairradar.repository.RepairHouseRepository;
import ru.repairradar.utility.ReservoirSampler;

import java.util.List;
import java.util.UUID;
import java.util.random.RandomGenerator;

@Service
@RequiredArgsConstructor
public class ProgramImportService {

    private final RepairHouseRepository houses;
    private final RepairOperationGuard guard;
    private final ProgramJobJournal journal;
    private final ProgramImportWorker worker;
    private final ProgramDataCleaner cleaner;

    public UUID start() {
        var lease = guard.acquire();
        UUID jobId = null;
        try {
            List<UUID> selected = selectHouses();
            jobId = journal.create(selected);
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

    public ProgramJobView get(UUID id) {
        return journal.get(id);
    }

    public void clear() {
        try (var lease = guard.acquire()) {
            cleaner.clear();
        }
    }

    private List<UUID> selectHouses() {
        var sampler = new ReservoirSampler<UUID>(50, RandomGenerator.getDefault());
        houses.findProgramImportCandidates().forEach(sampler::accept);
        return sampler.values();
    }
}