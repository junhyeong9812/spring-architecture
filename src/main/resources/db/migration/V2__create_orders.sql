CREATE TABLE orders (
    id              UUID PRIMARY KEY,
    customer_name   VARCHAR(255) NOT NULL,
    status          VARCHAR(20)  NOT NULL,
    total_amount    DECIMAL(15,2) NOT NULL,
    shipping_fee    DECIMAL(15,2) NOT NULL DEFAULT 0,
    discount_amount DECIMAL(15,2) NOT NULL DEFAULT 0,
    final_amount    DECIMAL(15,2) NOT NULL,
    created_at      TIMESTAMPTZ   NOT NULL
);

CREATE TABLE order_items (
    id            UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    order_id      UUID NOT NULL REFERENCES orders(id),
    product_name  VARCHAR(255) NOT NULL,
    quantity      INT NOT NULL,
    unit_price    DECIMAL(15,2) NOT NULL
);

CREATE INDEX idx_orders_customer ON orders (customer_name);
CREATE INDEX idx_orders_status ON orders (status);
CREATE INDEX idx_order_items_order ON order_items (order_id);
