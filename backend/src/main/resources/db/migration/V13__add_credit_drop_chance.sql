-- V13: Add configurable credit drop chance to discord_bot_settings table
-- This migration adds support for configurable credit drop probability

ALTER TABLE public.discord_bot_settings
ADD COLUMN IF NOT EXISTS credit_drop_chance DOUBLE PRECISION DEFAULT 0.05 NOT NULL;

-- Add comment for documentation
COMMENT ON COLUMN public.discord_bot_settings.credit_drop_chance IS 'Probability (0.0-1.0) of a credit drop occurring each minute when credit drops are enabled'; 