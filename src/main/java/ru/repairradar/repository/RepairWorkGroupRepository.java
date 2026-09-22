package ru.repairradar.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import ru.repairradar.entity.RepairWorkGroup;
import java.util.UUID;

public interface RepairWorkGroupRepository extends JpaRepository<RepairWorkGroup, UUID> {
}
