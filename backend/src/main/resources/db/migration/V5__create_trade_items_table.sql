CREATE TABLE IF NOT EXISTS public.trade_items (
    id BIGSERIAL PRIMARY KEY,
    trade_id BIGINT NOT NULL,
    item_instance_id UUID NOT NULL,
    CONSTRAINT fk_trade_items_trade FOREIGN KEY (trade_id) REFERENCES public.trades(id),
    CONSTRAINT fk_trade_items_item_instance FOREIGN KEY (item_instance_id) REFERENCES public.item_instances(id),
    CONSTRAINT uk_trade_items_item_instance_id UNIQUE (item_instance_id)
); 