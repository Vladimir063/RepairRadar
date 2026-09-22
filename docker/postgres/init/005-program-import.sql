BEGIN;

-- Данные программ капитального ремонта: сырой ответ API + поисковые колонки (Task "program import").
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

-- Джобы импорта программ, аналогично repair_jobs.
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