CREATE TABLE payments (
                          id                    UUID PRIMARY KEY,
                          order_id              UUID NOT NULL REFERENCES orders(id),
                          original_amount       DECIMAL(15,2) NOT NULL,
                          discount_amount       DECIMAL(15,2) NOT NULL DEFAULT 0,
                          final_amount          DECIMAL(15,2) NOT NULL,
                          method                VARCHAR(20) NOT NULL,
                          status                VARCHAR(20) NOT NULL,
                          applied_discount_type VARCHAR(50) NOT NULL DEFAULT 'none',
                          processed_at          TIMESTAMPTZ
);

CREATE INDEX idx_payments_order ON payments (order_id);