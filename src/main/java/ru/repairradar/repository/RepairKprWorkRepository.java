package ru.repairradar.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import ru.repairradar.entity.RepairKprWork;
import java.util.UUID;

public interface RepairKprWorkRepository extends JpaRepository<RepairKprWork, UUID> {
}
