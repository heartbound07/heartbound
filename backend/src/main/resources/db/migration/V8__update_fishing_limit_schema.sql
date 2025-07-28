-- V8: Update schema for randomized fishing limits
-- This migration adapts the database to support the new dynamic fishing limit feature.

-- Step 1: Add the 'current_fishing_limit' column to the 'users' table.
-- This will store the personalized, random limit for each user's fishing session.
ALTER TABLE public.users
ADD COLUMN IF NOT EXISTS current_fishing_limit INTEGER;

COMMENT ON COLUMN public.users.current_fishing_limit IS 'The current randomized fishing limit for the user session.';


-- Step 2: Rename the old 'fishing_max_catches' column in 'discord_bot_settings'.
-- Its name is changed to 'fishing_default_max_catches' to reflect its new role as a fallback.
-- We check if the new column does NOT exist and the old one DOES exist to make this safe to re-run.
DO $$
BEGIN
    IF NOT EXISTS (SELECT FROM information_schema.columns WHERE table_name = 'discord_bot_settings' AND column_name = 'fishing_default_max_catches') THEN
        IF EXISTS (SELECT FROM information_schema.columns WHERE table_name = 'discord_bot_settings' AND column_name = 'fishing_max_catches') THEN
            ALTER TABLE public.discord_bot_settings RENAME COLUMN fishing_max_catches TO fishing_default_max_catches;
        END IF;
    END IF;
END $$;


-- Step 3: Add the new columns for the configurable random fishing range.
ALTER TABLE public.discord_bot_settings
ADD COLUMN IF NOT EXISTS fishing_min_catches INTEGER DEFAULT 500;

ALTER TABLE public.discord_bot_settings
ADD COLUMN IF NOT EXISTS fishing_max_catches INTEGER DEFAULT 1500;


-- Step 4: Update comments for clarity on the new and renamed columns.
COMMENT ON COLUMN public.discord_bot_settings.fishing_default_max_catches IS 'Fallback max catches for users without a generated limit, and for validation.';
COMMENT ON COLUMN public.discord_bot_settings.fishing_min_catches IS 'The minimum number of fish a user can be randomly assigned to catch per session.';
COMMENT ON COLUMN public.discord_bot_settings.fishing_max_catches IS 'The maximum number of fish a user can be randomly assigned to catch per session.'; 