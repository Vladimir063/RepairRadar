package ru.repairradar.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import ru.repairradar.entity.ProgramWork;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

public interface ProgramWorkRepository extends JpaRepository<ProgramWork, UUID> {

    @Query("""
            select distinct w.capitalRepairWorkTypeName
            from ProgramWork w
            where w.capitalRepairWorkTypeName is not null
              and trim(w.capitalRepairWorkTypeName) <> ''
            order by w.capitalRepairWorkTypeName
            """)
    List<String> findDistinctWorkTypeNames();

    @Query("""
            select distinct w.house.houseAddress
            from ProgramWork w
            where lower(w.capitalRepairWorkTypeName) like lower(concat('%', :workTypeName, '%'))
              and w.startDate <= :rangeEnd
              and w.endDate >= :rangeStart
            """)
    List<String> findHouseAddresses(@Param("workTypeName") String workTypeName,
                                    @Param("rangeStart") LocalDate rangeStart,
                                    @Param("rangeEnd") LocalDate rangeEnd);
}
