package ru.repairradar.service;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import ru.repairradar.repository.ProgramWorkRepository;
import ru.repairradar.utility.ProgramDates;

import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.List;

@Service
@RequiredArgsConstructor
public class ProgramSearchService {

    private final ProgramWorkRepository works;

    public List<String> findWorkTypeNames() {
        return works.findDistinctWorkTypeNames();
    }

    public List<String> searchAddresses(String workTypeName, String startDate, String endDate) {
        String name = workTypeName == null ? null : workTypeName.trim();
        if (name == null || name.isEmpty()) {
            throw new IllegalArgumentException("Тип работ не указан");
        }
        LocalDate from;
        LocalDate to;
        try {
            from = ProgramDates.start(startDate);
            to = ProgramDates.end(endDate);
        } catch (DateTimeParseException e) {
            throw new IllegalArgumentException("Неверный формат даты, ожидается ММ.гггг", e);
        }
        return works.findHouseAddresses(name, from, to);
    }
}
