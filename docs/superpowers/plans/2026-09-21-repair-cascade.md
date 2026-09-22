# Repair graph persistence implementation plan

**Goal:** Map validated repair responses to house entity graphs and persist roots with JPA cascades.

**Constraints:** Preserve UUID identity, shared groups, nullable programType GUID, page atomicity and existing schema. Do not delete absent works or shared groups.

- [ ] Add Bean Validation dependency and nested DTO constraints, validating client results and store arguments through Spring proxies.
- [ ] Map house -> works with OneToMany and each work -> owner/group with ManyToOne; cascade PERSIST/MERGE only down to works/groups.
- [ ] Use a per-page MapStruct context to reuse group instances by GUID; attach owner backreferences after mapping.
- [ ] Map the entire response before persistence; save house roots via one saveAll call. Keep page snapshot and progress in the same transaction.
- [ ] Verify null nested GUIDs, optional programType GUID, repeated import, shared groups, missing works preservation and SQL rollback using local mocks/PostgreSQL.

No SQL migration is needed: associations use the existing owner_guid and work_group_guid foreign keys.
