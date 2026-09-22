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
@Table(name = "repair_jobs", schema = "public")
@Getter
@Setter
public class RepairJob {

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
    @Column(name = "selected_street_guids", nullable = false, columnDefinition = "jsonb")
    private String selectedStreetGuids;

    @Column(name = "successful_streets", nullable = false)
    private int successfulStreets;

    @Column(name = "failed_streets", nullable = false)
    private int failedStreets;

    @Column(name = "pages_saved", nullable = false)
    private int pagesSaved;

    @Column(name = "items_saved", nullable = false)
    private long itemsSaved;

    @Column(name = "error_details", columnDefinition = "text")
    private String errorDetails;
}
