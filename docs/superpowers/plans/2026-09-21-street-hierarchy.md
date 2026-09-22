# Street Hierarchy Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [x]`) syntax for tracking.

**Goal:** Distinguish same-named Moscow streets by their GAR ancestors and enrich previously imported rows.

**Architecture:** Preserve GUID identity and city Москва. A formatter builds street-first full addresses, nearest locality labels and root-first OBJECTID paths from validated administrative chains. Transactional conditional upserts refresh existing streets and report inserted/updated counts.

**Tech Stack:** Java 21, Spring Boot 4, StAX, MapStruct, Spring Data JPA, PostgreSQL 18.6.

**Spec:** User examples of Садовая in different settlements; change schema and importer to preserve location context. Earlier user requirement: city always Москва and import all streets in one call.

## Global Constraints

- GUID remains the primary key; neither name nor full address is unique.
- Keep all labels present in XML; do not invent missing administrative districts.
- Existing rows require backfill through import, without deletes.
- House import behavior remains unchanged.

## Review Focus

- Same street name in different localities must produce separate rows and full addresses.
- Missing locality must remain null, while full address still includes Moscow.
- Nested settlement/territory ancestors must remain in the full address and path.
- Reimport must enrich legacy rows and update changed ancestors; unchanged rows count as neither inserted nor updated.
- Persistence errors must roll back updates as well as inserts.

### Task 1: Read and format street context

**Files:** `src/main/java/ru/repairradar/dto/StreetRow.java`, `utility/StreetFormatter.java`, `service/GarStreetReader.java`; `src/test/java/ru/repairradar/gar/GarStreetReaderTest.java`.

**Interfaces:** `StreetRow(UUID streetGuid, long streetObjectId, String name, String city, String fullAddress, String locality, String hierarchyPath)`; `StreetFormatter.toRow(List<GarObject> chain, String city)`.

- [x] Test two Садовая streets with different locality GUIDs, preserving both paths.
- [x] Format all ancestors in reverse order with `GarObject.label()` and comma separation; select nearest level 5/6 ancestor for locality.
- [x] Test direct Moscow streets and nested territory ancestors.

### Task 2: Migrate and refresh persisted streets

**Files:** `docker/postgres/init/001-addresses.sql`, `docker/postgres/migrations/003-street-hierarchy.sql`; `entity/Street.java`, `repository/StreetRepository.java`, `service/StreetStore.java`, `dto/StreetImportResult.java`; service and integration tests, README and Postman.

**Interfaces:** `StreetImportResult(int inserted, int updated, long total)`; `StreetRepository.upsert(Street)` returns affected row count, zero for unchanged rows; GUID projection distinguishes insert/update under the existing table lock.

- [x] Add nullable text columns full_address, locality, hierarchy_path; legacy rows stay null until reimport rather than receiving invented context.
- [x] Extend MapStruct mapping through matching property names.
- [x] Upsert on GUID, updating only when imported values are distinct; count inserts/updates separately.
- [x] Test >1000 import, duplicate names, legacy backfill, changed context, no-op reimport and rollback of both updates/inserts.
- [x] Run Maven verify with Docker; apply additive migration and reimport real XML; inspect Садовая examples and confirm no missing full addresses.
- [x] Document schema, counters and migration order; stop the temporary validation application.

Execution continues in this session under the user's implementation request.

## Validation results

- Maven verify: BUILD SUCCESS; 28 unit tests and 20 integration tests, no failures/errors/skips.
- Migration 003 applied to existing local Compose database.
- Real XML import returned inserted=0, updated=5766, total=5766.
- SQL confirmed 5766 unique GUIDs and zero rows missing full_address or hierarchy_path.
- Checked all five requested Sadovaya examples by OBJECTID; full addresses and locality values differ correctly.
- Source administrative paths for these examples do not contain an administrative okrug; no extra labels were invented.
- Temporary validation application stopped.
