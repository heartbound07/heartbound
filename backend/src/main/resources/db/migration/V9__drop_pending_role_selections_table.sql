-- Migration to drop pending_role_selections table
-- This table was used to store role selections for unregistered Discord users
-- The feature has been removed in favor of requiring users to register before selecting roles

-- Drop the table if it exists
DROP TABLE IF EXISTS public.pending_role_selections;

-- Note: The index idx_pending_updated_at will be dropped automatically with the table 