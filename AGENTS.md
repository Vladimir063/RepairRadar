# AGENTS.md

Follow existing project style and architecture.

* Java 21, Spring Boot 4.
* Use Lombok where appropriate, especially `@RequiredArgsConstructor`.
* Use constructor injection. Avoid unnecessary `@Autowired`.
* Dependencies should normally be `private final`.
* Use packages: `controller`, `service`, `repository`, `entity`, `dto`, `mapper`, `config`, `exception`, `utility`.
* Keep controllers thin; business logic belongs in services.
* Use MapStruct for mappings when applicable.
* For simple Spring Data queries, prefer derived repository methods. Do not write native queries unless actually necessary.
* If a Spring Data query can be expressed by the method name alone, use a derived method - do not write a native query for it.
* Put blank lines between methods.
* Never compress `if`, `try`, `catch`, `finally`, loops, or method bodies into one line.
* Prefer readable production code over compact code.
* Inspect nearby classes and follow existing project conventions.
* Follow SOLID principles, especially Single Responsibility Principle.
* Each class should have one clear responsibility and one reason to change.
* Each method should perform one logical task.
* Avoid large methods that mix validation, transformation, persistence, orchestration, or other unrelated responsibilities.
* Split large or complex methods into small, focused, well-named private methods.
* Prefer readable, cohesive methods over long procedural blocks.
* All logs and errors are in Russian only.


## Repo shape

* Maven Wrapper, no CI. Commands below use PowerShell (`.\mvnw.cmd`; elsewhere `./mvnw`).
* Main code uses flat layer packages under `ru.repairradar`: `controller`, `service`, `repository`, `entity`, `dto`, `mapper`, `config`, `exception`, `utility`. Tests stay in feature packages `ru.repairradar.address` and `ru.repairradar.gar`.
* `export_addresses.py` / `export_xml_addresses.py` are standalone helper scripts, not part of the running app.

## Build and test

* `.\mvnw.cmd test` - unit tests only, no Docker.
* `.\mvnw.cmd verify` - unit + integration tests (`*IT`, failsafe); uses embedded PostgreSQL 18.6 (zonky `embedded-postgres`), no Docker required.
* One unit test: `.\mvnw.cmd test "-Dtest=ReservoirSamplerTest"`.
* One IT: `.\mvnw.cmd verify "-Dit.test=AddressStoreIT"` (unit tests still run, ITs are filtered).
* Quote `-D...` arguments: PowerShell splits unquoted dotted properties like `-Dit.test=...`.

## Run locally

* `Copy-Item .env.example .env`, then `docker compose up -d --wait`, then `.\mvnw.cmd spring-boot:run`.
* Compose reads `.env`; the Java app reads OS environment variables only (DB_URL, DB_USERNAME, DB_PASSWORD, GAR_DIRECTORY, SERVER_PORT).
* `docker/postgres/init/001-addresses.sql` runs only on a fresh empty volume. JPA uses `ddl-auto=validate`, so editing the SQL does not migrate an existing volume - add a manual migration instead.
* `GAR_DIRECTORY` defaults to `./77`, the gitignored GAR XML source dir; import needs valid `AS_ADDR_OBJ_*` / `AS_HOUSES_*` / `AS_ADM_HIERARCHY_*` files there.

## Domain behavior that is easy to get wrong

* Each import call inserts up to 1000 new rows (1000 -> 2000 -> ...), never updates or deletes existing rows, and sets `exhausted=true` when fewer than 1000 were added. Duplicates are detected only by `house_guid`.
* `GarAddressReader` streams the XMLs with StAX (`GarXmlReader`, DTD and external entities disabled) and keeps active Moscow (region 77) houses with a valid ancestor chain and a level-8 street.
* `AddressStore.append` takes a table-wide `LOCK TABLE ... IN EXCLUSIVE MODE`, filters known GUIDs, reservoir-samples 1000, and inserts with `ON CONFLICT (house_guid) DO NOTHING` in one transaction (rollback covers all new rows).
* Fast-fail on concurrent operations is the per-instance `AtomicBoolean` in `AddressImportService` (409); the table lock serializes across application instances.
* API: `POST /api/addresses/import` (no body), `DELETE /api/addresses` (204). Errors are `application/problem+json` with a `code` field: `GAR_SOURCE_INVALID` / `GAR_XML_INVALID` (422), `ADDRESS_OPERATION_IN_PROGRESS` (409), `DATABASE_ERROR` (503).

## Toolchain gotchas

* Lombok and MapStruct processors are declared in `pom.xml` `annotationProcessorPaths`. Keep `lombok-mapstruct-binding` in that list and Lombok before `mapstruct-processor`, or MapStruct will not see Lombok-generated accessors.
* `AddressMapper` (MapStruct, Spring component) maps `AddressRow` -> `Address`; `AddressRepository.insertIfAbsent` takes the entity, not the row DTO.
* `GarProperties` is a record bound via `@ConfigurationPropertiesScan`; constructor binding relies on the `-parameters` flag inherited from the Spring Boot parent - do not drop the compiler plugin config.
* Integration tests: `PostgresIntegrationSupport` starts one shared embedded PostgreSQL per JVM via `io.zonky.test:embedded-postgres` (binaries pinned to 18.6.0 by `embedded-postgres-binaries-bom`), applies `001-addresses.sql` and `004-repair-import.sql` with `ScriptUtils`, then seeds XML with `GarFixture.write(dir, count)` and clears the table before each test. `docker/postgres/init` is declared as a test resource so the scripts resolve from the classpath.
