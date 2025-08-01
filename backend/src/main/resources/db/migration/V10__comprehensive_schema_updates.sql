-- V10: Comprehensive Schema Updates
-- This migration combines all schema changes from the development cycle
-- including challenge cleanup, item progression system, fishing rod system,
-- and various column type fixes for production deployment

-- =============================================================================
-- 1. DROP LEGACY TABLES (from original V9)
-- =============================================================================

-- Drop the pending_role_selections table as it's no longer needed
DROP TABLE IF EXISTS public.pending_role_selections;

-- =============================================================================
-- 2. ITEM PROGRESSION SYSTEM
-- =============================================================================

-- Add item progression columns to item_instances
ALTER TABLE public.item_instances 
ADD COLUMN IF NOT EXISTS durability INTEGER,
ADD COLUMN IF NOT EXISTS experience BIGINT,
ADD COLUMN IF NOT EXISTS level INTEGER,
ADD COLUMN IF NOT EXISTS max_durability INTEGER,
ADD COLUMN IF NOT EXISTS repair_count INTEGER DEFAULT 0;

-- Add fishing rod equipment system to item_instances
ALTER TABLE public.item_instances 
ADD COLUMN IF NOT EXISTS equipped_rod_shaft_id UUID,
ADD COLUMN IF NOT EXISTS equipped_reel_id UUID,
ADD COLUMN IF NOT EXISTS equipped_fishing_line_id UUID,
ADD COLUMN IF NOT EXISTS equipped_hook_id UUID,
ADD COLUMN IF NOT EXISTS equipped_grip_id UUID;

-- Add foreign key constraints for fishing rod equipment (self-referencing)
ALTER TABLE public.item_instances 
ADD CONSTRAINT fk_item_instances_equipped_rod_shaft 
    FOREIGN KEY (equipped_rod_shaft_id) REFERENCES public.item_instances(id),
ADD CONSTRAINT fk_item_instances_equipped_reel 
    FOREIGN KEY (equipped_reel_id) REFERENCES public.item_instances(id),
ADD CONSTRAINT fk_item_instances_equipped_fishing_line 
    FOREIGN KEY (equipped_fishing_line_id) REFERENCES public.item_instances(id),
ADD CONSTRAINT fk_item_instances_equipped_hook 
    FOREIGN KEY (equipped_hook_id) REFERENCES public.item_instances(id),
ADD CONSTRAINT fk_item_instances_equipped_grip 
    FOREIGN KEY (equipped_grip_id) REFERENCES public.item_instances(id);

-- Add unique constraints to prevent multiple rods having the same equipped part
ALTER TABLE public.item_instances 
ADD CONSTRAINT uk_item_instances_equipped_rod_shaft 
    UNIQUE (equipped_rod_shaft_id),
ADD CONSTRAINT uk_item_instances_equipped_reel 
    UNIQUE (equipped_reel_id),
ADD CONSTRAINT uk_item_instances_equipped_fishing_line 
    UNIQUE (equipped_fishing_line_id),
ADD CONSTRAINT uk_item_instances_equipped_hook 
    UNIQUE (equipped_hook_id),
ADD CONSTRAINT uk_item_instances_equipped_grip 
    UNIQUE (equipped_grip_id);

-- =============================================================================
-- 3. SHOP SYSTEM ENHANCEMENTS
-- =============================================================================

-- Add advanced shop item features
ALTER TABLE public.shop_items 
ADD COLUMN IF NOT EXISTS max_repairs INTEGER,
ADD COLUMN IF NOT EXISTS durability_increase INTEGER,
ADD COLUMN IF NOT EXISTS bonus_loot_chance DOUBLE PRECISION,
ADD COLUMN IF NOT EXISTS rarity_chance_increase DOUBLE PRECISION,
ADD COLUMN IF NOT EXISTS multiplier_increase DOUBLE PRECISION,
ADD COLUMN IF NOT EXISTS negation_chance DOUBLE PRECISION,
ADD COLUMN IF NOT EXISTS fishing_rod_part_type VARCHAR(255),
ADD COLUMN IF NOT EXISTS max_durability INTEGER;

-- Add check constraints for percentage-based columns
ALTER TABLE public.shop_items 
ADD CONSTRAINT chk_shop_items_bonus_loot_chance 
    CHECK (bonus_loot_chance IS NULL OR (bonus_loot_chance >= 0.0 AND bonus_loot_chance <= 100.0)),
ADD CONSTRAINT chk_shop_items_rarity_chance_increase 
    CHECK (rarity_chance_increase IS NULL OR (rarity_chance_increase >= 0.0 AND rarity_chance_increase <= 100.0)),
ADD CONSTRAINT chk_shop_items_multiplier_increase 
    CHECK (multiplier_increase IS NULL OR (multiplier_increase >= 0.0)),
ADD CONSTRAINT chk_shop_items_negation_chance 
    CHECK (negation_chance IS NULL OR (negation_chance >= 0.0 AND negation_chance <= 100.0));

-- =============================================================================
-- 4. USER FISHING ROD EQUIPMENT
-- =============================================================================

-- Add equipped fishing rod to users table
ALTER TABLE public.users 
ADD COLUMN IF NOT EXISTS equipped_fishing_rod_instance_id UUID;

-- Add foreign key constraint to item_instances table
ALTER TABLE public.users 
ADD CONSTRAINT fk_users_equipped_fishing_rod_instance 
    FOREIGN KEY (equipped_fishing_rod_instance_id) REFERENCES public.item_instances(id);

-- =============================================================================
-- 5. DISCORD BOT SETTINGS UPDATES
-- =============================================================================

-- Add part drop settings for fishing part drops
ALTER TABLE public.discord_bot_settings 
ADD COLUMN IF NOT EXISTS part_drop_enabled BOOLEAN DEFAULT false,
ADD COLUMN IF NOT EXISTS part_drop_chance DOUBLE PRECISION DEFAULT 5.0,
ADD COLUMN IF NOT EXISTS part_drop_channel_id VARCHAR(255);

-- =============================================================================
-- 6. COLUMN TYPE FIXES
-- =============================================================================

-- Fix drop rate column types from INTEGER to proper floating point types
ALTER TABLE public.case_items 
ALTER COLUMN drop_rate TYPE NUMERIC(10,4);

ALTER TABLE public.roll_audits 
ALTER COLUMN drop_rate TYPE DOUBLE PRECISION,
ALTER COLUMN total_drop_rates TYPE DOUBLE PRECISION;

-- =============================================================================
-- 7. COMMENTS FOR DOCUMENTATION
-- =============================================================================

-- Item progression system comments
COMMENT ON COLUMN public.item_instances.durability IS 'Current durability value, typically used for equipment degradation mechanics';
COMMENT ON COLUMN public.item_instances.experience IS 'Item experience points as BIGINT for large values, typically used for leveling up equipment/weapons';
COMMENT ON COLUMN public.item_instances.level IS 'Item level for equipment progression and stat scaling';
COMMENT ON COLUMN public.item_instances.max_durability IS 'Maximum durability value for the item, used for repair mechanics and determining item condition';
COMMENT ON COLUMN public.item_instances.repair_count IS 'Number of times this item has been repaired, used for tracking item history and repair limits';

-- Fishing rod equipment comments
COMMENT ON COLUMN public.item_instances.equipped_rod_shaft_id IS 'ID of the rod shaft component equipped to this fishing rod';
COMMENT ON COLUMN public.item_instances.equipped_reel_id IS 'ID of the reel component equipped to this fishing rod';
COMMENT ON COLUMN public.item_instances.equipped_fishing_line_id IS 'ID of the fishing line component equipped to this fishing rod';
COMMENT ON COLUMN public.item_instances.equipped_hook_id IS 'ID of the hook component equipped to this fishing rod';
COMMENT ON COLUMN public.item_instances.equipped_grip_id IS 'ID of the grip component equipped to this fishing rod';

-- Shop system comments
COMMENT ON COLUMN public.shop_items.max_repairs IS 'Maximum number of times this item can be repaired before breaking permanently';
COMMENT ON COLUMN public.shop_items.durability_increase IS 'Amount of durability this item provides when used as a repair material';
COMMENT ON COLUMN public.shop_items.bonus_loot_chance IS 'Percentage chance for bonus loot when using this item (0-100)';
COMMENT ON COLUMN public.shop_items.rarity_chance_increase IS 'Percentage increase in rare item drop chance when using this item (0-100)';
COMMENT ON COLUMN public.shop_items.multiplier_increase IS 'Multiplier increase provided by this item (can be greater than 100%)';
COMMENT ON COLUMN public.shop_items.negation_chance IS 'Percentage chance to negate negative effects when using this item (0-100)';
COMMENT ON COLUMN public.shop_items.fishing_rod_part_type IS 'Type of fishing rod component (SHAFT, REEL, FISHING_LINE, HOOK, GRIP) if this shop item is a fishing rod part';
COMMENT ON COLUMN public.shop_items.max_durability IS 'Maximum durability value that items will have when purchased from the shop, used for equipment durability system';

-- User equipment comments
COMMENT ON COLUMN public.users.equipped_fishing_rod_instance_id IS 'Currently equipped fishing rod instance ID - references item_instances table for the users active fishing rod';

-- Discord bot settings comments
COMMENT ON COLUMN public.discord_bot_settings.part_drop_enabled IS 'Whether fishing rod part drops are enabled globally (default false)';
COMMENT ON COLUMN public.discord_bot_settings.part_drop_chance IS 'Percentage chance for fishing rod parts to drop during fishing activities (default 5.0%)';
COMMENT ON COLUMN public.discord_bot_settings.part_drop_channel_id IS 'Discord channel ID where fishing rod part drops are announced';

-- Drop rate comments
COMMENT ON COLUMN public.case_items.drop_rate IS 'Drop rate as a decimal value with precision (10,4) for accurate probability calculations';
COMMENT ON COLUMN public.roll_audits.drop_rate IS 'Drop rate as a double precision floating point value for accurate probability calculations in roll audits';
COMMENT ON COLUMN public.roll_audits.total_drop_rates IS 'Total drop rates as a double precision floating point value for accurate aggregate probability calculations in roll audits'; 