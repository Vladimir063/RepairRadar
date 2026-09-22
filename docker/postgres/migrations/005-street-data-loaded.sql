BEGIN;

-- Флаг: ремонтные данные по улице успешно загружены из внешнего API.
-- Существующие строки считаются незагруженными, повторный импорт улиц флаг не сбрасывает.
ALTER TABLE public.streets
    ADD COLUMN IF NOT EXISTS repair_data_loaded boolean NOT NULL DEFAULT false;

COMMIT;