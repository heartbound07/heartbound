-- V12: Remove deprecated matchmaking queue tables
-- This migration removes database tables that are no longer needed after the removal 
-- of the user-facing matchmaking queue system from the frontend.
-- Users can no longer join queues through the UI, making these tables obsolete.
--
-- =============================================================================
-- 1. DROP MATCHMAKING QUEUE TABLE AND INDEXES
-- =============================================================================
--
-- Drop indexes first to avoid dependency issues
DROP INDEX IF EXISTS public.idx_queue_active_users;
DROP INDEX IF EXISTS public.idx_queue_rank;
DROP INDEX IF EXISTS public.idx_queue_region;
DROP INDEX IF EXISTS public.idx_queue_queued_at;
DROP INDEX IF EXISTS public.idx_queue_in_queue;
DROP INDEX IF EXISTS public.idx_queue_user_id;

-- Drop the main matchmaking queue table
-- This table stored users who were waiting to be matched in the queue system
DROP TABLE IF EXISTS public.match_queue_users;

--
-- =============================================================================
-- 2. MIGRATION COMMENTS
-- =============================================================================
--
-- This migration removes the following deprecated functionality:
-- - match_queue_users table: Stored users waiting in the matchmaking queue
-- - Associated indexes: Performance indexes for queue operations
--
-- The pairing system still exists and functions through:
-- - Admin-created pairings via PairingController
-- - Discord bot commands for manual pairing
-- - Existing pairings table for active and historical matches
--
-- No data migration is needed as queue data was transient by nature. 