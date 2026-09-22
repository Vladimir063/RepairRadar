package ru.repairradar.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import java.util.UUID;
import java.time.LocalDate;
import java.time.Instant;
import java.math.BigDecimal;

@MappedSuperclass
@Getter
@Setter
public abstract class RepairMetadata {

    @Id
    @Column(name = "guid")
    private UUID guid;

    @Column(name = "root_guid")
    private UUID rootGuid;

    @Column(name = "last_update_unix_time")
    private Long lastUpdateUnixTime;

    @Column(name = "last_update_date", columnDefinition = "text")
    private String lastUpdateDate;

    @Column(name = "create_date", columnDefinition = "text")
    private String createDate;

    @Column(name = "read_only")
    private Boolean readOnly;

    @Column(name = "active")
    private Boolean active;

}
