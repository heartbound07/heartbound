-- V13: Remove deprecated user role selection fields
-- This migration removes database columns that are no longer needed after the removal 
-- of the user-selectable Age, Rank, Region, and Gender role system.
-- Users can no longer select these roles through the application, making these fields obsolete.
--
-- =============================================================================
-- 1. REMOVE DEPRECATED ROLE FIELDS FROM USERS TABLE
-- =============================================================================
--
-- Remove the deprecated role selection fields from users table
-- These fields stored references to Discord role IDs that users selected
ALTER TABLE public.users
DROP COLUMN IF EXISTS selected_age_role_id,
DROP COLUMN IF EXISTS selected_gender_role_id,
DROP COLUMN IF EXISTS selected_rank_role_id,
DROP COLUMN IF EXISTS selected_region_role_id;

--
-- =============================================================================
-- 2. REMOVE DEPRECATED ROLE SNAPSHOT FIELDS FROM PAIRINGS TABLE
-- =============================================================================
--
-- Remove the deprecated role snapshot fields from pairings table
-- These fields stored snapshots of user role data at the time of pairing creation
ALTER TABLE public.pairings
DROP COLUMN IF EXISTS user1_age,
DROP COLUMN IF EXISTS user1_gender,
DROP COLUMN IF EXISTS user1_region,
DROP COLUMN IF EXISTS user1_rank,
DROP COLUMN IF EXISTS user2_age,
DROP COLUMN IF EXISTS user2_gender,
DROP COLUMN IF EXISTS user2_region,
DROP COLUMN IF EXISTS user2_rank;

--
-- =============================================================================
-- 3. MIGRATION COMMENTS
-- =============================================================================
--
-- This migration removes the following deprecated functionality:
-- - User role selection fields: Age, Gender, Rank, Region role preferences
-- - Pairing role snapshots: Historical role data stored with pairing records
--
-- The role assignment system still exists and functions through:
-- - Discord bot commands for role assignment (/roles command)
-- - Admin verification for high-tier ranks (/verify command)
-- - Role assignment is now handled purely through Discord without database tracking
--
-- No data migration is needed as role preferences are no longer tracked.
-- Users can still be assigned roles through Discord, but selections are not persisted. 