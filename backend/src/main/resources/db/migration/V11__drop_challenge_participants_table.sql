-- Migration to drop challenge_participants table
-- This table was used to store team challenge message counts and participant data
-- The challenge functionality has been completely removed from the application

-- Drop the table if it exists
DROP TABLE IF EXISTS public.challenge_participants;

-- Note: This table had no foreign key constraints or indexes beyond the primary key,
-- so no additional cleanup is needed 