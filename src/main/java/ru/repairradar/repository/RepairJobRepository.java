package ru.repairradar.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import ru.repairradar.dto.RepairJobStatus;
import ru.repairradar.entity.RepairJob;

import java.util.Collection;
import java.util.List;
import java.util.UUID;

public interface RepairJobRepository extends JpaRepository<RepairJob, UUID> {

    List<RepairJob> findByStatusIn(Collection<RepairJobStatus> statuses);
}
