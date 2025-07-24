-- First, ensure the target table exists. This makes the script robust if it's run
-- against a database that was baselined before this table was added to V1.
CREATE TABLE IF NOT EXISTS public.item_instances (
    id UUID PRIMARY KEY,
    owner_id VARCHAR(255) NOT NULL,
    base_item_id UUID NOT NULL,
    serial_number BIGINT,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    CONSTRAINT fk_item_instances_owner FOREIGN KEY (owner_id) REFERENCES public.users(id),
    CONSTRAINT fk_item_instances_base_item FOREIGN KEY (base_item_id) REFERENCES public.shop_items(id)
);

DO $$
DECLARE
    item_row RECORD;
BEGIN
    -- Iterate over each entry in the old user_inventory_items table
    FOR item_row IN
        SELECT user_id, item_id, quantity, created_at FROM public.user_inventory_items
    LOOP
        -- For each entry, insert a number of rows equal to the quantity into the new item_instances table
        FOR i IN 1..item_row.quantity LOOP
            INSERT INTO public.item_instances (id, owner_id, base_item_id, created_at, serial_number)
            VALUES (gen_random_uuid(), item_row.user_id, item_row.item_id, item_row.created_at, NULL);
        END LOOP;
    END LOOP;

    -- After migrating all data, drop the old, now-redundant tables.
    DROP TABLE public.user_inventory_items;
    DROP TABLE public.user_inventory;
END $$; 