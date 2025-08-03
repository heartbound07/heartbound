-- V14: Initialize legacy item instances with durability and experience values
-- This migration populates existing fishing rod and fishing rod part instances
-- with proper durability, max_durability, and experience values based on their base shop items.
--
-- =============================================================================
-- 1. INITIALIZE FISHING ROD INSTANCES
-- =============================================================================
--
-- Update fishing rod instances that don't have durability initialized
UPDATE public.item_instances 
SET 
    durability = shop_items.max_durability,
    max_durability = shop_items.max_durability,
    experience = 0,
    level = 1
FROM public.shop_items 
WHERE 
    item_instances.base_item_id = shop_items.id 
    AND shop_items.category = 'FISHING_ROD'
    AND item_instances.durability IS NULL
    AND shop_items.max_durability IS NOT NULL;

--
-- =============================================================================
-- 2. INITIALIZE FISHING ROD PART INSTANCES  
-- =============================================================================
--
-- Update fishing rod part instances that don't have durability initialized
UPDATE public.item_instances 
SET 
    durability = shop_items.max_durability,
    max_durability = shop_items.max_durability
FROM public.shop_items 
WHERE 
    item_instances.base_item_id = shop_items.id 
    AND shop_items.category = 'FISHING_ROD_PART'
    AND item_instances.durability IS NULL
    AND shop_items.max_durability IS NOT NULL;

--
-- =============================================================================
-- 3. HANDLE CASES WHERE SHOP ITEMS DON'T HAVE MAX_DURABILITY SET
-- =============================================================================
--
-- For shop items that don't have max_durability set, use sensible defaults
-- This handles cases where admins haven't updated the base shop items yet

-- Default durability for fishing rods (if base shop item has no max_durability)
UPDATE public.item_instances 
SET 
    durability = 100,
    max_durability = 100,
    experience = 0,
    level = 1
FROM public.shop_items 
WHERE 
    item_instances.base_item_id = shop_items.id 
    AND shop_items.category = 'FISHING_ROD'
    AND item_instances.durability IS NULL
    AND shop_items.max_durability IS NULL;

-- Default durability for fishing rod parts (if base shop item has no max_durability)  
UPDATE public.item_instances 
SET 
    durability = 50,
    max_durability = 50
FROM public.shop_items 
WHERE 
    item_instances.base_item_id = shop_items.id 
    AND shop_items.category = 'FISHING_ROD_PART'
    AND item_instances.durability IS NULL
    AND shop_items.max_durability IS NULL;

--
-- =============================================================================
-- 4. VERIFICATION QUERIES (FOR LOGGING)
-- =============================================================================
--
-- Log the results of the migration
DO $$
DECLARE
    rod_count INTEGER;
    part_count INTEGER;
BEGIN
    -- Count updated fishing rods
    SELECT COUNT(*) INTO rod_count
    FROM public.item_instances ii
    JOIN public.shop_items si ON ii.base_item_id = si.id
    WHERE si.category = 'FISHING_ROD' AND ii.durability IS NOT NULL;
    
    -- Count updated fishing rod parts
    SELECT COUNT(*) INTO part_count
    FROM public.item_instances ii
    JOIN public.shop_items si ON ii.base_item_id = si.id
    WHERE si.category = 'FISHING_ROD_PART' AND ii.durability IS NOT NULL;
    
    RAISE NOTICE 'Migration V14 completed: Initialized % fishing rods and % fishing rod parts', rod_count, part_count;
END $$;

--
-- =============================================================================
-- 5. COMMENTS FOR DOCUMENTATION
-- =============================================================================
--
COMMENT ON COLUMN public.item_instances.durability IS 'Current durability value initialized from base shop item max_durability';
COMMENT ON COLUMN public.item_instances.max_durability IS 'Maximum durability value copied from base shop item for easy access';
COMMENT ON COLUMN public.item_instances.experience IS 'Item experience points initialized to 0 for new progression system'; 