package ru.repairradar.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;

import java.util.List;
import java.util.UUID;

@JsonIgnoreProperties(ignoreUnknown = true)
public record ProgramResponse(UUID guid, UUID houseGuid, String houseAddress,
                              @NotNull(message = "Upstream response has no works array")
                              List<@Valid @NotNull Work> works) {

    @Getter
    @Setter
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class Work {

        @NotNull
        private UUID guid;

        private Long workNumber;

        private String startDate;

        private String endDate;

        @Valid
        private WorkType workType;
    }

    @Getter
    @Setter
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class WorkType {

        private UUID guid;

        private String code;

        private String capitalRepairWorkTypeName;
    }
}