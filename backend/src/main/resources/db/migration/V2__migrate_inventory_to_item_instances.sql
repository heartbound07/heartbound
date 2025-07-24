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