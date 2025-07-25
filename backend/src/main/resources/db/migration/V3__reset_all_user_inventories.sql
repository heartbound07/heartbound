-- V3__reset_all_user_inventories.sql
-- This script performs a complete wipe of all user inventories and equipped items
-- to establish a clean slate after the V2 inventory system migration.
-- It is designed to be a one-time operation.

-- Use a transaction to ensure all operations succeed or fail together.
BEGIN;

-- Step 1: Delete all existing item instances from the new inventory system.
-- This effectively removes all items from every user's inventory.
TRUNCATE TABLE public.item_instances RESTART IDENTITY;

-- Step 2: Unequip all items for all users by setting the equipped item columns to NULL.
-- This ensures no user has any lingering equipped items after the inventory wipe.
UPDATE public.users
SET
    equipped_user_color_id = NULL,
    equipped_listing_id = NULL,
    equipped_accent_id = NULL,
    equipped_badge_id = NULL,
    equipped_fishing_rod_id = NULL;

-- Commit the transaction to apply all changes.
COMMIT; 