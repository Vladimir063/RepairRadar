BEGIN;

ALTER TABLE public.streets
    ADD COLUMN IF NOT EXISTS full_address text,
    ADD COLUMN IF NOT EXISTS locality text,
    ADD COLUMN IF NOT EXISTS hierarchy_path text;

-- Existing rows remain NULL until the next XML import reconstructs their context.
ALTER TABLE public.streets DROP CONSTRAINT IF EXISTS streets_context_nonempty;
ALTER TABLE public.streets ADD CONSTRAINT streets_context_nonempty CHECK (
    (full_address IS NULL OR btrim(full_address) <> '')
    AND (locality IS NULL OR btrim(locality) <> '')
    AND (hierarchy_path IS NULL OR btrim(hierarchy_path) <> '')
);

COMMIT;
