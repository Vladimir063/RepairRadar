# Program work types and OpenAPI Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [x]`) syntax for tracking.

**Goal:** Return unique work type names from program_works and document both search endpoints in Swagger UI.

**Architecture:** ProgramSearchController delegates to ProgramSearchService and ProgramWorkRepository. A scalar JPQL DISTINCT query returns sorted nonempty names without loading entities. Springdoc provides OpenAPI and Swagger UI.

**Tech Stack:** Java 21, Spring Boot 4.1.1, Spring Data JPA, springdoc 3.1.1, embedded PostgreSQL.

**Spec:** User request in this session: add a unique program_works endpoint and Swagger/OpenAPI for it and the existing search.

## Global Constraints

- Follow AGENTS.md; constructor injection and Russian descriptions/errors.
- Keep existing search behavior and database schema.
- Use JPQL for scalar DISTINCT projection; no native SQL.

## Review Focus

- Duplicate names across houses return once.
- Empty table returns [].
- Null and blank names are excluded.
- Names have deterministic alphabetical ordering, exact stored values are preserved.
- OpenAPI contains both GET operations, required search parameters, array schemas; Swagger UI loads.

### Task 1: Unique work types and API documentation

**Files:** Modify pom.xml, ProgramSearchController.java, ProgramSearchService.java, ProgramWorkRepository.java, ProgramSearchIT.java, README.md.

**Interfaces:** GET /api/programs/work-types returns List<String>; service.findWorkTypeNames() calls repository.findDistinctWorkTypeNames(). Existing GET /api/programs/search remains available.

- [x] Add integration fixtures with repeated names, null, empty and whitespace names; assert response equals ["Кровля", "Фасад"]. Assert an empty table returns [].
- [x] Implement repository query:
  ```sql
  select distinct w.capitalRepairWorkTypeName from ProgramWork w
  where w.capitalRepairWorkTypeName is not null
    and trim(w.capitalRepairWorkTypeName) <> ''
  order by w.capitalRepairWorkTypeName
  ```
- [x] Add service delegation and GET /work-types controller method returning List<String>.
- [x] Add springdoc-openapi-starter-webmvc-ui 3.1.1 and Russian Operation/Parameter/ApiResponse annotations for both methods.
- [x] Assert /v3/api-docs includes both operations, required query parameters and string array responses; assert /swagger-ui/index.html returns 200.
- [x] Document routes and response example in README.md.
- [x] Run `.\mvnw.cmd verify` and inspect failures and final report.
