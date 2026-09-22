package ru.repairradar.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import java.util.UUID;
import java.time.LocalDate;
import java.time.Instant;
import java.math.BigDecimal;

@Embeddable
@Getter
@Setter
public class RepairProgramType {

    @Column(name = "program_type_guid")
    private UUID guid;

    @Column(name = "program_type_root_guid")
    private UUID rootGuid;

    @Column(name = "program_type_last_update_unix_time")
    private Long lastUpdateUnixTime;

    @Column(name = "program_type_last_update_date", columnDefinition = "text")
    private String lastUpdateDate;

    @Column(name = "program_type_create_date", columnDefinition = "text")
    private String createDate;

    @Column(name = "program_type_read_only")
    private Boolean readOnly;

    @Column(name = "program_type_active")
    private Boolean active;

    @Column(name = "program_type_code", columnDefinition = "text")
    private String code;

}
