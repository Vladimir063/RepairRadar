BEGIN;

-- Source properties are nullable except GUID primary keys.
CREATE TABLE IF NOT EXISTS public.repair_work_groups (
    guid uuid PRIMARY KEY,
    code text,
    root_entity_guid uuid,
    actual boolean,
    last_update_date text,
    create_date text,
    name text
);

CREATE TABLE IF NOT EXISTS public.repair_houses (
    guid uuid PRIMARY KEY,
    root_guid uuid,
    last_update_unix_time bigint,
    last_update_date text,
    create_date text,
    read_only boolean,
    active boolean,
    status text,
    program_guid uuid,
    program_name text,
    start_date date,
    end_date date,
    works_number integer,
    program_type_guid uuid,
    program_type_root_guid uuid,
    program_type_last_update_unix_time bigint,
    program_type_last_update_date text,
    program_type_create_date text,
    program_type_read_only boolean,
    program_type_active boolean,
    program_type_code text
);

CREATE TABLE IF NOT EXISTS public.repair_regional_works (
    guid uuid PRIMARY KEY,
    root_guid uuid,
    last_update_unix_time bigint,
    last_update_date text,
    create_date text,
    read_only boolean,
    active boolean,
    owner_guid uuid NOT NULL REFERENCES public.repair_houses(guid),
    work_number bigint,
    work_type_code text,
    work_group_code text,
    start_date date,
    end_date date,
    fias_house_guid uuid,
    oktmo_code text,
    region_guid uuid,
    work_type_name text,
    work_group_guid uuid REFERENCES public.repair_work_groups(guid)
);

CREATE INDEX IF NOT EXISTS repair_regional_works_owner_idx ON public.repair_regional_works(owner_guid);

CREATE TABLE IF NOT EXISTS public.repair_kpr_works (
    owner_guid uuid NOT NULL REFERENCES public.repair_houses(guid),
    guid uuid PRIMARY KEY,
    last_editing_date bigint,
    from_excel boolean,
    work_number bigint,
    work_type_code text,
    work_group_code text,
    work_type_name text,
    house_guid uuid,
    fias_house_guid uuid,
    house_address text,
    oktmo_code text,
    region_guid uuid,
    end_date date,
    fund_sum numeric,
    subject_sum numeric,
    local_sum numeric,
    owner_sum numeric,
    total_sum numeric,
    specific_cost numeric,
    maximum_cost numeric,
    contracted_sum numeric,
    complete_percent numeric,
    work_group_guid uuid REFERENCES public.repair_work_groups(guid),
    contracts jsonb
);

CREATE INDEX IF NOT EXISTS repair_kpr_works_owner_idx ON public.repair_kpr_works(owner_guid);

CREATE TABLE IF NOT EXISTS public.repair_jobs (
    id uuid PRIMARY KEY,
    status varchar(32) NOT NULL,
    queued_at timestamptz NOT NULL,
    started_at timestamptz,
    finished_at timestamptz,
    selected_street_guids jsonb NOT NULL,
    successful_streets integer NOT NULL DEFAULT 0,
    failed_streets integer NOT NULL DEFAULT 0,
    pages_saved integer NOT NULL DEFAULT 0,
    items_saved bigint NOT NULL DEFAULT 0,
    error_details text
);

CREATE TABLE IF NOT EXISTS public.repair_pages (
    id uuid PRIMARY KEY,
    job_id uuid NOT NULL REFERENCES public.repair_jobs(id),
    street_guid uuid NOT NULL,
    page_index integer NOT NULL,
    source_count bigint,
    response_timestamp text,
    payload jsonb NOT NULL,
    UNIQUE (job_id, street_guid, page_index)
);

COMMIT;
