package ru.repairradar.utility;

import java.time.LocalDate;
import java.time.YearMonth;
import java.time.format.DateTimeFormatter;

public final class ProgramDates {

    private static final DateTimeFormatter FORMAT = DateTimeFormatter.ofPattern("MM.yyyy");

    private ProgramDates() {
    }

    public static LocalDate start(String value) {
        return value == null ? null : YearMonth.parse(value, FORMAT).atDay(1);
    }

    public static LocalDate end(String value) {
        return value == null ? null : YearMonth.parse(value, FORMAT).atEndOfMonth();
    }
}