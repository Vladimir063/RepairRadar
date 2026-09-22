package ru.repairradar.dto;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.AssertTrue;
import com.fasterxml.jackson.annotation.JsonIgnore;
import lombok.Getter;
import lombok.Setter;
import java.util.List;
import java.util.UUID;
import java.time.LocalDate;
import java.math.BigDecimal;
import tools.jackson.databind.JsonNode;

@JsonIgnoreProperties(ignoreUnknown = true)
public record RepairResponse(@NotNull(message = "Upstream response has no items array") List<@Valid @NotNull House> items, Long count, String responseTimestamp) {

    @Getter
    @Setter
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class Metadata {

        private UUID guid;

        private UUID rootGuid;

        private Long lastUpdateUnixTime;

        private String lastUpdateDate;

        private String createDate;

        private Boolean readOnly;

        private Boolean active;

    }

    public static class IdentifiedMetadata extends Metadata {

        @Override
        @NotNull
        public UUID getGuid() {
            return super.getGuid();
        }
    }

    @Getter
    @Setter
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class ProgramType extends Metadata {

        private String code;

    }

    @Getter
    @Setter
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class House extends IdentifiedMetadata {

        private String status;

        private UUID programGuid;

        private String programName;

        @JsonFormat(pattern = "dd.MM.yyyy")
        private LocalDate startDate;

        @JsonFormat(pattern = "dd.MM.yyyy")
        private LocalDate endDate;

        private Integer worksNumber;

        private ProgramType programType;

        private List<@Valid @NotNull RegionalWork> regionalWorks;

        private List<@Valid @NotNull KprWork> kprWorks;

    }

    @Getter
    @Setter
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class RegionalWork extends IdentifiedMetadata {

        private Long workNumber;

        private String workTypeCode;

        private String workGroupCode;

        @JsonFormat(pattern = "dd.MM.yyyy")
        private LocalDate startDate;

        @JsonFormat(pattern = "dd.MM.yyyy")
        private LocalDate endDate;

        private UUID fiasHouseGuid;

        private String oktmoCode;

        private UUID regionGuid;

        private String workTypeName;

        @Valid
        private WorkGroup workGroup;

    }

    @Getter
    @Setter
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class KprWork {

        @NotNull
        private UUID guid;

        private Long lastEditingDate;

        private Boolean fromExcel;

        private Long workNumber;

        private String workTypeCode;

        private String workGroupCode;

        private String workTypeName;

        private UUID houseGuid;

        private UUID fiasHouseGuid;

        private String houseAddress;

        private String oktmoCode;

        private UUID regionGuid;

        @JsonFormat(pattern = "dd.MM.yyyy")
        private LocalDate endDate;

        private BigDecimal fundSum;

        private BigDecimal subjectSum;

        private BigDecimal localSum;

        private BigDecimal ownerSum;

        private BigDecimal totalSum;

        private BigDecimal specificCost;

        private BigDecimal maximumCost;

        private BigDecimal contractedSum;

        private BigDecimal completePercent;

        @Valid
        private WorkGroup workGroup;

        private JsonNode contracts;

        @AssertTrue(message = "contracts must be an array or null")
        @JsonIgnore
        public boolean isContractsArrayOrNull() {
            return contracts == null || contracts.isNull() || contracts.isArray();
        }

    }

    @Getter
    @Setter
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class WorkGroup {

        @NotNull
        private UUID guid;

        private String code;

        private UUID rootEntityGuid;

        private Boolean actual;

        private String lastUpdateDate;

        private String createDate;

        private String name;

    }
}
