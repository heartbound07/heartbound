-- V17: Add user daily shop items persistence table
-- This migration creates a database table to store user daily shop item selections
-- for persistence across application restarts, replacing the volatile in-memory cache.
--
-- Background:
-- Previously, daily shop items were stored in an in-memory Caffeine cache that was
-- lost on application restart, causing users to see different daily selections.
-- This migration implements database-backed persistence to ensure consistency.
--
-- =============================================================================
-- 1. CREATE USER DAILY SHOP ITEMS TABLE
-- =============================================================================
--
-- Create the main table to store user daily shop item selections
CREATE TABLE IF NOT EXISTS public.user_daily_shop_items (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id VARCHAR(255) NOT NULL,
    shop_item_id UUID NOT NULL,
    selection_date DATE NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    
    -- Foreign key constraints
    CONSTRAINT fk_user_daily_shop_items_shop_item 
        FOREIGN KEY (shop_item_id) REFERENCES public.shop_items(id) ON DELETE CASCADE,
    
    -- Unique constraint to prevent duplicate selections
    CONSTRAINT uk_user_daily_shop_selection 
        UNIQUE (user_id, shop_item_id, selection_date)
);

--
-- =============================================================================
-- 2. CREATE PERFORMANCE INDEXES
-- =============================================================================
--
-- Primary lookup index for fetching user's daily selections
CREATE INDEX IF NOT EXISTS idx_user_daily_shop_items_user_date 
    ON public.user_daily_shop_items(user_id, selection_date);

-- Index for cleanup operations (finding old records)
CREATE INDEX IF NOT EXISTS idx_user_daily_shop_items_selection_date 
    ON public.user_daily_shop_items(selection_date);

-- Index for shop item references (useful for analytics)
CREATE INDEX IF NOT EXISTS idx_user_daily_shop_items_shop_item 
    ON public.user_daily_shop_items(shop_item_id);

-- Index for creation time (useful for monitoring and debugging)
CREATE INDEX IF NOT EXISTS idx_user_daily_shop_items_created_at 
    ON public.user_daily_shop_items(created_at);

--
-- =============================================================================
-- 3. ADD DOCUMENTATION COMMENTS
-- =============================================================================
--
-- Table documentation
COMMENT ON TABLE public.user_daily_shop_items IS 
    'Stores user daily shop item selections for persistence across application restarts. Replaces volatile in-memory cache with database-backed storage.';

-- Column documentation
COMMENT ON COLUMN public.user_daily_shop_items.id IS 
    'Primary key UUID for the daily selection record';
    
COMMENT ON COLUMN public.user_daily_shop_items.user_id IS 
    'Discord user ID for whom this daily selection was made. References users.id conceptually but no FK constraint due to unregistered users.';
    
COMMENT ON COLUMN public.user_daily_shop_items.shop_item_id IS 
    'ID of the shop item selected for this user''s daily shop. Foreign key to shop_items.id.';
    
COMMENT ON COLUMN public.user_daily_shop_items.selection_date IS 
    'The date (LocalDate) for which this selection is valid. Ensures daily consistency.';
    
COMMENT ON COLUMN public.user_daily_shop_items.created_at IS 
    'Timestamp when this selection record was created. Used for cleanup and monitoring.';

-- Constraint documentation  
COMMENT ON CONSTRAINT uk_user_daily_shop_selection ON public.user_daily_shop_items IS 
    'Ensures each user can only have one selection of each shop item per day, preventing duplicates in daily selection.';

COMMENT ON CONSTRAINT fk_user_daily_shop_items_shop_item ON public.user_daily_shop_items IS 
    'Foreign key to shop_items table with CASCADE delete to automatically clean up selections when shop items are removed.';

--
-- =============================================================================
-- 4. MIGRATION VERIFICATION
-- =============================================================================
--
-- Log the successful completion of the migration
DO $$
BEGIN
    RAISE NOTICE 'Migration V17 completed successfully: user_daily_shop_items table created with indexes and constraints';
    RAISE NOTICE 'Daily shop selections will now persist across application restarts';
    RAISE NOTICE 'Automatic cleanup service will remove records older than 7 days';
END $$;

--
-- =============================================================================
-- 5. PERFORMANCE AND MAINTENANCE NOTES
-- =============================================================================
--
-- Performance characteristics:
-- - Primary lookup (user_id + selection_date) is highly optimized with composite index
-- - Cleanup operations use selection_date index for efficient bulk deletion
-- - Table expected to grow by ~4 records per active user per day
-- - Automatic cleanup after 7 days prevents unbounded growth
--
-- Maintenance:
-- - UserDailyShopItemCleanupService runs daily at 2:30 AM to remove old records
-- - Records older than 7 days are automatically purged
-- - No manual maintenance required under normal operation
--
-- Business logic:
-- - Replaces ShopService dailyUserItemsCache (Caffeine in-memory cache)
-- - Maintains same rarity distribution logic (55% Common, 25% Uncommon, etc.)
-- - Same seeded randomization ensures consistent daily selections per user
-- - Database persistence ensures selections survive application restarts 