package ru.repairradar.address;

import org.junit.jupiter.api.Test;
import org.springframework.core.task.TaskRejectedException;
import ru.repairradar.dto.RepairJobStatus;
import ru.repairradar.repository.StreetRepository;
import ru.repairradar.service.*;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class RepairLifecycleTest {

    @Test
    void rejectedAsyncDispatchRecordsFailureAndReleasesLease() {
        var streets = mock(StreetRepository.class);
        var guard = mock(RepairOperationGuard.class);
        var journal = mock(RepairJobJournal.class);
        var worker = mock(RepairImportWorker.class);
        var lease = mock(RepairOperationGuard.Lease.class);
        UUID job = UUID.randomUUID();
        when(guard.acquire()).thenReturn(lease);
        when(streets.findUnloadedStreetGuids()).thenReturn(List.of(UUID.randomUUID()));
        when(journal.create(anyList())).thenReturn(job);
        doThrow(new TaskRejectedException("full")).when(worker).run(eq(job), anyList(), eq(lease));
        var service = new RepairImportService(streets, guard, journal, worker, mock(RepairDataCleaner.class));

        assertThatThrownBy(service::start).isInstanceOf(TaskRejectedException.class);
        verify(journal).terminated(eq(job), eq(RepairJobStatus.FAILED), any(TaskRejectedException.class));
        verify(lease).close();
    }

    @Test
    void interruptedWorkerStopsAndReleasesLease() throws Exception {
        var loader = mock(RepairStreetLoader.class);
        var journal = mock(RepairJobJournal.class);
        var lease = mock(RepairOperationGuard.Lease.class);
        UUID job = UUID.randomUUID();
        UUID street = UUID.randomUUID();
        doThrow(new InterruptedException("shutdown")).when(loader).load(job, street);
        try {
            new RepairImportWorker(loader, journal).run(job, List.of(street, UUID.randomUUID()), lease);
            assertThat(Thread.currentThread().isInterrupted()).isTrue();
            verify(loader, times(1)).load(any(), any());
            verify(journal).terminated(eq(job), eq(RepairJobStatus.INTERRUPTED), any(InterruptedException.class));
            verify(lease).close();
        } finally {
            Thread.interrupted();
        }
    }
}
