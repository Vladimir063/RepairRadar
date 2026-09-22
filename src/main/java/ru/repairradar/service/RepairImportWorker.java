package ru.repairradar.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import ru.repairradar.dto.RepairJobStatus;

import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class RepairImportWorker {

    private final RepairStreetLoader loader;
    private final RepairJobJournal journal;

    @Async("repairImportExecutor")
    public void run(UUID jobId, List<UUID> streets, RepairOperationGuard.Lease lease) {
        boolean interrupted = false;
        try (lease) {
            try {
                journal.started(jobId);
                for (UUID street : streets) {
                    loadStreet(jobId, street);
                }
                journal.finished(jobId);
            } catch (InterruptedException e) {
                Thread.interrupted();
                interrupted = true;
                recordTermination(jobId, RepairJobStatus.INTERRUPTED, e);
            } catch (Exception e) {
                recordTermination(jobId, RepairJobStatus.FAILED, e);
            }
        } finally {
            if (interrupted) {
                Thread.currentThread().interrupt();
            }
        }
    }

    private void loadStreet(UUID jobId, UUID street) throws InterruptedException {
        if (Thread.currentThread().isInterrupted()) {
            throw new InterruptedException("Repair worker interrupted");
        }
        try {
            loader.load(jobId, street);
        } catch (RuntimeException e) {
            if (Thread.currentThread().isInterrupted()) {
                Thread.interrupted();
                var interruption = new InterruptedException("Repair worker interrupted during HTTP request");
                interruption.initCause(e);
                throw interruption;
            }
            log.error("Repair import failed: jobId={}, streetGuid={}", jobId, street, e);
            journal.streetFailed(jobId, street, e);
            return;
        }
        journal.streetSucceeded(jobId);
    }

    private void recordTermination(UUID jobId, RepairJobStatus status, Exception failure) {
        log.error("Repair job terminated: jobId={}, status={}", jobId, status, failure);
        try {
            journal.terminated(jobId, status, failure);
        } catch (Exception journalFailure) {
            log.error("Cannot persist termination of repair job {}", jobId, journalFailure);
        }
    }
}
