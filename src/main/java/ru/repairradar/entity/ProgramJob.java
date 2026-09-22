package ru.repairradar.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import ru.repairradar.dto.RepairJobStatus;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "program_jobs", schema = "public")
@Getter
@Setter
public class ProgramJob {

    @Id
    private UUID id;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private RepairJobStatus status;

    @Column(name = "queued_at", nullable = false)
    private Instant queuedAt;

    @Column(name = "started_at")
    private Instant startedAt;

    @Column(name = "finished_at")
    private Instant finishedAt;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "selected_house_guids", nullable = false, columnDefinition = "jsonb")
    private String selectedHouseGuids;

    @Column(name = "successful_houses", nullable = false)
    private int successfulHouses;

    @Column(name = "failed_houses", nullable = false)
    private int failedHouses;

    @Column(name = "works_saved", nullable = false)
    private int worksSaved;

    @Column(name = "error_details", columnDefinition = "text")
    private String errorDetails;
}