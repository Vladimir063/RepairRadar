CREATE TABLE IF NOT EXISTS public.streets (
    street_guid uuid PRIMARY KEY,
    street_object_id bigint NOT NULL,
    name text NOT NULL,
    city text NOT NULL,
    CONSTRAINT streets_nonempty CHECK (btrim(name) <> '' AND btrim(city) <> '')
);
