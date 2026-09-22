# Capital Repair Import Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [x]`) syntax for tracking.

**Goal:** Persist all capital-repair API pages for 20 sampled streets using a tracked asynchronous job.

**Architecture:** Typed DTOs and MapStruct map normalized entities; separate client, pacing, pagination, page store, job journal and lifecycle coordinator. PostgreSQL advisory locking guards the complete operation across processes without an open transaction; REQUIRES_NEW journals errors independently.

**Tech Stack:** Java 21, Spring Boot 4.1.1, RestClient/JDK HTTP client, @Async, Spring Data JPA, MapStruct, PostgreSQL JSONB, JUnit/Testcontainers and local JDK HttpServer.

**Spec:** docs/superpowers/specs/2026-09-21-repair-import.md

## Global Constraints

- Follow AGENTS.md flat layer packages and constructor injection.
- Only guid required in source entities; programType.guid exception explicitly approved.
- No production upstream requests in verification.
- Response fixtures derive from response.json; use two houses and small work arrays.
- All imported domain tables and history are deleted by clear, never source streets/addresses.

## Review Focus

- Pagination: short pages, absent count, empty items, missing items, repeated pages and page ceiling.
- Async start: response arrives while mock HTTP is blocked; duplicate start and clear return 409.
- Persistence: source nulls, repeated GUIDs and updated data, shared work groups, arbitrary contract JSON.
- Errors: HTTP failures/invalid GUID/SQL error roll back one page, preserve older pages and persist diagnostics.
- Lifecycle: queue rejection/interruption releases lock; abandoned jobs become INTERRUPTED on next accepted start.

### Task 1: Schema, DTOs and page persistence

**Files:** docker/postgres/init/004-repair-import.sql; docker/postgres/migrations/004-repair-import.sql; dto/RepairResponse.java; entity/RepairMetadata.java, RepairProgramType.java, RepairHouse.java, RepairRegionalWork.java, RepairKprWork.java, RepairWorkGroup.java, RepairPage.java, RepairJob.java; repository equivalents; mapper/RepairMapper.java; service/RepairPageStore.java. Production Java paths start src/main/java/ru/repairradar/.

**Interfaces:** `RepairResponse(List<House> items, Long count, String responseTimestamp)` with nested typed work DTOs. `RepairPageStore.save(UUID jobId, UUID streetGuid, int pageIndex, RepairResponse response, String body)` commits a page. `RepairMapper` maps every observed property, ignoring only internal owning-house GUIDs and generated page IDs.

- [x] Generate nullable source columns from the observed fields, common metadata as mapped superclass, programType as embedded columns; primary guid columns are UUID NOT NULL. Work owner_guid is an internal FK; API houseGuid remains a separate nullable source field. Monetary values use BigDecimal/numeric, dates with known format LocalDate/date.
- [x] Add repair_jobs, repair_pages (job_id, street_guid, page_index, source_count, response_timestamp, payload JSONB); snapshots and progress are committed with the normalized page.
- [x] Use derived repositories and saveAll for entity updates. MapStruct handles DTO -> entity; page validation rejects absent/invalid actual entity GUIDs.
- [x] Tests: store two houses with shared workGroup, then reimport changed title/contracts, assert counts remain constant and nullable fields update. Invalid second item must roll back first item and progress.

```java
@Transactional
public void save(UUID jobId, UUID streetGuid, int pageIndex, RepairResponse response, String body) {
    // Validate/map all house GUIDs; save groups, houses, regional works, KPR works in FK order.
    // Persist original response JSON and increment this job's page/item counters in the same transaction.
}
```

### Task 2: Client, paging and pacing

**Files:** config/RepairApiProperties.java, config/RepairImportConfig.java; dto/RepairRequest.java; service/RepairApiClient.java, RepairStreetLoader.java; utility/RepairRequestDelay.java; exception/RepairImportException.java; tests under ru.repairradar.address.

**Interfaces:** `RepairApiClient.fetch(UUID streetGuid, int page): RepairApiPage` (parsed response plus original body); `RepairStreetLoader.load(UUID jobId, UUID streetGuid)`; `RepairRequestDelay.pause()` throws InterruptedException.

- [x] Configure one dedicated RestClient with connect/read timeouts and explicit User-Agent/Content-Type. No real API address in the local mock test configuration.
- [x] Build request with immutable regionGuid and itemsPerPage=100; serialize UUID as JSON string.
- [x] Delay before every request with `Thread.sleep(ThreadLocalRandom.current().nextLong(1000, 3001))`; interruption propagates and restores interrupt flag at worker boundary.
- [x] Iterate pageIndex from 1 up to configurable maxPages; terminate only on empty list. Validate items presence, reject a nonempty page adding zero unseen GUIDs; save each nonempty page before advancing.
- [x] Mock tests assert first page=1, following page=2, size=100, correct street/region and exact UA; short pages still advance. Test repeated page, missing items, HTTP error and max-page failure.

### Task 3: Job lifecycle and async controller

**Files:** service/RepairJobJournal.java, RepairOperationGuard.java, RepairImportWorker.java, RepairImportService.java; dto/RepairJobView.java, RepairJobStatus.java; controller/RepairImportController.java; scoped exception advice; API integration tests.

**Interfaces:** journal create/start/streetSucceeded/streetFailed/finish/interrupted methods use REQUIRES_NEW; guard `acquire(): Lease` AutoCloseable; `@Async("repairImportExecutor") worker.run(UUID jobId, List<UUID> streets, Lease lease)`; service start/get/clear.

- [x] Configure executor core=max=1 and queueCapacity=0. Acquire session advisory lock before job creation/clear; release in every rejection/failure/worker-finally path.
- [x] Sample up to 20 distinct street GUIDs via existing ReservoirSampler and GUID projection; commit QUEUED job before invoking worker proxy.
- [x] Worker records RUNNING, processes streets sequentially, continues on street failure, records detailed stack/body with page context, then computes SUCCESS/PARTIAL_FAILED/FAILED. Interruption records INTERRUPTED and stops further HTTP.
- [x] Under exclusive lease mark old QUEUED/RUNNING jobs INTERRUPTED (previous process abandoned them); no automatic upstream retry.
- [x] Return 202 plus Location/jobId; GET returns DTO; DELETE transaction removes both work tables, houses/groups, pages and jobs. Conflicts return problem+json 409; missing job returns 404.
- [x] Test async response using a blocked local server; assert duplicate start and DELETE conflict; release server, poll terminal status, then clear and verify all new tables empty while streets remain.

### Task 4: Integration and documentation

**Files:** src/test/resources/repair-response.json; src/test/java/ru/repairradar/address/RepairImportIT.java and unit tests; README.md; Postman collection; existing PostgresIntegrationSupport init resource.

- [x] Build a small fixture from the original response (two houses, two works each, preserve both work types), retaining the root shape and nullable programType.
- [x] Initialize the additional SQL script in the existing Testcontainers fixture without modifying addresses schema behavior.
- [x] Run `mvn verify` using installed Maven if wrapper cannot access its cache; inspect failures and resolve before applying migration.
- [x] Apply additive 004 migration locally after tests. Do not launch a real import to upstream.
- [x] Document API/status/counters, configuration, per-page transactions, partial failure, history deletion, no retries, crash behavior and one retained advisory-lock connection.

## Execution note

Implementation is already authorized by the user's request and clarified answers. Continue locally without another approval gate; the referenced execution subskills are not installed, so use the current harness directly. Do not create commits in this workspace (it has no Git repository).

## Validation results

- Final mvn verify: BUILD SUCCESS, 34 unit tests and 26 integration tests passed, zero failures/errors/skips.
- Local mock HTTP verified 202 before upstream completion, request headers/body, all-page traversal, 20 unique streets, continued processing after 503 and deletion of all repair data/history.
- PostgreSQL tests verified nullable source data, upserts, arbitrary contracts JSON, rollback of a whole page and independently committed error journal.
- Applied additive migration 004 to local Compose database; all six new tables exist and contain no real upstream data.
- No real upstream requests were made. Source response.json was left unchanged; the reduced test fixture is about 7 KB.
