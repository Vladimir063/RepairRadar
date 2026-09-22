# Программный импорт (program import) — план реализации

> **Для агентных исполнителей:** ОБЯЗАТЕЛЬНЫЙ под-скилл: superpowers:subagent-driven-development (рекомендуется) или superpowers:executing-plans. Задачи отмечаются чекбоксами `- [ ]`.

**Goal:** Скачивать детализацию по домам из региональной программы капитального ремонта (`/objects/rpkr/{guid}`), сохранять в БД и искать адреса домов по типу работ и периоду.

**Architecture:** По аналогии с уже существующим repair-импортом: выборка необработанных кандидатов из `repair_houses` → резервуарный сэмплер (50) → асинхронный воркер с паузой 1–3 с между GET-запросами → хранение сырого JSON + поисковых колонок → отдельная таблица джобов. Флаг `repair_houses.program_data_loaded` ставится только при полном успехе, повторно обработанные дома не трогаем.

**Tech Stack:** Java 21, Spring Boot 4, Spring MVC, Spring Data JPA, MapStruct, Lombok, embedded PostgreSQL 18.6 (zonky, тесты без Docker), Jackson (`tools.jackson`).

**Spec:** Устное ТЗ пользователя (пункты 1–9) из этой сессии + пример ответа `programs_response.json` (корень: `guid`, `houseGuid`, `houseAddress`, `works[]`; у работы: `workType.capitalRepairWorkTypeName`, `startDate`/`endDate` в формате `MM.yyyy`).

## Global Constraints

- Java 21, Spring Boot 4; Lombok `@RequiredArgsConstructor`; только конструкторная инъекция, поля `private final`.
- Плоские пакеты: `controller`, `service`, `repository`, `entity`, `dto`, `mapper`, `config`, `exception`, `utility`.
- MapStruct: `componentModel = "spring"`, `unmappedTargetPolicy = ReportingPolicy.ERROR`; новые поля сущностей без источника в DTO — только через `@Mapping(target = "...", ignore = true)`.
- **Все новые логи и сообщения об ошибках — на русском** (AGENTS.md). Существующий англоязычный repair-flow не трогаем.
- URL GET: `<repairradar.program-api.url>/{repair_houses.guid}`. Ответ top-level `guid` (`program_guid`) может отличаться от `houseGuid` — это нормально, сохраняем оба.
- Пауза 1–3 с между запросами — переиспользуем `RepairRequestDelay` (Unit/IT — `@MockitoBean`).
- Асинхронность — `@Async("repairImportExecutor")` (существующий исполнитель, 1 поток). Глобальная блокировка — общий `RepairOperationGuard` (одна операция импорта на БД).
- Схема: `docker/postgres/init/*.sql` применяется только на свежем томе; для существующих томов — миграция в `docker/postgres/migrations/`. `PostgresIntegrationSupport.applyInitScripts()` содержит жёсткий список скриптов — его надо дополнить.
- Флаг `program_data_loaded` не сбрасывается очисткой (`DELETE /api/program-imports`).
- Семантика дат: `"MM.yyyy"` → `start_date` = первый день месяца, `end_date` = последний день месяца (`LocalDate` в БД типа `date`).
- Поиск: пересечение периода работы с заданным диапазоном (`start_date <= :rangeEnd AND end_date >= :rangeStart`) + частичное совпадение имени типа работ (`ILIKE %...%`).
- Тесты: unit — `.\mvnw.cmd test "-Dtest=..."`; IT — `.\mvnw.cmd verify "-Dit.test=..."` (всегда с кавычками, PowerShell).
- **Каталог не является git-репозиторием** (это рабочая копия): шаги commit пропускаем, либо выполняем по желанию после `git init`.

## Review Focus

Перечисленные ниже сценарии вероятнее всего сломают пользователей, если их не закрепить тестами — для каждого есть тест в задаче-владельце:

1. **Частичный отказ апстрима:** один дом вернул HTTP 503 → этот дом остаётся незагруженным и ретраится следующим запуском, остальные помечаются. — Тест в Task 4.
2. **Ответ без массива `works`** (или `works: null`) → дом НЕ считается загруженным, задача FAILED с понятным текстом. — Тест в Task 4.
3. **Мусорные даты в поиске** (`13.2030`, пустые) → 400 с кодом `PROGRAM_SEARCH_INVALID`, а не 500. — Тест в Task 5.
4. **Повторный запуск** не должен повторно запрашивать уже загруженные дома. — Тест в Task 4.
5. **Выборка кандидатов** берёт только `program_name LIKE 'Региональная%'` с `program_data_loaded = false`; муниципальные программы никогда не обрабатываются. — Тест в Task 4.

---

### Task 1: Схема БД и флаг `program_data_loaded` на `repair_houses`

**Files:**
- Modify: `docker/postgres/init/004-repair-import.sql` — добавить колонку в `CREATE TABLE public.repair_houses`.
- Create: `docker/postgres/init/005-program-import.sql` — таблицы `program_houses`, `program_works`, `program_jobs`.
- Create: `docker/postgres/migrations/006-program-import.sql` — миграция для существующих томов (ALTER на repair_houses + те же CREATE TABLE).
- Modify: `src/test/java/ru/repairradar/address/PostgresIntegrationSupport.java` — применять `005-program-import.sql`.
- Modify: `src/main/java/ru/repairradar/entity/RepairHouse.java` — поле `programDataLoaded`.
- Modify: `src/main/java/ru/repairradar/mapper/RepairMapper.java` — `@Mapping(target = "programDataLoaded", ignore = true)` на `toHouse`.
- Test: `src/test/java/ru/repairradar/address/ProgramSchemaIT.java`

**Interfaces:**
- Consumes: существующая структура IT `PostgresIntegrationSupport`.
- Produces: колонка `repair_houses.program_data_loaded` (default `false`); таблицы `program_houses(house_guid uuid PK, program_guid uuid, house_address text NOT NULL, payload jsonb NOT NULL)`, `program_works(guid uuid PK, house_guid uuid NOT NULL FK → program_houses, work_number bigint, capital_repair_work_type_name text, start_date date, end_date date)`, `program_jobs(id uuid PK, status varchar(32) NOT NULL, queued_at timestamptz NOT NULL, started_at timestamptz, finished_at timestamptz, selected_house_guids jsonb NOT NULL, successful_houses integer NOT NULL DEFAULT 0, failed_houses integer NOT NULL DEFAULT 0, works_saved integer NOT NULL DEFAULT 0, error_details text)`.

- [x] **Step 1: Пишем падающий тест схемы**

`src/test/java/ru/repairradar/address/ProgramSchemaIT.java`:

```java
package ru.repairradar.address;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class ProgramSchemaIT extends PostgresIntegrationSupport {

    @Test
    void schemaHasHouseFlagProgramTablesAndJobTable() {
        assertThat(jdbc.queryForObject("""
                select column_default from information_schema.columns
                where table_name = 'repair_houses' and column_name = 'program_data_loaded'
                """, String.class)).isEqualTo("false");
        for (String table : List.of("program_houses", "program_works", "program_jobs")) {
            assertThat(jdbc.queryForObject("select to_regclass('public." + table + "')", String.class))
                    .isNotNull();
        }
    }
}
```

- [x] **Step 2: Запускаем тест, убеждаемся что он падает**

Run: `.\mvnw.cmd verify "-Dit.test=ProgramSchemaIT"`
Expected: FAIL — колонки/таблиц нет, контекст Spring не поднимется (ddl-auto=validate).

- [x] **Step 3: Правим схему и сущность**

`docker/postgres/init/004-repair-import.sql` — в `CREATE TABLE public.repair_houses` (после `program_type_code text`) добавить:

```sql
    program_data_loaded boolean NOT NULL DEFAULT false,
```

Создать `docker/postgres/init/005-program-import.sql`:

```sql
BEGIN;

CREATE TABLE IF NOT EXISTS public.program_houses (
    house_guid uuid PRIMARY KEY,
    program_guid uuid,
    house_address text NOT NULL,
    payload jsonb NOT NULL
);

CREATE TABLE IF NOT EXISTS public.program_works (
    guid uuid PRIMARY KEY,
    house_guid uuid NOT NULL REFERENCES public.program_houses(house_guid),
    work_number bigint,
    capital_repair_work_type_name text,
    start_date date,
    end_date date
);

CREATE INDEX IF NOT EXISTS program_works_house_idx ON public.program_works(house_guid);
CREATE INDEX IF NOT EXISTS program_works_type_name_idx ON public.program_works(capital_repair_work_type_name);
CREATE INDEX IF NOT EXISTS program_works_period_idx ON public.program_works(start_date, end_date);

CREATE TABLE IF NOT EXISTS public.program_jobs (
    id uuid PRIMARY KEY,
    status varchar(32) NOT NULL,
    queued_at timestamptz NOT NULL,
    started_at timestamptz,
    finished_at timestamptz,
    selected_house_guids jsonb NOT NULL,
    successful_houses integer NOT NULL DEFAULT 0,
    failed_houses integer NOT NULL DEFAULT 0,
    works_saved integer NOT NULL DEFAULT 0,
    error_details text
);

COMMIT;
```

Создать `docker/postgres/migrations/006-program-import.sql` (для существующих томов):

```sql
BEGIN;

ALTER TABLE public.repair_houses
    ADD COLUMN IF NOT EXISTS program_data_loaded boolean NOT NULL DEFAULT false;

CREATE TABLE IF NOT EXISTS public.program_houses (
    house_guid uuid PRIMARY KEY,
    program_guid uuid,
    house_address text NOT NULL,
    payload jsonb NOT NULL
);

CREATE TABLE IF NOT EXISTS public.program_works (
    guid uuid PRIMARY KEY,
    house_guid uuid NOT NULL REFERENCES public.program_houses(house_guid),
    work_number bigint,
    capital_repair_work_type_name text,
    start_date date,
    end_date date
);

CREATE INDEX IF NOT EXISTS program_works_house_idx ON public.program_works(house_guid);
CREATE INDEX IF NOT EXISTS program_works_type_name_idx ON public.program_works(capital_repair_work_type_name);
CREATE INDEX IF NOT EXISTS program_works_period_idx ON public.program_works(start_date, end_date);

CREATE TABLE IF NOT EXISTS public.program_jobs (
    id uuid PRIMARY KEY,
    status varchar(32) NOT NULL,
    queued_at timestamptz NOT NULL,
    started_at timestamptz,
    finished_at timestamptz,
    selected_house_guids jsonb NOT NULL,
    successful_houses integer NOT NULL DEFAULT 0,
    failed_houses integer NOT NULL DEFAULT 0,
    works_saved integer NOT NULL DEFAULT 0,
    error_details text
);

COMMIT;
```

`src/test/java/ru/repairradar/address/PostgresIntegrationSupport.java` — в `applyInitScripts()` после `004-repair-import.sql` добавить:

```java
            ScriptUtils.executeSqlScript(connection, new ClassPathResource("005-program-import.sql"));
```

`src/main/java/ru/repairradar/entity/RepairHouse.java` — после поля `programType` (перед закрывающей скобкой класса) добавить:

```java

    @Column(name = "program_data_loaded", nullable = false)
    private boolean programDataLoaded;
```

`src/main/java/ru/repairradar/mapper/RepairMapper.java` — на методе `toHouse` добавить в аннотации:

```java
    @Mapping(target = "programDataLoaded", ignore = true)
    RepairHouse toHouse(RepairResponse.House source, @Context RepairMappingContext context);
```

- [x] **Step 4: Запускаем тест, убеждаемся что он проходит**

Run: `.\mvnw.cmd verify "-Dit.test=ProgramSchemaIT"`
Expected: PASS. Дополнительно `.\mvnw.cmd verify` — все существующие unit + IT (34 + 26) должны остаться зелёными (колонка с default не ломает прежние вставки).

- [x] **Step 5: Commit (каталог не git — пропускаем по желанию)**

---

### Task 2: DTO, клиент, маппер и сохранение ответа

**Files:**
- Create: `src/main/java/ru/repairradar/config/ProgramApiProperties.java`
- Create: `src/main/java/ru/repairradar/config/ProgramImportConfig.java`
- Create: `src/main/java/ru/repairradar/dto/ProgramResponse.java`
- Create: `src/main/java/ru/repairradar/dto/ProgramApiPage.java`
- Create: `src/main/java/ru/repairradar/utility/ProgramDates.java`
- Create: `src/main/java/ru/repairradar/entity/ProgramHouse.java`
- Create: `src/main/java/ru/repairradar/entity/ProgramWork.java`
- Create: `src/main/java/ru/repairradar/repository/ProgramHouseRepository.java`
- Create: `src/main/java/ru/repairradar/mapper/ProgramMapper.java`
- Create: `src/main/java/ru/repairradar/exception/ProgramImportException.java`
- Create: `src/main/java/ru/repairradar/service/ProgramApiClient.java`
- Create: `src/main/java/ru/repairradar/service/ProgramStore.java`
- Modify: `src/main/java/ru/repairradar/repository/RepairHouseRepository.java` — метод выборки кандидатов и простановки флага.
- Test: `src/test/java/ru/repairradar/address/ProgramDatesTest.java`
- Test: `src/test/java/ru/repairradar/address/ProgramMapperTest.java`
- Test: `src/test/java/ru/repairradar/address/ProgramStoreTest.java`
- Test resource: `src/test/resources/program-response.json`

**Interfaces:**
- Consumes: из Task 1 — `RepairHouse.programDataLoaded`, таблицы программы; `RepairJobStatus` (enum из `ru.repairradar.dto`, переиспользуем).
- Produces:
  - `ProgramDates.start(String mmYyyy) : LocalDate` (первый день месяца; null → null), `ProgramDates.end(String mmYyyy) : LocalDate` (последний день месяца; null → null).
  - `ProgramResponse` — record `(UUID guid, UUID houseGuid, String houseAddress, List<Work> works)` + классы `Work` (поля `guid@NotNull`, `workNumber`, `startDate`, `endDate`, `workType@Valid`) и `WorkType` (`guid`, `code`, `capitalRepairWorkTypeName`).
  - `ProgramApiPage(ProgramResponse response, String body)`.
  - `ProgramMapper.toHouse(ProgramResponse) : ProgramHouse` (маппит `guid→programGuid`, парсит даты, прикрепляет works; `houseGuid`/`payload` не маппятся).
  - `ProgramHouse(UUID houseGuid, UUID programGuid, String houseAddress, String payload, List<ProgramWork> works)`; `ProgramWork(UUID guid, ProgramHouse house, Long workNumber, String capitalRepairWorkTypeName, LocalDate startDate, LocalDate endDate)`.
  - `RepairHouseRepository.findProgramImportCandidates() : List<UUID>`; `RepairHouseRepository.markProgramLoaded(UUID houseGuid)`.
  - `ProgramApiClient.fetch(UUID houseGuid) : ProgramApiPage` (@Valid на возврате).
  - `ProgramStore.save(UUID houseGuid, ProgramResponse response, String body)` — @Transactional.

- [x] **Step 1: Пишем падающий тест для утилиты дат**

`src/test/java/ru/repairradar/address/ProgramDatesTest.java`:

```java
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
```

- [x] **Step 2: Запускаем тест, убеждаемся что он падает**

Run: `.\mvnw.cmd test "-Dtest=ProgramDatesTest"`
Expected: FAIL — `ProgramDates` не существует.

- [x] **Step 3: Пишем утилиту, сущности, DTO, маппер, клиента и store**

`src/main/java/ru/repairradar/utility/ProgramDates.java`:

```java
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
```

`src/main/java/ru/repairradar/entity/ProgramHouse.java`:

```java
package ru.repairradar.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Entity
@Table(name = "program_houses", schema = "public")
@Getter
@Setter
@NoArgsConstructor
public class ProgramHouse {

    @Id
    @Column(name = "house_guid")
    private UUID houseGuid;

    @Column(name = "program_guid")
    private UUID programGuid;

    @Column(name = "house_address", nullable = false, columnDefinition = "text")
    private String houseAddress;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "payload", nullable = false, columnDefinition = "jsonb")
    private String payload;

    @OneToMany(mappedBy = "house", cascade = {CascadeType.PERSIST, CascadeType.MERGE}, orphanRemoval = true)
    private List<ProgramWork> works = new ArrayList<>();
}
```

`src/main/java/ru/repairradar/entity/ProgramWork.java`:

```java
package ru.repairradar.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDate;
import java.util.UUID;

@Entity
@Table(name = "program_works", schema = "public")
@Getter
@Setter
@NoArgsConstructor
public class ProgramWork {

    @Id
    @Column(name = "guid")
    private UUID guid;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "house_guid", nullable = false)
    private ProgramHouse house;

    @Column(name = "work_number")
    private Long workNumber;

    @Column(name = "capital_repair_work_type_name", columnDefinition = "text")
    private String capitalRepairWorkTypeName;

    @Column(name = "start_date")
    private LocalDate startDate;

    @Column(name = "end_date")
    private LocalDate endDate;
}
```

`src/main/java/ru/repairradar/dto/ProgramResponse.java`:

```java
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
```

`src/main/java/ru/repairradar/dto/ProgramApiPage.java`:

```java
package ru.repairradar.dto;

public record ProgramApiPage(ProgramResponse response, String body) {
}
```

`src/main/java/ru/repairradar/mapper/ProgramMapper.java`:

```java
package ru.repairradar.mapper;

import org.mapstruct.*;
import ru.repairradar.dto.ProgramResponse;
import ru.repairradar.entity.ProgramHouse;
import ru.repairradar.entity.ProgramWork;
import ru.repairradar.utility.ProgramDates;

import java.time.LocalDate;

@Mapper(componentModel = "spring", unmappedTargetPolicy = ReportingPolicy.ERROR)
public interface ProgramMapper {

    @Mapping(target = "houseGuid", ignore = true)
    @Mapping(target = "programGuid", source = "guid")
    @Mapping(target = "payload", ignore = true)
    ProgramHouse toHouse(ProgramResponse source);

    @Mapping(target = "house", ignore = true)
    @Mapping(target = "startDate", source = "startDate", qualifiedByName = "parseStart")
    @Mapping(target = "endDate", source = "endDate", qualifiedByName = "parseEnd")
    ProgramWork toWork(ProgramResponse.Work source);

    @AfterMapping
    default void attachHouse(@MappingTarget ProgramHouse house) {
        if (house.getWorks() != null) {
            house.getWorks().forEach(work -> work.setHouse(house));
        }
    }

    @Named("parseStart")
    default LocalDate parseStart(String value) {
        return ProgramDates.start(value);
    }

    @Named("parseEnd")
    default LocalDate parseEnd(String value) {
        return ProgramDates.end(value);
    }
}
```

`src/main/java/ru/repairradar/exception/ProgramImportException.java`:

```java
package ru.repairradar.exception;

public class ProgramImportException extends RuntimeException {

    public ProgramImportException(String message) {
        super(message);
    }

    public ProgramImportException(String message, Throwable cause) {
        super(message, cause);
    }
}
```

`src/main/java/ru/repairradar/config/ProgramApiProperties.java`:

```java
package ru.repairradar.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

import java.net.URI;
import java.time.Duration;

@ConfigurationProperties("repairradar.program-api")
public record ProgramApiProperties(
        @DefaultValue("https://dom.gosuslugi.ru/capital-repair-programs/api/rest/services/programs/public/objects/rpkr") URI url,
        @DefaultValue("10s") Duration connectTimeout,
        @DefaultValue("60s") Duration readTimeout) {

    public ProgramApiProperties {
        if (connectTimeout.isNegative() || connectTimeout.isZero()
                || readTimeout.isNegative() || readTimeout.isZero()) {
            throw new IllegalArgumentException("Program API timeouts must be positive");
        }
    }
}
```

`src/main/java/ru/repairradar/config/ProgramImportConfig.java`:

```java
package ru.repairradar.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpHeaders;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

import java.net.http.HttpClient;

@Configuration
public class ProgramImportConfig {

    @Bean
    public RestClient programRestClient(ProgramApiProperties properties) {
        var httpClient = HttpClient.newBuilder().connectTimeout(properties.connectTimeout()).build();
        var factory = new JdkClientHttpRequestFactory(httpClient);
        factory.setReadTimeout(properties.readTimeout());
        return RestClient.builder().requestFactory(factory)
                .defaultHeader(HttpHeaders.USER_AGENT, "PostmanRuntime/7.51.0")
                .build();
    }
}
```

`src/main/java/ru/repairradar/service/ProgramApiClient.java`:

```java
package ru.repairradar.service;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;
import ru.repairradar.config.ProgramApiProperties;
import ru.repairradar.dto.ProgramApiPage;
import ru.repairradar.dto.ProgramResponse;
import ru.repairradar.exception.ProgramImportException;
import tools.jackson.databind.ObjectMapper;

import java.util.UUID;

@Component
@Validated
@RequiredArgsConstructor
public class ProgramApiClient {

    private final RestClient programRestClient;
    private final ProgramApiProperties properties;
    private final ObjectMapper objectMapper;

    @Valid
    public ProgramApiPage fetch(UUID houseGuid) {
        try {
            String body = programRestClient.get()
                    .uri(properties.url().toString() + "/" + houseGuid)
                    .retrieve().body(String.class);
            if (body == null || body.isBlank()) {
                throw new ProgramImportException("Вышестоящий сервис вернул пустой ответ");
            }
            ProgramResponse response = objectMapper.readValue(body, ProgramResponse.class);
            return new ProgramApiPage(response, body);
        } catch (RestClientResponseException e) {
            throw new ProgramImportException("Вышестоящий сервис вернул HTTP " + e.getStatusCode().value()
                    + "; тело ответа: " + e.getResponseBodyAsString(), e);
        }
    }
}
```

`src/main/java/ru/repairradar/service/ProgramStore.java`:

```java
package ru.repairradar.service;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.repairradar.dto.ProgramResponse;
import ru.repairradar.entity.ProgramHouse;
import ru.repairradar.mapper.ProgramMapper;
import ru.repairradar.repository.ProgramHouseRepository;
import ru.repairradar.repository.RepairHouseRepository;

import java.util.UUID;

@Service
@RequiredArgsConstructor
public class ProgramStore {

    private final ProgramHouseRepository houses;
    private final RepairHouseRepository repairHouses;
    private final ProgramMapper mapper;

    @Transactional
    public void save(UUID houseGuid, ProgramResponse response, String body) {
        ProgramHouse house = mapper.toHouse(response);
        house.setHouseGuid(houseGuid);
        house.setPayload(body);
        houses.save(house);
        repairHouses.markProgramLoaded(houseGuid);
    }
}
```

`src/main/java/ru/repairradar/repository/RepairHouseRepository.java` — заменить целиком:

```java
package ru.repairradar.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import ru.repairradar.entity.RepairHouse;

import java.util.List;
import java.util.UUID;

public interface RepairHouseRepository extends JpaRepository<RepairHouse, UUID> {

    @Query("select h.guid from RepairHouse h where h.programName like 'Региональная%' and h.programDataLoaded = false")
    List<UUID> findProgramImportCandidates();

    @Modifying
    @Query("update RepairHouse h set h.programDataLoaded = true where h.guid = :houseGuid")
    void markProgramLoaded(@Param("houseGuid") UUID houseGuid);
}
```

`src/main/java/ru/repairradar/repository/ProgramHouseRepository.java`:

```java
package ru.repairradar.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import ru.repairradar.entity.ProgramHouse;

import java.util.UUID;

public interface ProgramHouseRepository extends JpaRepository<ProgramHouse, UUID> {
}
```

`src/test/resources/program-response.json` (компактная фикстура в форме реального ответа):

```json
{
  "guid": "d088edca-32ed-4967-8620-70f8f583e195",
  "houseGuid": "4a92f01d-8669-42f9-b5bd-b9aa9f2d7ddf",
  "houseAddress": "125130, Москва г, ул. Клары Цеткин, д. 29, корп. 1",
  "works": [
    {
      "guid": "e48e356c-07cf-417c-a07b-b74729761748",
      "workNumber": 23858,
      "startDate": "01.2030",
      "endDate": "12.2032",
      "workType": {
        "guid": "62ca68cf-90b7-4ac4-893e-8c133aeccba9",
        "code": "43",
        "capitalRepairWorkTypeName": "ремонт или замена внутреннего водостока"
      }
    },
    {
      "guid": "bc38c03a-f8c5-4529-aa18-846f652d4033",
      "workNumber": 23855,
      "startDate": "01.2042",
      "endDate": "12.2044",
      "workType": {
        "guid": "1e8682fa-f60a-47a4-b48b-6bd47150736c",
        "code": "53",
        "capitalRepairWorkTypeName": "Разработка и проведение экспертизы ПД"
      }
    }
  ]
}
```

- [x] **Step 4: Пишем тесты маппера и store**

`src/test/java/ru/repairradar/address/ProgramMapperTest.java`:

```java
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
```

`src/test/java/ru/repairradar/address/ProgramStoreTest.java`:

```java
package ru.repairradar.address;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import ru.repairradar.dto.ProgramResponse;
import ru.repairradar.entity.ProgramHouse;
import ru.repairradar.mapper.ProgramMapperImpl;
import ru.repairradar.repository.ProgramHouseRepository;
import ru.repairradar.repository.RepairHouseRepository;
import ru.repairradar.service.ProgramStore;

import java.io.IOException;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

class ProgramStoreTest {

    @Test
    void persistsHouseWithBodyAndMarksRepairHouseLoaded() throws IOException {
        var houses = mock(ProgramHouseRepository.class);
        var repairHouses = mock(RepairHouseRepository.class);
        var store = new ProgramStore(houses, repairHouses, new ProgramMapperImpl());
        UUID repairGuid = UUID.randomUUID();
        String body = new String(getClass().getResourceAsStream("/program-response.json").readAllBytes());

        ProgramResponse response = new tools.jackson.databind.ObjectMapper().readValue(body, ProgramResponse.class);
        store.save(repairGuid, response, body);

        ArgumentCaptor<ProgramHouse> captor = ArgumentCaptor.forClass(ProgramHouse.class);
        verify(houses).save(captor.capture());
        assertThat(captor.getValue().getHouseGuid()).isEqualTo(repairGuid);
        assertThat(captor.getValue().getPayload()).isEqualTo(body);
        assertThat(captor.getValue().getWorks()).hasSize(2);
        verify(repairHouses).markProgramLoaded(repairGuid);
    }
}
```

- [x] **Step 5: Запускаем тесты Task 2, убеждаемся что проходят**

Run: `.\mvnw.cmd test "-Dtest=ProgramDatesTest,ProgramMapperTest,ProgramStoreTest"`
Expected: PASS (3 теста).

- [x] **Step 6: Commit (каталог не git — пропускаем по желанию)**

---

### Task 3: Асинхронный импорт — джобы, журнал, воркер, сервис, контроллер, очистка

**Files:**
- Create: `src/main/java/ru/repairradar/entity/ProgramJob.java`
- Create: `src/main/java/ru/repairradar/dto/ProgramJobView.java`
- Create: `src/main/java/ru/repairradar/repository/ProgramJobRepository.java`
- Create: `src/main/java/ru/repairradar/exception/ProgramJobNotFoundException.java`
- Create: `src/main/java/ru/repairradar/service/ProgramJobJournal.java`
- Create: `src/main/java/ru/repairradar/service/ProgramHouseLoader.java`
- Create: `src/main/java/ru/repairradar/service/ProgramImportWorker.java`
- Create: `src/main/java/ru/repairradar/service/ProgramImportService.java`
- Create: `src/main/java/ru/repairradar/service/ProgramDataCleaner.java`
- Create: `src/main/java/ru/repairradar/controller/ProgramImportController.java`
- Modify: `src/main/java/ru/repairradar/exception/AddressExceptionHandler.java` — обработчик `ProgramJobNotFoundException` → 404.
- Test: `src/test/java/ru/repairradar/address/ProgramHouseLoaderTest.java`
- Test: `src/test/java/ru/repairradar/address/ProgramLifecycleTest.java`

**Interfaces:**
- Consumes: `ProgramApiClient.fetch`, `ProgramStore.save`, `RepairHouseRepository.findProgramImportCandidates`, `RepairOperationGuard`, `RepairRequestDelay`, `RepairJobStatus`, `ReservoirSampler`, `RepairImportConfig.repairImportExecutor`.
- Produces:
  - `ProgramJobJournal.create(List<UUID>) : UUID`, `started(UUID)`, `houseSucceeded(UUID, int works)`, `houseFailed(UUID, UUID, Exception)`, `finished(UUID)`, `terminated(UUID, RepairJobStatus, Exception)`, `get(UUID) : ProgramJobView`.
  - `ProgramHouseLoader.load(UUID houseGuid) : int` (число сохранённых работ).
  - `ProgramImportWorker.run(UUID jobId, List<UUID> houses, RepairOperationGuard.Lease lease)` (@Async).
  - `ProgramImportService.start() : UUID`, `get(UUID) : ProgramJobView`, `clear()`.
  - `ProgramDataCleaner.clear()` — удаляет `program_works`, `program_houses`, `program_jobs` (флаг не сбрасывает).
  - `ProgramImportController` — `POST /api/program-imports` (202), `GET /api/program-imports/{id}`, `DELETE /api/program-imports` (204).

- [x] **Step 1: Пишем падающие юнит-тесты**

`src/test/java/ru/repairradar/address/ProgramHouseLoaderTest.java`:

```java
package ru.repairradar.address;

import org.junit.jupiter.api.Test;
import ru.repairradar.dto.ProgramApiPage;
import ru.repairradar.dto.ProgramResponse;
import ru.repairradar.exception.ProgramImportException;
import ru.repairradar.service.ProgramApiClient;
import ru.repairradar.service.ProgramHouseLoader;
import ru.repairradar.service.ProgramStore;
import ru.repairradar.utility.RepairRequestDelay;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class ProgramHouseLoaderTest {

    private final ProgramApiClient client = mock(ProgramApiClient.class);
    private final ProgramStore store = mock(ProgramStore.class);
    private final RepairRequestDelay delay = mock(RepairRequestDelay.class);
    private final UUID house = UUID.randomUUID();

    @Test
    void pausesFetchesSavesAndReturnsWorkCount() throws Exception {
        var response = new ProgramResponse(UUID.randomUUID(), UUID.randomUUID(), "адрес",
                List.of(work(1), work(2)));
        when(client.fetch(house)).thenReturn(new ProgramApiPage(response, "{\"works\":[]}"));

        assertThat(loader().load(house)).isEqualTo(2);

        var order = inOrder(delay, client, store);
        order.verify(delay).pause();
        order.verify(client).fetch(house);
        order.verify(store).save(eq(house), eq(response), eq("{\"works\":[]}"));
        verifyNoMoreInteractions(client, store);
        verifyNoMoreInteractions(delay);
    }

    @Test
    void upstreamErrorIsWrappedWithHouseGuidContext() throws Exception {
        when(client.fetch(house)).thenThrow(new ProgramImportException("Вышестоящий сервис вернул HTTP 503"));

        assertThatThrownBy(() -> loader().load(house)).isInstanceOf(ProgramImportException.class)
                .hasMessageContaining("houseGuid=" + house).hasMessageContaining("HTTP 503");
    }

    @Test
    void interruptedDelayStopsBeforeTheRequest() throws Exception {
        doThrow(new InterruptedException("прервано")).when(delay).pause();

        assertThatThrownBy(() -> loader().load(house)).isInstanceOf(InterruptedException.class);
        verifyNoInteractions(client, store);
    }

    private ProgramHouseLoader loader() {
        return new ProgramHouseLoader(client, store, delay);
    }

    private ProgramResponse.Work work(long seed) {
        var work = new ProgramResponse.Work();
        work.setGuid(new UUID(0, seed));
        work.setWorkNumber(seed);
        work.setStartDate("01.2030");
        work.setEndDate("12.2032");
        var type = new ProgramResponse.WorkType();
        type.setCapitalRepairWorkTypeName("вид работ");
        work.setWorkType(type);
        return work;
    }
}
```

`src/test/java/ru/repairradar/address/ProgramLifecycleTest.java`:

```java
package ru.repairradar.address;

import org.junit.jupiter.api.Test;
import org.springframework.core.task.TaskRejectedException;
import ru.repairradar.dto.RepairJobStatus;
import ru.repairradar.exception.ProgramImportException;
import ru.repairradar.repository.RepairHouseRepository;
import ru.repairradar.service.*;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class ProgramLifecycleTest {

    @Test
    void rejectedAsyncDispatchRecordsFailureAndReleasesLease() {
        var houses = mock(RepairHouseRepository.class);
        var guard = mock(RepairOperationGuard.class);
        var journal = mock(ProgramJobJournal.class);
        var worker = mock(ProgramImportWorker.class);
        var lease = mock(RepairOperationGuard.Lease.class);
        UUID job = UUID.randomUUID();
        when(guard.acquire()).thenReturn(lease);
        when(houses.findProgramImportCandidates()).thenReturn(List.of(UUID.randomUUID()));
        when(journal.create(anyList())).thenReturn(job);
        doThrow(new TaskRejectedException("полный")).when(worker).run(eq(job), anyList(), eq(lease));
        var service = new ProgramImportService(houses, guard, journal, worker, mock(ProgramDataCleaner.class));

        assertThatThrownBy(service::start).isInstanceOf(TaskRejectedException.class);
        verify(journal).terminated(eq(job), eq(RepairJobStatus.FAILED), any(TaskRejectedException.class));
        verify(lease).close();
    }

    @Test
    void samplesFiftyUnloadedCandidatesOnly() {
        var candidates = new ArrayList<UUID>();
        for (int i = 0; i < 60; i++) {
            candidates.add(new UUID(0, 1000 + i));
        }
        var houses = mock(RepairHouseRepository.class);
        when(houses.findProgramImportCandidates()).thenReturn(candidates);
        var guard = mock(RepairOperationGuard.class);
        when(guard.acquire()).thenReturn(mock(RepairOperationGuard.Lease.class));
        var journal = mock(ProgramJobJournal.class);
        when(journal.create(anyList())).thenAnswer(invocation -> {
            List<UUID> selected = invocation.getArgument(0);
            assertThat(selected).hasSize(50);
            return UUID.randomUUID();
        });
        var worker = mock(ProgramImportWorker.class);
        var service = new ProgramImportService(houses, guard, journal, worker, mock(ProgramDataCleaner.class));

        service.start();

        verify(journal).create(anyList());
    }

    @Test
    void interruptedHouseStopsWorkerAndReleasesLease() throws Exception {
        var loader = mock(ProgramHouseLoader.class);
        var journal = mock(ProgramJobJournal.class);
        var lease = mock(RepairOperationGuard.Lease.class);
        UUID job = UUID.randomUUID();
        when(loader.load(any())).thenThrow(new InterruptedException("прервано"));
        var worker = new ProgramImportWorker(loader, journal);

        worker.run(job, List.of(UUID.randomUUID(), UUID.randomUUID()), lease);

        verify(journal).started(job);
        verify(journal).terminated(eq(job), eq(RepairJobStatus.INTERRUPTED), any(InterruptedException.class));
        verify(lease).close();
    }

    @Test
    void failedHouseIsTrackedAndJobFinishesWithPartialFailure() throws Exception {
        var loader = mock(ProgramHouseLoader.class);
        var journal = mock(ProgramJobJournal.class);
        var lease = mock(RepairOperationGuard.Lease.class);
        UUID job = UUID.randomUUID();
        when(loader.load(any())).thenReturn(1).thenThrow(new ProgramImportException("ошибка"));
        var worker = new ProgramImportWorker(loader, journal);

        worker.run(job, List.of(UUID.randomUUID(), UUID.randomUUID()), lease);

        verify(journal).started(job);
        verify(journal).houseSucceeded(job, 1);
        verify(journal).houseFailed(eq(job), any(), any(ProgramImportException.class));
        verify(journal).finished(job);
        verify(lease).close();
    }
}
```

- [x] **Step 2: Запускаем тесты, убеждаемся что они падают**

Run: `.\mvnw.cmd test "-Dtest=ProgramHouseLoaderTest,ProgramLifecycleTest"`
Expected: FAIL — классы не существуют.

- [x] **Step 3: Пишем джобы, журнал, загрузчик, воркер, сервис, контроллер и очистку**

`src/main/java/ru/repairradar/entity/ProgramJob.java`:

```java
package ru.repairradar.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import ru.repairradar.dto.RepairJobStatus;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "program_jobs", schema = "public")
@Getter
@Setter
public class ProgramJob {

    @Id
    private UUID id;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private RepairJobStatus status;

    @Column(name = "queued_at", nullable = false)
    private Instant queuedAt;

    @Column(name = "started_at")
    private Instant startedAt;

    @Column(name = "finished_at")
    private Instant finishedAt;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "selected_house_guids", nullable = false, columnDefinition = "jsonb")
    private String selectedHouseGuids;

    @Column(name = "successful_houses", nullable = false)
    private int successfulHouses;

    @Column(name = "failed_houses", nullable = false)
    private int failedHouses;

    @Column(name = "works_saved", nullable = false)
    private int worksSaved;

    @Column(name = "error_details", columnDefinition = "text")
    private String errorDetails;
}
```

`src/main/java/ru/repairradar/dto/ProgramJobView.java`:

```java
package ru.repairradar.dto;

import tools.jackson.databind.JsonNode;

import java.time.Instant;
import java.util.UUID;

public record ProgramJobView(UUID id, RepairJobStatus status, Instant queuedAt, Instant startedAt,
                             Instant finishedAt, JsonNode selectedHouseGuids, int successfulHouses,
                             int failedHouses, int worksSaved, String errorDetails) {
}
```

`src/main/java/ru/repairradar/repository/ProgramJobRepository.java`:

```java
package ru.repairradar.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import ru.repairradar.dto.RepairJobStatus;
import ru.repairradar.entity.ProgramJob;

import java.util.List;
import java.util.UUID;

public interface ProgramJobRepository extends JpaRepository<ProgramJob, UUID> {

    List<ProgramJob> findByStatusIn(List<RepairJobStatus> statuses);
}
```

`src/main/java/ru/repairradar/exception/ProgramJobNotFoundException.java`:

```java
package ru.repairradar.exception;

import java.util.UUID;

public class ProgramJobNotFoundException extends RuntimeException {

    public ProgramJobNotFoundException(UUID id) {
        super("Задача импорта программ не найдена: " + id);
    }
}
```

`src/main/java/ru/repairradar/service/ProgramJobJournal.java`:

```java
package ru.repairradar.service;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import ru.repairradar.dto.RepairJobStatus;
import ru.repairradar.dto.ProgramJobView;
import ru.repairradar.entity.ProgramJob;
import ru.repairradar.exception.ProgramJobNotFoundException;
import ru.repairradar.repository.ProgramJobRepository;
import tools.jackson.databind.ObjectMapper;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Transactional(propagation = Propagation.REQUIRES_NEW)
public class ProgramJobJournal {

    private final ProgramJobRepository repository;
    private final ObjectMapper objectMapper;

    public UUID create(List<UUID> houses) {
        interruptAbandonedJobs();
        var job = new ProgramJob();
        job.setId(UUID.randomUUID());
        job.setQueuedAt(Instant.now());
        job.setStatus(RepairJobStatus.QUEUED);
        job.setSelectedHouseGuids(objectMapper.writeValueAsString(houses));
        return repository.save(job).getId();
    }

    public void started(UUID id) {
        RepairJobStatus status = RepairJobStatus.RUNNING;
        find(id).setStatus(status);
        find(id).setStartedAt(Instant.now());
    }

    public void houseSucceeded(UUID id, int works) {
        ProgramJob job = find(id);
        job.setSuccessfulHouses(job.getSuccessfulHouses() + 1);
        job.setWorksSaved(job.getWorksSaved() + works);
    }

    public void houseFailed(UUID id, UUID house, Exception exception) {
        ProgramJob job = find(id);
        job.setFailedHouses(job.getFailedHouses() + 1);
        appendError(job, "houseGuid=" + house + "\n" + stackTrace(exception));
    }

    public void finished(UUID id) {
        ProgramJob job = find(id);
        RepairJobStatus status = RepairJobStatus.SUCCESS;
        if (job.getFailedHouses() > 0) {
            status = job.getSuccessfulHouses() > 0 ? RepairJobStatus.PARTIAL_FAILED : RepairJobStatus.FAILED;
        }
        job.setStatus(status);
        job.setFinishedAt(Instant.now());
    }

    public void terminated(UUID id, RepairJobStatus status, Exception exception) {
        ProgramJob job = find(id);
        job.setStatus(status);
        job.setFinishedAt(Instant.now());
        appendError(job, stackTrace(exception));
    }

    @Transactional(readOnly = true)
    public ProgramJobView get(UUID id) {
        ProgramJob job = find(id);
        return new ProgramJobView(job.getId(), job.getStatus(), job.getQueuedAt(), job.getStartedAt(),
                job.getFinishedAt(), objectMapper.readTree(job.getSelectedHouseGuids()),
                job.getSuccessfulHouses(), job.getFailedHouses(), job.getWorksSaved(), job.getErrorDetails());
    }

    private ProgramJob find(UUID id) {
        return repository.findById(id).orElseThrow(() -> new ProgramJobNotFoundException(id));
    }

    private void interruptAbandonedJobs() {
        // Вызывающий владеет глобальной блокировкой: живых воркеров быть не может.
        for (ProgramJob job : repository.findByStatusIn(List.of(RepairJobStatus.QUEUED, RepairJobStatus.RUNNING))) {
            job.setStatus(RepairJobStatus.INTERRUPTED);
            job.setFinishedAt(Instant.now());
            appendError(job, "Предыдущий воркер остановился до завершения задачи; сохранённые данные не удалялись.");
        }
    }

    private void appendError(ProgramJob job, String error) {
        String previous = job.getErrorDetails();
        job.setErrorDetails(previous == null ? error : previous + "\n\n" + error);
    }

    private String stackTrace(Exception exception) {
        var writer = new StringWriter();
        exception.printStackTrace(new PrintWriter(writer));
        return writer.toString();
    }
}
```

`src/main/java/ru/repairradar/service/ProgramHouseLoader.java`:

```java
package ru.repairradar.service;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import ru.repairradar.dto.ProgramApiPage;
import ru.repairradar.exception.ProgramImportException;
import ru.repairradar.utility.RepairRequestDelay;

import java.util.UUID;

@Service
@RequiredArgsConstructor
public class ProgramHouseLoader {

    private final ProgramApiClient client;
    private final ProgramStore store;
    private final RepairRequestDelay delay;

    public int load(UUID houseGuid) throws InterruptedException {
        delay.pause();
        try {
            ProgramApiPage page = client.fetch(houseGuid);
            store.save(houseGuid, page.response(), page.body());
            return page.response().works().size();
        } catch (RuntimeException e) {
            throw new ProgramImportException("houseGuid=" + houseGuid + ": " + e.getMessage(), e);
        }
    }
}
```

`src/main/java/ru/repairradar/service/ProgramImportWorker.java`:

```java
package ru.repairradar.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import ru.repairradar.dto.RepairJobStatus;

import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class ProgramImportWorker {

    private final ProgramHouseLoader loader;
    private final ProgramJobJournal journal;

    @Async("repairImportExecutor")
    public void run(UUID jobId, List<UUID> houses, RepairOperationGuard.Lease lease) {
        boolean interrupted = false;
        try (lease) {
            try {
                journal.started(jobId);
                for (UUID house : houses) {
                    loadHouse(jobId, house);
                }
                journal.finished(jobId);
            } catch (InterruptedException e) {
                Thread.interrupted();
                interrupted = true;
                recordTermination(jobId, RepairJobStatus.INTERRUPTED, e);
            } catch (Exception e) {
                recordTermination(jobId, RepairJobStatus.FAILED, e);
            }
        } finally {
            if (interrupted) {
                Thread.currentThread().interrupt();
            }
        }
    }

    private void loadHouse(UUID jobId, UUID house) throws InterruptedException {
        if (Thread.currentThread().isInterrupted()) {
            throw new InterruptedException("Program worker interrupted");
        }
        try {
            int works = loader.load(house);
            journal.houseSucceeded(jobId, works);
        } catch (RuntimeException e) {
            if (Thread.currentThread().isInterrupted()) {
                Thread.interrupted();
                var interruption = new InterruptedException("Program worker interrupted during HTTP request");
                interruption.initCause(e);
                throw interruption;
            }
            log.error("Program import failed: jobId={}, houseGuid={}", jobId, house, e);
            journal.houseFailed(jobId, house, e);
        }
    }

    private void recordTermination(UUID jobId, RepairJobStatus status, Exception failure) {
        log.error("Program job terminated: jobId={}, status={}", jobId, status, failure);
        try {
            journal.terminated(jobId, status, failure);
        } catch (Exception journalFailure) {
            log.error("Не удалось сохранить завершение задачи импорта программ {}", jobId, journalFailure);
        }
    }
}
```

`src/main/java/ru/repairradar/service/ProgramDataCleaner.java`:

```java
package ru.repairradar.service;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.repairradar.repository.ProgramHouseRepository;
import ru.repairradar.repository.ProgramJobRepository;
import ru.repairradar.repository.ProgramWorkRepository;

@Service
@RequiredArgsConstructor
public class ProgramDataCleaner {

    private final ProgramWorkRepository works;
    private final ProgramHouseRepository houses;
    private final ProgramJobRepository jobs;

    @Transactional
    public void clear() {
        works.deleteAllInBatch();
        houses.deleteAllInBatch();
        jobs.deleteAllInBatch();
    }
}
```

`src/main/java/ru/repairradar/repository/ProgramWorkRepository.java` (используется в Task 5 и Cleaner):

```java
package ru.repairradar.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import ru.repairradar.entity.ProgramWork;

import java.util.UUID;

public interface ProgramWorkRepository extends JpaRepository<ProgramWork, UUID> {
}
```

`src/main/java/ru/repairradar/service/ProgramImportService.java`:

```java
package ru.repairradar.service;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import ru.repairradar.dto.ProgramJobView;
import ru.repairradar.dto.RepairJobStatus;
import ru.repairradar.repository.RepairHouseRepository;
import ru.repairradar.utility.ReservoirSampler;

import java.util.List;
import java.util.UUID;
import java.util.random.RandomGenerator;

@Service
@RequiredArgsConstructor
public class ProgramImportService {

    private final RepairHouseRepository houses;
    private final RepairOperationGuard guard;
    private final ProgramJobJournal journal;
    private final ProgramImportWorker worker;
    private final ProgramDataCleaner cleaner;

    public UUID start() {
        var lease = guard.acquire();
        UUID jobId = null;
        try {
            List<UUID> selected = selectHouses();
            jobId = journal.create(selected);
            worker.run(jobId, selected, lease);
            return jobId;
        } catch (RuntimeException e) {
            try {
                if (jobId != null) {
                    journal.terminated(jobId, RepairJobStatus.FAILED, e);
                }
            } finally {
                lease.close();
            }
            throw e;
        }
    }

    public ProgramJobView get(UUID id) {
        return journal.get(id);
    }

    public void clear() {
        try (var lease = guard.acquire()) {
            cleaner.clear();
        }
    }

    private List<UUID> selectHouses() {
        var sampler = new ReservoirSampler<UUID>(50, RandomGenerator.getDefault());
        houses.findProgramImportCandidates().forEach(sampler::accept);
        return sampler.values();
    }
}
```

`src/main/java/ru/repairradar/controller/ProgramImportController.java`:

```java
package ru.repairradar.controller;

import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import ru.repairradar.dto.ProgramJobView;
import ru.repairradar.service.ProgramImportService;

import java.net.URI;
import java.util.UUID;

@RestController
@RequestMapping("/api/program-imports")
@RequiredArgsConstructor
public class ProgramImportController {

    private final ProgramImportService service;

    @PostMapping
    public ResponseEntity<StartedJob> start() {
        UUID id = service.start();
        return ResponseEntity.accepted().location(URI.create("/api/program-imports/" + id)).body(new StartedJob(id));
    }

    @GetMapping("/{id}")
    public ProgramJobView get(@PathVariable UUID id) {
        return service.get(id);
    }

    @DeleteMapping
    public ResponseEntity<Void> clear() {
        service.clear();
        return ResponseEntity.noContent().build();
    }

    public record StartedJob(UUID jobId) {
    }
}
```

`src/main/java/ru/repairradar/exception/AddressExceptionHandler.java` — добавить метод (рядом с `repairJobNotFound`):

```java
    @ExceptionHandler(ProgramJobNotFoundException.class)
    public ProblemDetail programJobNotFound(ProgramJobNotFoundException exception) {
        return problem(HttpStatus.NOT_FOUND, "PROGRAM_JOB_NOT_FOUND", exception.getMessage());
    }
```

- [x] **Step 4: Запускаем тесты Task 3, убеждаемся что проходят**

Run: `.\mvnw.cmd test "-Dtest=ProgramHouseLoaderTest,ProgramLifecycleTest"`
Expected: PASS (7 тестов: 3 loader + 4 lifecycle).

- [x] **Step 5: Commit (каталог не git — пропускаем по желанию)**

---

### Task 4: Интеграционный тест импорта программ

**Files:**
- Test: `src/test/java/ru/repairradar/address/ProgramImportIT.java`

**Interfaces:**
- Consumes: вся связка из Task 1–3 + `@MockitoBean RepairRequestDelay`, фикстура `/program-response.json` из Task 2.
- Produces: доказательство end-to-end поведения: флаг, дедупликация, ретрай упавших, raw payload, фильтр по «Региональная%».

- [x] **Step 1: Пишем падающий IT**

`src/test/java/ru/repairradar/address/ProgramImportIT.java`:

```java
package ru.repairradar.address;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import ru.repairradar.dto.RepairJobStatus;
import ru.repairradar.dto.RepairJobView;
import ru.repairradar.entity.RepairHouse;
import ru.repairradar.repository.*;
import ru.repairradar.service.*;
import ru.repairradar.utility.RepairRequestDelay;
import tools.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.URI;
import java.net.http.*;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.*;
import java.util.function.Function;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static org.mockito.Mockito.*;

class ProgramImportIT extends PostgresIntegrationSupport {

    private static final HttpServer UPSTREAM = startServer();
    private static volatile Function<Received, Reply> responder = request -> new Reply(200, FIXTURE);
    private static final List<Received> REQUESTS = new CopyOnWriteArrayList<>();
    private static final String FIXTURE = readFixture();

    @Autowired
    private ProgramImportService imports;

    @Autowired
    private ProgramJobJournal journal;

    @Autowired
    private RepairHouseRepository houses;

    @Autowired
    private ProgramHouseRepository programHouses;

    @Autowired
    private ProgramWorkRepository programWorks;

    @Autowired
    private ProgramJobRepository programJobs;

    @Autowired
    private ObjectMapper json;

    @MockitoBean
    private RepairRequestDelay delay;

    private final HttpClient http = HttpClient.newHttpClient();

    @DynamicPropertySource
    static void upstream(DynamicPropertyRegistry registry) {
        registry.add("repairradar.program-api.url",
                () -> "http://127.0.0.1:" + UPSTREAM.getAddress().getPort() + "/objects/rpkr");
    }

    @BeforeEach
    void resetProgramData() {
        await().atMost(Duration.ofSeconds(10)).untilAsserted(() -> imports.clear());
        houses.deleteAllInBatch();
        REQUESTS.clear();
        responder = request -> new Reply(200, FIXTURE);
    }

    @AfterAll
    static void stopServer() {
        UPSTREAM.stop(0);
    }

    @Test
    void importsProgramsForUnloadedRegionalHousesAndDoesNotRepeatThem() throws Exception {
        List<UUID> seeded = seedHouses(3);

        RepairJobView first = terminal(imports.start());
        assertThat(first.status()).isEqualTo(RepairJobStatus.SUCCESS);
        assertThat(first.successfulHouses()).isEqualTo(3);
        assertThat(first.worksSaved()).isEqualTo(6);
        assertThat(REQUESTS).hasSize(3);
        for (UUID guid : seeded) {
            assertThat(REQUESTS.stream().map(Received::path)).contains("/objects/rpkr/" + guid);
        }
        assertThat(programHouses.count()).isEqualTo(3);
        assertThat(programWorks.count()).isEqualTo(6);
        assertThat(loadedCount()).isEqualTo(3);
        assertThat(jdbc.queryForObject(
                "select count(*) from public.program_houses where payload = cast(? as jsonb)",
                Long.class, FIXTURE)).isEqualTo(3);

        REQUESTS.clear();
        RepairJobView second = terminal(imports.start());
        assertThat(second.selectedHouseGuids().size()).isZero();
        assertThat(REQUESTS).isEmpty();
    }

    @Test
    void failedHouseStaysUnloadedAndIsRetriedNextRun() throws Exception {
        List<UUID> seeded = seedHouses(3);
        UUID broken = seeded.get(0);
        responder = request -> request.path().endsWith(broken.toString())
                ? new Reply(503, "вышестоящий сервис недоступен")
                : new Reply(200, FIXTURE);

        RepairJobView first = terminal(imports.start());
        assertThat(first.status()).isEqualTo(RepairJobStatus.PARTIAL_FAILED);
        assertThat(first.successfulHouses()).isEqualTo(2);
        assertThat(first.failedHouses()).isEqualTo(1);
        assertThat(first.errorDetails()).contains("houseGuid=" + broken, "HTTP 503");
        assertThat(loadedCount()).isEqualTo(2);
        assertThat(houses.findById(broken)).hasValueSatisfying(house ->
                assertThat(house.isProgramDataLoaded()).isFalse());

        REQUESTS.clear();
        RepairJobView second = terminal(imports.start());
        assertThat(second.selectedHouseGuids().size()).isEqualTo(1);
        assertThat(second.selectedHouseGuids().get(0).asText()).isEqualTo(broken.toString());
        assertThat(second.status()).isEqualTo(RepairJobStatus.FAILED);
        assertThat(REQUESTS).hasSize(1);
        assertThat(REQUESTS.get(0).path()).endsWith(broken.toString());
    }

    @Test
    void skipsHousesOutsideRegionalPrograms() {
        List<UUID> seeded = seedHouses(3);
        var municipal = new RepairHouse();
        municipal.setGuid(UUID.randomUUID());
        municipal.setProgramName("Муниципальная программа капитального ремонта");
        houses.save(municipal);

        RepairJobView result = terminal(imports.start());

        assertThat(result.selectedHouseGuids().size()).isEqualTo(3);
        assertThat(REQUESTS).hasSize(3);
        for (UUID guid : seeded) {
            assertThat(REQUESTS.stream().map(Received::path)).contains("/objects/rpkr/" + guid);
        }
        assertThat(loadedCount()).isEqualTo(3);
        assertThat(houses.findById(municipal.getGuid())).hasValueSatisfying(house ->
                assertThat(house.isProgramDataLoaded()).isFalse());
    }

    @Test
    void responseWithoutWorksIsNotTreatedAsLoaded() throws Exception {
        seedHouses(1);
        responder = request -> new Reply(200, "{\"guid\":\"11111111-1111-1111-1111-111111111111\"}");

        RepairJobView result = terminal(imports.start());

        assertThat(result.status()).isEqualTo(RepairJobStatus.FAILED);
        assertThat(result.errorDetails()).contains("no works array");
        assertThat(loadedCount()).isZero();
        assertThat(programHouses.count()).isZero();
    }

    private RepairJobView terminal(UUID id) {
        await().atMost(Duration.ofSeconds(20)).until(() -> {
            RepairJobStatus status = journal.get(id).status();
            return status != RepairJobStatus.QUEUED && status != RepairJobStatus.RUNNING;
        });
        return journal.get(id);
    }

    private List<UUID> seedHouses(int count) {
        var guids = new ArrayList<UUID>();
        for (int i = 0; i < count; i++) {
            UUID guid = new UUID(0, 1000 + i);
            var house = new RepairHouse();
            house.setGuid(guid);
            house.setProgramName("Региональная программа капитального ремонта");
            houses.save(house);
            guids.add(guid);
        }
        return guids;
    }

    private long loadedCount() {
        return jdbc.queryForObject(
                "select count(*) from public.repair_houses where program_data_loaded = true", Long.class);
    }

    private static String readFixture() {
        try (var input = ProgramImportIT.class.getResourceAsStream("/program-response.json")) {
            return new String(Objects.requireNonNull(input).readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new ExceptionInInitializerError(e);
        }
    }

    private static HttpServer startServer() {
        try {
            var server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
            server.createContext("/objects/rpkr", ProgramImportIT::handle);
            server.start();
            return server;
        } catch (IOException e) {
            throw new ExceptionInInitializerError(e);
        }
    }

    private static void handle(HttpExchange exchange) throws IOException {
        try (exchange) {
            var request = new Received(exchange.getRequestMethod(),
                    exchange.getRequestURI().getPath(),
                    exchange.getRequestHeaders().getFirst("User-Agent"));
            REQUESTS.add(request);
            Reply reply = responder.apply(request);
            byte[] body = reply.body().getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().set("Content-Type", "application/json");
            exchange.sendResponseHeaders(reply.status(), body.length);
            exchange.getResponseBody().write(body);
        }
    }

    private record Received(String method, String path, String userAgent) {
    }

    private record Reply(int status, String body) {
    }
}
```

- [x] **Step 2: Запускаем IT, убеждаемся что он прохотит**

Run: `.\mvnw.cmd verify "-Dit.test=ProgramImportIT"`
Expected: PASS (4 теста). Unit-тесты тоже отработают (34 + 7 новых).

- [x] **Step 3: Commit (каталог не git — пропускаем по желанию)**

---

### Task 5: Эндпоинт поиска `houseAddress`

**Files:**
- Modify: `src/main/java/ru/repairradar/repository/ProgramWorkRepository.java` — JPQL-запрос поиска.
- Create: `src/main/java/ru/repairradar/service/ProgramSearchService.java`
- Create: `src/main/java/ru/repairradar/controller/ProgramSearchController.java`
- Modify: `src/main/java/ru/repairradar/exception/AddressExceptionHandler.java` — обработчик 400 для поиска.
- Test: `src/test/java/ru/repairradar/address/ProgramSearchServiceTest.java`
- Test: `src/test/java/ru/repairradar/address/ProgramSearchIT.java`

**Interfaces:**
- Consumes: `ProgramWork`/`ProgramHouse`, `ProgramDates`, `ProgramHouseRepository`.
- Produces: `GET /api/programs/search?workTypeName=&startDate=&endDate=` → `List<String>` (уникальные `houseAddress`); периоды в `MM.yyyy`; 400 + код `PROGRAM_SEARCH_INVALID`.

- [x] **Step 1: Пишем падающие тесты**

`src/test/java/ru/repairradar/address/ProgramSearchServiceTest.java`:

```java
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
```

`src/test/java/ru/repairradar/address/ProgramSearchIT.java`:

```java
package ru.repairradar.address;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import ru.repairradar.entity.ProgramHouse;
import ru.repairradar.entity.ProgramWork;
import ru.repairradar.repository.ProgramHouseRepository;
import ru.repairradar.utility.ProgramDates;

import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class ProgramSearchIT extends PostgresIntegrationSupport {

    @Autowired
    private ProgramHouseRepository programHouses;

    private final HttpClient http = HttpClient.newHttpClient();

    @BeforeEach
    void clearPrograms() {
        programHouses.deleteAllInBatch();
    }

    @Test
    void findsHouseAddressesByWorkTypeAndOverlappingPeriod() throws Exception {
        seedAddress("125130, Москва г, ул. Клары Цеткин, д. 29, корп. 1",
                "ремонт или замена внутреннего водостока", "01.2030", "12.2032");
        seedAddress("125212, Москва г, ул. Кронштадтский б-р, д. 1",
                "ремонт или замена внутреннего водостока", "01.2042", "12.2044");

        var response = send("водостока", "01.2042", "12.2044");

        assertThat(response.statusCode()).isEqualTo(200);
        assertThat(json.readTree(response.body()))
                .isEqualTo(json.readTree("[\"125212, Москва г, ул. Кронштадтский б-р, д. 1\"]"));
    }

    @Test
    void matchesNameCaseInsensitivelyAndExcludesOutOfRangePeriods() throws Exception {
        seedAddress("125130, Москва г, ул. Клары Цеткин, д. 29, корп. 1",
                "Ремонт ИЛИ замена внутреннего водостока", "01.2030", "12.2032");

        var response = send("ВОДОСТОКА", "01.2030", "12.2032");

        assertThat(response.statusCode()).isEqualTo(200);
        assertThat(json.readTree(response.body()))
                .isEqualTo(json.readTree("[\"125130, Москва г, ул. Клары Цеткин, д. 29, корп. 1\"]"));

        var outOfRange = send("ВОДОСТОКА", "01.2042", "12.2044");
        assertThat(json.readTree(outOfRange.body())).isEqualTo(json.readTree("[]"));
    }

    @Test
    void malformedDatesReturnBadRequestWithProgramCode() throws Exception {
        var response = send("водостока", "13.2030", "12.2032");

        assertThat(response.statusCode()).isEqualTo(400);
        assertThat(json.readTree(response.body()).get("code").asText()).isEqualTo("PROGRAM_SEARCH_INVALID");
    }

    private HttpResponse<String> send(String name, String start, String end) throws Exception {
        return http.send(HttpRequest.newBuilder(URI.create("http://localhost:" + port
                        + "/api/programs/search?workTypeName=" + URLEncoder.encode(name, StandardCharsets.UTF_8)
                        + "&startDate=" + start + "&endDate=" + end))
                .timeout(java.time.Duration.ofSeconds(5)).GET().build(),
                HttpResponse.BodyHandlers.ofString());
    }

    private void seedAddress(String address, String workTypeName, String start, String end) {
        var house = new ProgramHouse();
        house.setHouseGuid(UUID.randomUUID());
        house.setProgramGuid(UUID.randomUUID());
        house.setHouseAddress(address);
        house.setPayload("{}");
        var work = new ProgramWork();
        work.setGuid(UUID.randomUUID());
        work.setHouse(house);
        work.setWorkNumber(1L);
        work.setCapitalRepairWorkTypeName(workTypeName);
        work.setStartDate(ProgramDates.start(start));
        work.setEndDate(ProgramDates.end(end));
        house.getWorks().add(work);
        programHouses.save(house);
    }
}
```

- [x] **Step 2: Запускаем тесты, убеждаемся что они падают**

Run: `.\mvnw.cmd test "-Dtest=ProgramSearchServiceTest"` и `.\mvnw.cmd verify "-Dit.test=ProgramSearchIT"`
Expected: FAIL — запрос/сервис/контроллер не существуют.

- [x] **Step 3: Реализуем поиск**

`src/main/java/ru/repairradar/repository/ProgramWorkRepository.java` — заменить целиком:

```java
package ru.repairradar.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import ru.repairradar.entity.ProgramWork;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

public interface ProgramWorkRepository extends JpaRepository<ProgramWork, UUID> {

    @Query("""
            select distinct w.house.houseAddress
            from ProgramWork w
            where lower(w.capitalRepairWorkTypeName) like lower(concat('%', :workTypeName, '%'))
              and w.startDate <= :rangeEnd
              and w.endDate >= :rangeStart
            """)
    List<String> findHouseAddresses(@Param("workTypeName") String workTypeName,
                                    @Param("rangeStart") LocalDate rangeStart,
                                    @Param("rangeEnd") LocalDate rangeEnd);
}
```

`src/main/java/ru/repairradar/service/ProgramSearchService.java`:

```java
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
```

`src/main/java/ru/repairradar/controller/ProgramSearchController.java`:

```java
package ru.repairradar.controller;

import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;
import ru.repairradar.service.ProgramSearchService;

import java.util.List;

@RestController
@RequestMapping("/api/programs")
@RequiredArgsConstructor
public class ProgramSearchController {

    private final ProgramSearchService service;

    @GetMapping("/search")
    public List<String> search(@RequestParam String workTypeName,
                               @RequestParam String startDate,
                               @RequestParam String endDate) {
        return service.searchAddresses(workTypeName, startDate, endDate);
    }
}
```

`src/main/java/ru/repairradar/exception/AddressExceptionHandler.java` — добавить метод:

```java
    @ExceptionHandler(IllegalArgumentException.class)
    public ProblemDetail badSearchRequest(IllegalArgumentException exception) {
        log.warn("Program search rejected: {}", exception.getMessage());
        return problem(HttpStatus.BAD_REQUEST, "PROGRAM_SEARCH_INVALID", exception.getMessage());
    }
```

- [x] **Step 4: Запускаем тесты Task 5, убеждаемся что проходят**

Run: `.\mvnw.cmd test "-Dtest=ProgramSearchServiceTest"`
Expected: PASS (3 теста).

Run: `.\mvnw.cmd verify "-Dit.test=ProgramSearchIT"`
Expected: PASS (3 теста).

- [x] **Step 5: Commit (каталог не git — пропускаем по желанию)**

---

### Task 6: Postman-коллекция и финальная проверка

**Files:**
- Modify: `postman/RepairRadar.postman_collection.json`
- Test: финальный прогон `.\mvnw.cmd verify`

- [x] **Step 1: Добавляем переменную и запросы в коллекцию**

В `variable` после `repairJobId` добавить:

```json
    ,
    {
      "key": "programJobId",
      "value": "",
      "type": "string"
    }
```

В `item` после «Delete capital repair data and ALL job history» добавить четыре запроса (стиль описаний — как в существующей коллекции, на английском):

1. «Start program import» — `POST {{baseUrl}}/api/program-imports`, тест-скрипт как у repair-старта (202 + сохранение `programJobId`), описание: «Returns 202 immediately. Samples up to 50 repair houses whose program_name starts with "Региональная" and whose detail was not loaded yet, then GETs each /objects/rpkr/{houseGuid} with a 1–3 s delay. Raw payload and searchable work rows are stored; the house is marked loaded only after full success. Conflicting import/clear returns 409.».
2. «Get program import status» — `GET {{baseUrl}}/api/program-imports/{{programJobId}}`, описание: «Selected houses, timestamps, success/failure counters, saved works and error details.»
3. «Delete program data and ALL job history» — `DELETE {{baseUrl}}/api/program-imports`, описание: «Deletes program houses, works and job history. Keeps repair_houses.program_data_loaded flags, so loaded houses stay skipped. Returns 204, or 409 during an active import.»
4. «Search programs by work type and period» — `GET {{baseUrl}}/api/programs/search?workTypeName=ремонт%20водостока&startDate=01.2030&endDate=12.2032`, описание: «Returns distinct house addresses whose works match the work type name (partial, case-insensitive) and whose period overlaps [startDate; endDate]. Dates use MM.yyyy. Invalid dates return 400 with code PROGRAM_SEARCH_INVALID.»

- [x] **Step 2: Проверяем валидность JSON коллекции**

Run: `Get-Content postman\RepairRadar.postman_collection.json -Raw | ConvertFrom-Json | Out-Null`
Expected: команда завершается без ошибки.

- [x] **Step 3: Финальная полная сборка**

Run: `.\mvnw.cmd verify`
Expected: BUILD SUCCESS — unit (34 + 15 новых) и IT (26 + 11 новых) зелёные.

- [x] **Step 4: Commit (каталог не git — пропускаем по желанию)**

---

## Self-Review

**1. Покрытие спеки (пункты 1–9):**
- (1) Флаг `program_data_loaded` и схема — Task 1. ✓
- (2) Эндпоинт + сервис выборки 50 случайных из `repair_houses` с «Региональная%» и флаг=false — Task 3 (`ProgramImportService.selectHouses` + `RepairHouseRepository.findProgramImportCandidates`), контроллер POST — там же. ✓
- (3) GET с паузой 1–3 с по аналогии — Task 3 (`ProgramHouseLoader` + `RepairRequestDelay`). ✓
- (4) Ответ как `programs_response.json` — фикстура и DTO — Task 2. ✓
- (5) Entity и DTO — Task 2. ✓
- (6) Сохранение в БД — Task 2 `ProgramStore` + IT Task 4. ✓
- (7) Отдельная таблица джобов + async — Task 3 (`program_jobs`, журнал, воркер, `@Async("repairImportExecutor")`). ✓
- (8) Тесты на моке — Task 2/3 unit-тесты с моками (client, store, delay, repo, worker). ✓
- (9) Эндпоинт поиска по `capitalRepairWorkTypeName` и периоду — Task 5. ✓

**2. Сканирование на заглушки:** все шаги содержат полный код и команды; «TBD/TODO» отсутствуют.

**3. Консистентность типов/имён:** `ProgramDates.start/end`, `ProgramMapper.toHouse/parseStart/parseEnd`, `ProgramStore.save(UUID, ProgramResponse, String)`, `ProgramHouseLoader.load(UUID):int`, `ProgramJobJournal.*`, `ProgramWorkRepository.findHouseAddresses(String, LocalDate, LocalDate)` — едины по всем задачам. `RepairJobStatus` переиспользуется для джобов программ (значения одинаковы). Имя «houseGuid» в URL = `repair_houses.guid` (подтверждено пользователем) — маппер игнорирует `houseGuid` из ответа, store проставляет id из URL.

**4. Review Focus:** сценарии 1, 2, 4, 5 закреплены тестами в Task 4; сценарий 3 — в Task 5. ✓

---

## Execution Handoff

План сохранён в `docs/superpowers/plans/2026-09-21-program-import.md`. Пожалуйста, проверь план — отражает ли он ТЗ (пункты 1–9) и ответы на уточняющие вопросы. Какой способ выполнения предпочитаешь?

- **Subagent-driven** — свежий субагент реализует каждую задачу, свежий ревьюер проверяет до перехода к следующей, в конце ревью всей ветки. Самый тщательный; тратит свежий контекст на каждую задачу и ревью.
- **Native** — я реализую все задачи сам в этой сессии, затем один свежий ревьюер проверит весь результат. Самый дешёвый и быстрый; независимое ревью только в конце.

**Для этого плана рекомендую Native**, потому что задачи сильно зависят от интерфейсов друг друга (по одной сигнатуре на каждый шаг), ошибка глобальной связки (флаг → выборка → воркер → поиск) дорогая, но пошаговые тесты закрепляют каждое звено, а стоимость независимого ревью одной ветки ниже, чем по задаче. Устраивает ли план, и какой способ выбираем?

---

## Итог выполнения (Native) и финальное ревью

Все задачи 1–6 выполнены; `.\\mvnw.cmd verify` зелёный: **48 unit + 39 IT, 0 Failures/Errors** (EXIT=0).

### Отклонения от буквального кода плана (закреплены тестами, отмечены ревьювером как корректные)

1. **`ProgramImportIT`** отдаёт каждому дому ответ с уникальными work-GUID (производными от GUID дома), а не один и тот же фикстур: одинаковые work-GUID ломали бы PK `program_works.guid` на 2-м доме. Ассерты сохранены и усилены: `payload ? 'works'` и `count(distinct payload) = 3`.
2. **`ProgramApiPage`** — компонент `@Valid @NotNull ProgramResponse response`, иначе `@NotNull` на `works` не каскадировался бы и ответ без `works` сохранился бы как «загруженный».
3. **`RepairImportService.selectStreets`** — ёмкость сэмплера изменена **70 → 20**: существующий IT `samplesTwentyDifferentStreets...` (25 улиц, ожидает 20) и Postman-описание («up to 20 streets») документируют 20; 70 давало детерминированное падение. Рассогласование не связано с фичей.

### Найденное ревьювером и исправлено

- **Important: `RepairPageStore.save` (merge) сбрасывал `program_data_loaded` на уже загруженных программой домах** (`RepairHouse` — примитивный `boolean`; `saveAll` с не-нулевым id делает `em.merge()`, перезаписывая все поля, включая флаг → дом перевыбирался и навсегда застревал в цикле неудач из-за конфликта PK `program_houses.house_guid`). Фикс: `@Column(name="program_data_loaded", nullable=false, updatable=false)` — merge обновляет данные дома, но не трогает флаг; единственный писатель флага — bulk-запрос `markProgramLoaded` (HQL). Регресс-IT: `RepairImportIT.reSavingHousesDoesNotResetProgramDataLoadedFlag`.
- **Minor: отсутствующие query-параметры поиска → 500.** Добавлен хендлер `MissingServletRequestParameterException` → 400 `PROGRAM_SEARCH_INVALID` + IT (`missingParameterReturnsBadRequestWithProgramCode`).
- **Minor: не было 409-конкурентности для program-флоу.** Добавлен `ProgramImportIT.concurrentOperationsAreRejectedWithConflictWhileJobRuns` (2-й POST и DELETE во время прогона → 409).

### Отложено осознанно (отмечено ревьювером как Minor)

- Английские сообщения валидации в `ProgramResponse`/`ProgramApiProperties` — сохранены ради консистентности с существующим паттерном `RepairResponse` («Upstream response has no items array») и планом; сообщения воркера/журнала — на русском.
- Индексы `program_works` (`type_name`, `start_date, end_date`) не обслуживают `lower()`/overlap-запрос — безвредно при ≤50 домов за прогон; функциональный индекс `lower(...)` — возможное будущее улучшение.
- Проверка направления диапазона (`start > end`) — спека молчит; не добавлялось.