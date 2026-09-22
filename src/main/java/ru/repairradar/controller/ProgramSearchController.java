package ru.repairradar.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.annotation.*;
import ru.repairradar.service.ProgramSearchService;

import java.util.List;

@RestController
@RequestMapping("/api/programs")
@RequiredArgsConstructor
@Tag(name = "Программы ремонта", description = "Виды работ и поиск адресов по программе ремонта")
public class ProgramSearchController {

    private final ProgramSearchService service;

    @GetMapping("/work-types")
    @Operation(summary = "Получить уникальные названия видов работ",
            description = "Названия из program_works без повторений, в алфавитном порядке. "
                    + "Пустые значения исключены. При отсутствии данных возвращается пустой массив.")
    @ApiResponse(responseCode = "200", description = "Список названий видов работ")
    @ApiResponse(responseCode = "503", description = "Ошибка базы данных, код DATABASE_ERROR",
            content = @Content(mediaType = "application/problem+json",
                    schema = @Schema(implementation = ProblemDetail.class)))
    public List<String> workTypes() {
        return service.findWorkTypeNames();
    }

    @GetMapping("/search")
    @Operation(summary = "Найти адреса по виду работ и периоду",
            description = "Уникальные адреса домов. Название ищется по подстроке без учёта регистра; "
                    + "период работ должен пересекаться с заданным периодом, включая границы.")
    @ApiResponse(responseCode = "200", description = "Список адресов, пустой массив при отсутствии совпадений")
    @ApiResponse(responseCode = "400", description = "Некорректные параметры, код PROGRAM_SEARCH_INVALID",
            content = @Content(mediaType = "application/problem+json",
                    schema = @Schema(implementation = ProblemDetail.class)))
    @ApiResponse(responseCode = "503", description = "Ошибка базы данных, код DATABASE_ERROR",
            content = @Content(mediaType = "application/problem+json",
                    schema = @Schema(implementation = ProblemDetail.class)))
    public List<String> search(
            @Parameter(description = "Название вида работ или его часть", example = "водостока")
            @RequestParam String workTypeName,
            @Parameter(description = "Начало периода в формате ММ.гггг", example = "01.2030")
            @RequestParam String startDate,
            @Parameter(description = "Конец периода в формате ММ.гггг", example = "12.2032")
            @RequestParam String endDate) {
        return service.searchAddresses(workTypeName, startDate, endDate);
    }
}
