package ru.repairradar.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.util.UUID;

@Entity
@Table(name = "repair_pages", schema = "public")
@Getter
@Setter
public class RepairPage {

    @Id
    private UUID id;

    @Column(name = "job_id", nullable = false)
    private UUID jobId;

    @Column(name = "street_guid", nullable = false)
    private UUID streetGuid;

    @Column(name = "page_index", nullable = false)
    private int pageIndex;

    @Column(name = "source_count")
    private Long sourceCount;

    @Column(name = "response_timestamp", columnDefinition = "text")
    private String responseTimestamp;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(nullable = false, columnDefinition = "jsonb")
    private String payload;
}
