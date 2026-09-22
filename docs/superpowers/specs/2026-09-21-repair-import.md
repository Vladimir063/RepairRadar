# Capital repair import

## Confirmed requirements

- POST to https://dom.gosuslugi.ru/capital-repair-programs/api/rest/services/programs/public/house-repair-search.
- JSON request: pageIndex starts at 1, itemsPerPage=100, fiasAddress.regionGuid=0c5b2444-70a0-4932-980c-b4dc0d3f02b5, fiasAddress.streetGuid from streets.
- Select up to 20 different random existing streets; sequential requests, random 1000–3000 ms pause before requests, including pagination. Do not cap results at 300.
- User-Agent: PostmanRuntime/7.51.0; Content-Type: application/json.
- Controller returns immediately with job ID; @Async worker, persistent timestamps/status/detailed errors and GET status.
- Continue other streets after a failed street; commit each successful page independently; partial failure is visible.
- Update existing domain objects by guid; all source fields except entity guid are nullable. programType.guid is explicitly nullable and programType is embedded in the parent.
- Preserve contracts as JSONB because all supplied examples are empty arrays.
- DELETE clears imported data AND job history; reject start/clear conflicts with 409.
- No real upstream calls in tests or development verification. Use a reduced fixture from response.json.

## Observed response

response.json contains count=234, items=40, regionalWorks=592, kprWorks=378. Both work types reference workGroup. Top-level metadata is responseTimestamp/count. All 40 programType.guid values are null; code is present. Dates startDate/endDate use dd.MM.yyyy; lastEditingDate is epoch milliseconds. Money is supplied as strings. Preserve unobserved nullable metadata date formats as text.

## Decisions

- Read until explicit empty items, not until a short page or the estimated count. Null/missing items is an error. Fail a street if a nonempty page contains no new house GUIDs; a configurable 10000-page ceiling fails rather than silently truncating.
- Store response metadata and original page JSON in repair_pages; normalize houses, both work types and work groups. Page snapshots preserve optional fields, future additions and per-request provenance. Empty terminating responses need no page row.
- A job holds a PostgreSQL session advisory lock (one connection, no open transaction). Start and clear use the same lock, so conflicts are rejected across application instances. A later lock holder marks abandoned queued/running jobs INTERRUPTED before creating a new job; it cannot affect an active worker.
- Separate page store @Transactional and job journal REQUIRES_NEW. HTTP/pause/worker have no transaction. A failed page rolls back all its entities and progress; failure details commit independently.
- No automatic HTTP retries. Report upstream status/body, street/page and stack trace. Next explicit job may sample the street again.
- Queued tasks are not automatically resumed after a process crash. Previously committed pages survive; the next accepted start reconciles abandoned job status.

## API

- POST /api/repair-imports: 202, Location /api/repair-imports/{id}, body jobId.
- GET /api/repair-imports/{id}: status, queuedAt/startedAt/finishedAt, selectedStreetGuids, successfulStreets, failedStreets, pagesSaved, itemsSaved, errorDetails; 404 if missing.
- DELETE /api/repair-imports: 204, removes domain data, page snapshots and all jobs, preserves streets and addresses; 409 while active.
- States QUEUED, RUNNING, SUCCESS, PARTIAL_FAILED, FAILED, INTERRUPTED. Any failed street with some committed data or successful streets is PARTIAL_FAILED; all failed with no data is FAILED.
