-- V7: Add fishing game settings to discord_bot_settings table
-- This migration adds support for configurable fishing limits and anti-botting features

ALTER TABLE discord_bot_settings 
ADD COLUMN fishing_max_catches INTEGER DEFAULT 300 NOT NULL;

ALTER TABLE discord_bot_settings 
ADD COLUMN fishing_cooldown_hours INTEGER DEFAULT 6 NOT NULL;

ALTER TABLE discord_bot_settings 
ADD COLUMN fishing_limit_warning_threshold DOUBLE PRECISION DEFAULT 0.9 NOT NULL;

ALTER TABLE discord_bot_settings 
ADD COLUMN fishing_penalty_credits INTEGER DEFAULT 50 NOT NULL;

-- Add comments for documentation
COMMENT ON COLUMN discord_bot_settings.fishing_max_catches IS 'Maximum number of fish a user can catch before entering cooldown period';
COMMENT ON COLUMN discord_bot_settings.fishing_cooldown_hours IS 'Number of hours users must wait after reaching fishing limit';
COMMENT ON COLUMN discord_bot_settings.fishing_limit_warning_threshold IS 'Fraction of limit (0.0-1.0) at which to warn users they are approaching the limit';
COMMENT ON COLUMN discord_bot_settings.fishing_penalty_credits IS 'Credits silently deducted when users attempt to fish during cooldown (anti-botting measure)'; 