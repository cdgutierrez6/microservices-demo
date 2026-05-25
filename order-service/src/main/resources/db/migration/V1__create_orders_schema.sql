CREATE TABLE IF NOT EXISTS orders (
    id            UUID PRIMARY KEY,
    user_id       UUID NOT NULL,
    status        VARCHAR(20) NOT NULL DEFAULT 'PENDING',
    total_amount  NUMERIC(19,2) NOT NULL,
    created_at    TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at    TIMESTAMPTZ
);

CREATE TABLE IF NOT EXISTS order_items (
    id            UUID PRIMARY KEY,
    order_id      UUID NOT NULL REFERENCES orders(id) ON DELETE CASCADE,
    product_name  VARCHAR(255) NOT NULL,
    unit_price    NUMERIC(19,2) NOT NULL,
    quantity      INT NOT NULL CHECK (quantity > 0)
);

CREATE TABLE IF NOT EXISTS outbox_events (
    id            UUID PRIMARY KEY,
    aggregate_id  UUID NOT NULL,
    event_type    VARCHAR(100) NOT NULL,
    payload       TEXT NOT NULL,
    created_at    TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    published_at  TIMESTAMPTZ
);

CREATE INDEX idx_orders_user_id   ON orders(user_id);
CREATE INDEX idx_outbox_pending   ON outbox_events(created_at) WHERE published_at IS NULL;
