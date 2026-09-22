CREATE TABLE IF NOT EXISTS public.addresses (
    house_guid uuid PRIMARY KEY,
    street_guid uuid NOT NULL,
    full_address text NOT NULL,
    city text NOT NULL,
    street text NOT NULL,
    house_number text NOT NULL,
    additional_number_1 text,
    additional_type_1 integer,
    additional_number_2 text,
    additional_type_2 integer,
    house_type integer,
    street_object_id bigint NOT NULL,
    house_object_id bigint NOT NULL,
    CONSTRAINT addresses_nonempty CHECK (
        btrim(city) <> '' AND btrim(full_address) <> ''
        AND btrim(street) <> '' AND btrim(house_number) <> ''
    )
);

CREATE TABLE IF NOT EXISTS public.streets (
    street_guid uuid PRIMARY KEY,
    street_object_id bigint NOT NULL,
    name text NOT NULL,
    city text NOT NULL,
    full_address text,
    locality text,
    hierarchy_path text,
    repair_data_loaded boolean NOT NULL DEFAULT false,
    CONSTRAINT streets_nonempty CHECK (btrim(name) <> '' AND btrim(city) <> ''),
    CONSTRAINT streets_context_nonempty CHECK (
        (full_address IS NULL OR btrim(full_address) <> '')
        AND (locality IS NULL OR btrim(locality) <> '')
        AND (hierarchy_path IS NULL OR btrim(hierarchy_path) <> '')
    )
);
