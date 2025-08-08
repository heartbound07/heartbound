-- V18: Fix trade_items uniqueness to be per-trade and improve lookup performance
--
-- Problem:
-- The existing constraint uk_trade_items_item_instance_id enforces global uniqueness of item_instance_id,
-- which prevents an item from appearing in any other trade record (including historical), causing conflicts.
--
-- Solution:
-- 1) Drop the global unique constraint
-- 2) Add a composite unique constraint on (trade_id, item_instance_id)
-- 3) Add an index on item_instance_id for efficient lookups

-- 1) Drop the global unique constraint if it exists
ALTER TABLE public.trade_items
DROP CONSTRAINT IF EXISTS uk_trade_items_item_instance_id;

-- 2) Add composite unique constraint per trade
ALTER TABLE public.trade_items
ADD CONSTRAINT uk_trade_items_trade_item_instance UNIQUE (trade_id, item_instance_id);

-- 3) Add supporting index for queries by item_instance_id
CREATE INDEX IF NOT EXISTS idx_trade_items_item_instance_id
    ON public.trade_items(item_instance_id); 