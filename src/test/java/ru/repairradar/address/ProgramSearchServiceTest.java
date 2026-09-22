package ru.repairradar.address;

import org.junit.jupiter.api.Test;
import ru.repairradar.repository.ProgramWorkRepository;
import ru.repairradar.service.ProgramSearchService;

import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.*;

class ProgramSearchServiceTest {

    @Test
    void passesParsedRangeAndNameToRepository() {
        var works = mock(ProgramWorkRepository.class);
        var service = new ProgramSearchService(works);
        when(works.findHouseAddresses("водостока", LocalDate.of(2030, 1, 1), LocalDate.of(2032, 12, 31)))
                .thenReturn(List.of("адрес"));

        assertThat(service.searchAddresses("водостока", "01.2030", "12.2032")).containsExactly("адрес");
        verify(works).findHouseAddresses("водостока", LocalDate.of(2030, 1, 1), LocalDate.of(2032, 12, 31));
    }

    @Test
    void malformedDateIsRejectedAsBadRequestNotServerError() {
        var works = mock(ProgramWorkRepository.class);
        var service = new ProgramSearchService(works);

        assertThatThrownBy(() -> service.searchAddresses("водостока", "13.2030", "12.2032"))
                .isInstanceOf(IllegalArgumentException.class);
        verifyNoInteractions(works);
    }

    @Test
    void blankWorkTypeIsRejected() {
        var works = mock(ProgramWorkRepository.class);
        var service = new ProgramSearchService(works);

        assertThatThrownBy(() -> service.searchAddresses("  ", "01.2030", "12.2032"))
                .isInstanceOf(IllegalArgumentException.class);
        verifyNoInteractions(works);
    }
}