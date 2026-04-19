CREATE TABLE shipments (
                           id                 UUID PRIMARY KEY,
                           order_id           UUID NOT NULL REFERENCES orders(id),
                           status             VARCHAR(20) NOT NULL,
                           street             VARCHAR(255),
                           city               VARCHAR(100),
                           zip_code           VARCHAR(20),
                           shipping_fee       DECIMAL(15,2) NOT NULL DEFAULT 0,
                           original_fee       DECIMAL(15,2) NOT NULL DEFAULT 0,
                           fee_discount_type  VARCHAR(50) NOT NULL DEFAULT 'none',
                           tracking_number    VARCHAR(50),
                           estimated_delivery DATE,
                           created_at         TIMESTAMPTZ NOT NULL
);

CREATE INDEX idx_shipments_order ON shipments (order_id);