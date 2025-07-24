-- This migration script fixes the database schema for the trading feature.
-- It adds the `item_instance_id` column to the `trade_items` table,
-- which is required to link a trade item to a specific item instance.

-- NOTE: This script assumes that the `trade_items` table is currently empty.
-- If it contains data from previously failed trade attempts, you might need to
-- clear it before running this script to avoid errors with the NOT NULL constraint.
-- You can do so by running: TRUNCATE TABLE trade_items RESTART IDENTITY;

-- 1. Add the item_instance_id column.
-- It's defined as UUID and NOT NULL to match the ItemInstance ID and the
-- `nullable=false` property on the @OneToOne relationship in the TradeItem entity.
ALTER TABLE public.trade_items
    ADD COLUMN item_instance_id UUID NOT NULL;

-- 2. Add a UNIQUE constraint on the new column.
-- This is crucial for enforcing the @OneToOne relationship, ensuring that
-- a single ItemInstance cannot be part of multiple trades at the same time.
ALTER TABLE public.trade_items
    ADD CONSTRAINT uk_trade_items_item_instance_id UNIQUE (item_instance_id);

-- 3. Add the foreign key constraint.
-- This creates the relationship between `trade_items` and `item_instances`,
-- ensuring data integrity. It also adds an index automatically for performance.
ALTER TABLE public.trade_items
    ADD CONSTRAINT fk_trade_items_on_item_instance
    FOREIGN KEY (item_instance_id) REFERENCES public.item_instances (id);