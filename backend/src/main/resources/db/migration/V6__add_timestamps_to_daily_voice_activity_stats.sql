ALTER TABLE public.daily_voice_activity_stats
ADD COLUMN IF NOT EXISTS created_at TIMESTAMP,
ADD COLUMN IF NOT EXISTS updated_at TIMESTAMP;

-- Set a default value for existing rows to avoid null constraint errors.
-- We'll use the 'date' field to provide a reasonable timestamp.
UPDATE public.daily_voice_activity_stats
SET created_at = date::timestamp, updated_at = date::timestamp
WHERE created_at IS NULL OR updated_at IS NULL;

-- Now that existing rows are populated, enforce the NOT NULL constraint.
ALTER TABLE public.daily_voice_activity_stats
ALTER COLUMN created_at SET NOT NULL,
ALTER COLUMN updated_at SET NOT NULL; 