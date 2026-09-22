package ru.repairradar.service;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import ru.repairradar.dto.RepairJobStatus;
import ru.repairradar.dto.ProgramJobView;
import ru.repairradar.entity.ProgramJob;
import ru.repairradar.exception.ProgramJobNotFoundException;
import ru.repairradar.repository.ProgramJobRepository;
import tools.jackson.databind.ObjectMapper;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Transactional(propagation = Propagation.REQUIRES_NEW)
public class ProgramJobJournal {

    private final ProgramJobRepository repository;
    private final ObjectMapper objectMapper;

    public UUID create(List<UUID> houses) {
        interruptAbandonedJobs();
        var job = new ProgramJob();
        job.setId(UUID.randomUUID());
        job.setQueuedAt(Instant.now());
        job.setStatus(RepairJobStatus.QUEUED);
        job.setSelectedHouseGuids(objectMapper.writeValueAsString(houses));
        return repository.save(job).getId();
    }

    public void started(UUID id) {
        ProgramJob job = find(id);
        job.setStartedAt(Instant.now());
        job.setStatus(RepairJobStatus.RUNNING);
    }

    public void houseSucceeded(UUID id, int works) {
        ProgramJob job = find(id);
        job.setSuccessfulHouses(job.getSuccessfulHouses() + 1);
        job.setWorksSaved(job.getWorksSaved() + works);
    }

    public void houseFailed(UUID id, UUID house, Exception exception) {
        ProgramJob job = find(id);
        job.setFailedHouses(job.getFailedHouses() + 1);
        appendError(job, "houseGuid=" + house + "\n" + stackTrace(exception));
    }

    public void finished(UUID id) {
        ProgramJob job = find(id);
        RepairJobStatus status = RepairJobStatus.SUCCESS;
        if (job.getFailedHouses() > 0) {
            status = job.getSuccessfulHouses() > 0 ? RepairJobStatus.PARTIAL_FAILED : RepairJobStatus.FAILED;
        }
        job.setStatus(status);
        job.setFinishedAt(Instant.now());
    }

    public void terminated(UUID id, RepairJobStatus status, Exception exception) {
        ProgramJob job = find(id);
        job.setStatus(status);
        job.setFinishedAt(Instant.now());
        appendError(job, stackTrace(exception));
    }

    @Transactional(readOnly = true)
    public ProgramJobView get(UUID id) {
        ProgramJob job = find(id);
        return new ProgramJobView(job.getId(), job.getStatus(), job.getQueuedAt(), job.getStartedAt(),
                job.getFinishedAt(), objectMapper.readTree(job.getSelectedHouseGuids()),
                job.getSuccessfulHouses(), job.getFailedHouses(), job.getWorksSaved(), job.getErrorDetails());
    }

    private ProgramJob find(UUID id) {
        return repository.findById(id).orElseThrow(() -> new ProgramJobNotFoundException(id));
    }

    private void interruptAbandonedJobs() {
        // Вызывающий владеет глобальной блокировкой: живых воркеров быть не может.
        for (ProgramJob job : repository.findByStatusIn(List.of(RepairJobStatus.QUEUED, RepairJobStatus.RUNNING))) {
            job.setStatus(RepairJobStatus.INTERRUPTED);
            job.setFinishedAt(Instant.now());
            appendError(job, "Предыдущий воркер остановился до завершения задачи; сохранённые данные не удалялись.");
        }
    }

    private void appendError(ProgramJob job, String error) {
        String previous = job.getErrorDetails();
        job.setErrorDetails(previous == null ? error : previous + "\n\n" + error);
    }

    private String stackTrace(Exception exception) {
        var writer = new StringWriter();
        exception.printStackTrace(new PrintWriter(writer));
        return writer.toString();
    }
}