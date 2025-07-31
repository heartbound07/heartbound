ALTER TABLE discord_bot_settings
ADD COLUMN part_drop_enabled BOOLEAN NOT NULL DEFAULT FALSE,
ADD COLUMN part_drop_channel_id VARCHAR(255),
ADD COLUMN part_drop_chance DOUBLE PRECISION NOT NULL DEFAULT 0.05;

-- Update the existing record to ensure the new columns have default values
UPDATE discord_bot_settings SET 
part_drop_enabled = FALSE, 
part_drop_channel_id = '', 
part_drop_chance = 0.05 
WHERE id = 1; 