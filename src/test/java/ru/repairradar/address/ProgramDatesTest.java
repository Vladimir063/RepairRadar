package ru.repairradar.address;

import org.junit.jupiter.api.Test;
import ru.repairradar.utility.ProgramDates;

import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;

class ProgramDatesTest {

    @Test
    void parsesMonthYearIntoDayRange() {
        assertThat(ProgramDates.start("01.2042")).isEqualTo(LocalDate.of(2042, 1, 1));
        assertThat(ProgramDates.end("12.2032")).isEqualTo(LocalDate.of(2032, 12, 31));
        assertThat(ProgramDates.start(null)).isNull();
        assertThat(ProgramDates.end(null)).isNull();
    }
}