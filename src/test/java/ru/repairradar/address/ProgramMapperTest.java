package ru.repairradar.address;

import org.junit.jupiter.api.Test;
import ru.repairradar.dto.ProgramResponse;
import ru.repairradar.mapper.ProgramMapper;
import ru.repairradar.mapper.ProgramMapperImpl;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.Objects;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class ProgramMapperTest {

    private final ProgramMapper mapper = new ProgramMapperImpl();

    @Test
    void mapsProgramGuidParsesDatesAndAttachesHouse() throws IOException {
        ProgramResponse source = read();

        var house = mapper.toHouse(source);
        house.setHouseGuid(UUID.fromString("11111111-2222-3333-4444-555555555555"));

        assertThat(house.getProgramGuid()).isEqualTo(UUID.fromString("d088edca-32ed-4967-8620-70f8f583e195"));
        assertThat(house.getHouseAddress()).isEqualTo("125130, Москва г, ул. Клары Цеткин, д. 29, корп. 1");
        assertThat(house.getPayload()).isNull();
        assertThat(house.getWorks()).hasSize(2);
        assertThat(house.getWorks().get(0).getHouse()).isSameAs(house);
        assertThat(house.getWorks().get(0).getStartDate()).isEqualTo(LocalDate.of(2030, 1, 1));
        assertThat(house.getWorks().get(0).getEndDate()).isEqualTo(LocalDate.of(2032, 12, 31));
        assertThat(house.getWorks().get(0).getCapitalRepairWorkTypeName())
                .isEqualTo("ремонт или замена внутреннего водостока");
    }

    private ProgramResponse read() throws IOException {
        try (var input = getClass().getResourceAsStream("/program-response.json")) {
            String body = new String(Objects.requireNonNull(input).readAllBytes(), StandardCharsets.UTF_8);
            return new tools.jackson.databind.ObjectMapper().readValue(body, ProgramResponse.class);
        }
    }
}