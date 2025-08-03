-- V15: Update shop_items_category_check constraint to include new categories
-- This migration updates the check constraint on the shop_items table to allow for
-- new item categories like 'FISHING_ROD_PART' that were causing errors.

-- =============================================================================
-- 1. DROP EXISTING CHECK CONSTRAINT
-- =============================================================================
--
-- Drop the old check constraint if it exists. This makes the script safe to re-run
-- and ensures we are not building on an outdated constraint.
ALTER TABLE public.shop_items
DROP CONSTRAINT IF EXISTS shop_items_category_check;

--
-- =============================================================================
-- 2. ADD NEW, COMPREHENSIVE CHECK CONSTRAINT
-- =============================================================================
--
-- Add the new check constraint with an updated list of all valid categories.
-- This includes 'FISHING_ROD_PART' and ensures all current and future items
-- can be created without violating database integrity.
ALTER TABLE public.shop_items
ADD CONSTRAINT shop_items_category_check
CHECK (category IN ('USER_COLOR', 'LISTING', 'ACCENT', 'BADGE', 'CASE', 'FISHING_ROD', 'FISHING_ROD_PART'));

--
-- =============================================================================
-- 3. COMMENTS FOR DOCUMENTATION
-- =============================================================================
--
COMMENT ON CONSTRAINT shop_items_category_check ON public.shop_items IS 'Ensures that the category of a shop item is one of the valid, predefined enum values from ShopCategory.java.'; 