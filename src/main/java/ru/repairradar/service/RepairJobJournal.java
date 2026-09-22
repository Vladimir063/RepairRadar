package ru.repairradar.service;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import ru.repairradar.dto.RepairJobStatus;
import ru.repairradar.dto.RepairJobView;
import ru.repairradar.entity.RepairJob;
import ru.repairradar.exception.RepairJobNotFoundException;
import ru.repairradar.repository.RepairJobRepository;
import tools.jackson.databind.ObjectMapper;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Transactional(propagation = Propagation.REQUIRES_NEW)
public class RepairJobJournal {

    private final RepairJobRepository repository;
    private final ObjectMapper objectMapper;

    public UUID create(List<UUID> streets) {
        interruptAbandonedJobs();
        var job = new RepairJob();
        job.setId(UUID.randomUUID());
        job.setQueuedAt(Instant.now());
        job.setStatus(RepairJobStatus.QUEUED);
        job.setSelectedStreetGuids(objectMapper.writeValueAsString(streets));
        return repository.save(job).getId();
    }

    public void started(UUID id) {
        RepairJob job = find(id);
        job.setStartedAt(Instant.now());
        job.setStatus(RepairJobStatus.RUNNING);
    }

    public void streetSucceeded(UUID id) {
        RepairJob job = find(id);
        job.setSuccessfulStreets(job.getSuccessfulStreets() + 1);
    }

    public void streetFailed(UUID id, UUID street, Exception exception) {
        RepairJob job = find(id);
        job.setFailedStreets(job.getFailedStreets() + 1);
        appendError(job, "streetGuid=" + street + "\n" + stackTrace(exception));
    }

    public void finished(UUID id) {
        RepairJob job = find(id);
        RepairJobStatus status = RepairJobStatus.SUCCESS;
        if (job.getFailedStreets() > 0) {
            status = job.getSuccessfulStreets() > 0 || job.getPagesSaved() > 0
                    ? RepairJobStatus.PARTIAL_FAILED : RepairJobStatus.FAILED;
        }
        job.setStatus(status);
        job.setFinishedAt(Instant.now());
    }

    public void terminated(UUID id, RepairJobStatus status, Exception exception) {
        RepairJob job = find(id);
        job.setStatus(status);
        job.setFinishedAt(Instant.now());
        appendError(job, stackTrace(exception));
    }

    @Transactional(readOnly = true)
    public RepairJobView get(UUID id) {
        RepairJob job = find(id);
        return new RepairJobView(job.getId(), job.getStatus(), job.getQueuedAt(), job.getStartedAt(),
                job.getFinishedAt(), objectMapper.readTree(job.getSelectedStreetGuids()),
                job.getSuccessfulStreets(), job.getFailedStreets(), job.getPagesSaved(),
                job.getItemsSaved(), job.getErrorDetails());
    }

    private RepairJob find(UUID id) {
        return repository.findById(id).orElseThrow(() -> new RepairJobNotFoundException(id));
    }

    private void interruptAbandonedJobs() {
        // Caller owns the global session lock: no live worker can own these jobs.
        for (RepairJob job : repository.findByStatusIn(List.of(RepairJobStatus.QUEUED, RepairJobStatus.RUNNING))) {
            job.setStatus(RepairJobStatus.INTERRUPTED);
            job.setFinishedAt(Instant.now());
            appendError(job, "Previous worker stopped before completing the job; committed pages were preserved.");
        }
    }

    private void appendError(RepairJob job, String error) {
        String previous = job.getErrorDetails();
        job.setErrorDetails(previous == null ? error : previous + "\n\n" + error);
    }

    private String stackTrace(Exception exception) {
        var writer = new StringWriter();
        exception.printStackTrace(new PrintWriter(writer));
        return writer.toString();
    }
}
