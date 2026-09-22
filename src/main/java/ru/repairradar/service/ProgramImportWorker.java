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
public class ProgramImportWorker {

    private final ProgramHouseLoader loader;
    private final ProgramJobJournal journal;

    @Async("repairImportExecutor")
    public void run(UUID jobId, List<UUID> houses, RepairOperationGuard.Lease lease) {
        boolean interrupted = false;
        try (lease) {
            try {
                journal.started(jobId);
                for (UUID house : houses) {
                    loadHouse(jobId, house);
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

    private void loadHouse(UUID jobId, UUID house) throws InterruptedException {
        if (Thread.currentThread().isInterrupted()) {
            throw new InterruptedException("Прерван воркер импорта программ");
        }
        try {
            int works = loader.load(house);
            journal.houseSucceeded(jobId, works);
        } catch (RuntimeException e) {
            if (Thread.currentThread().isInterrupted()) {
                Thread.interrupted();
                var interruption = new InterruptedException("Воркер прерван во время HTTP-запроса");
                interruption.initCause(e);
                throw interruption;
            }
            log.error("Не удалось загрузить данные программы: jobId={}, houseGuid={}", jobId, house, e);
            journal.houseFailed(jobId, house, e);
        }
    }

    private void recordTermination(UUID jobId, RepairJobStatus status, Exception failure) {
        log.error("Задача импорта программ завершена аварийно: jobId={}, status={}", jobId, status, failure);
        try {
            journal.terminated(jobId, status, failure);
        } catch (Exception journalFailure) {
            log.error("Не удалось сохранить завершение задачи импорта программ: jobId={}", jobId, journalFailure);
        }
    }
}