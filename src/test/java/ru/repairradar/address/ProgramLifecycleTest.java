package ru.repairradar.address;

import org.junit.jupiter.api.Test;
import org.springframework.core.task.TaskRejectedException;
import ru.repairradar.dto.RepairJobStatus;
import ru.repairradar.exception.ProgramImportException;
import ru.repairradar.repository.RepairHouseRepository;
import ru.repairradar.service.*;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class ProgramLifecycleTest {

    @Test
    void rejectedAsyncDispatchRecordsFailureAndReleasesLease() {
        var houses = mock(RepairHouseRepository.class);
        var guard = mock(RepairOperationGuard.class);
        var journal = mock(ProgramJobJournal.class);
        var worker = mock(ProgramImportWorker.class);
        var lease = mock(RepairOperationGuard.Lease.class);
        UUID job = UUID.randomUUID();
        when(guard.acquire()).thenReturn(lease);
        when(houses.findProgramImportCandidates()).thenReturn(List.of(UUID.randomUUID()));
        when(journal.create(anyList())).thenReturn(job);
        doThrow(new TaskRejectedException("полный")).when(worker).run(eq(job), anyList(), eq(lease));
        var service = new ProgramImportService(houses, guard, journal, worker, mock(ProgramDataCleaner.class));

        assertThatThrownBy(service::start).isInstanceOf(TaskRejectedException.class);
        verify(journal).terminated(eq(job), eq(RepairJobStatus.FAILED), any(TaskRejectedException.class));
        verify(lease).close();
    }

    @Test
    void samplesFiftyUnloadedCandidatesOnly() {
        var candidates = new ArrayList<UUID>();
        for (int i = 0; i < 60; i++) {
            candidates.add(new UUID(0, 1000 + i));
        }
        var houses = mock(RepairHouseRepository.class);
        when(houses.findProgramImportCandidates()).thenReturn(candidates);
        var guard = mock(RepairOperationGuard.class);
        when(guard.acquire()).thenReturn(mock(RepairOperationGuard.Lease.class));
        var journal = mock(ProgramJobJournal.class);
        when(journal.create(anyList())).thenAnswer(invocation -> {
            List<UUID> selected = invocation.getArgument(0);
            assertThat(selected).hasSize(50);
            return UUID.randomUUID();
        });
        var worker = mock(ProgramImportWorker.class);
        var service = new ProgramImportService(houses, guard, journal, worker, mock(ProgramDataCleaner.class));

        service.start();

        verify(journal).create(anyList());
    }

    @Test
    void interruptedHouseStopsWorkerAndReleasesLease() throws Exception {
        var loader = mock(ProgramHouseLoader.class);
        var journal = mock(ProgramJobJournal.class);
        var lease = mock(RepairOperationGuard.Lease.class);
        UUID job = UUID.randomUUID();
        doThrow(new InterruptedException("прервано")).when(loader).load(any());
        try {
            new ProgramImportWorker(loader, journal).run(job, List.of(UUID.randomUUID(), UUID.randomUUID()), lease);
            assertThat(Thread.currentThread().isInterrupted()).isTrue();
            verify(loader, times(1)).load(any());
            verify(journal).terminated(eq(job), eq(RepairJobStatus.INTERRUPTED), any(InterruptedException.class));
            verify(lease).close();
        } finally {
            Thread.interrupted();
        }
    }

    @Test
    void failedHouseIsTrackedAndJobFinishesWithPartialFailure() throws Exception {
        var loader = mock(ProgramHouseLoader.class);
        var journal = mock(ProgramJobJournal.class);
        var lease = mock(RepairOperationGuard.Lease.class);
        UUID job = UUID.randomUUID();
        when(loader.load(any())).thenReturn(1).thenThrow(new ProgramImportException("ошибка"));
        var worker = new ProgramImportWorker(loader, journal);

        worker.run(job, List.of(UUID.randomUUID(), UUID.randomUUID()), lease);

        verify(journal).started(job);
        verify(journal).houseSucceeded(job, 1);
        verify(journal).houseFailed(eq(job), any(), any(ProgramImportException.class));
        verify(journal).finished(job);
        verify(lease).close();
    }
}