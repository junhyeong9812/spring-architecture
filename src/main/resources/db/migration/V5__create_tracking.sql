CREATE TABLE order_tracking (
                                id                UUID PRIMARY KEY,
                                order_id          UUID NOT NULL,
                                customer_name     VARCHAR(255) NOT NULL,
                                subscription_tier VARCHAR(20),
                                current_phase     VARCHAR(30) NOT NULL,
                                started_at        TIMESTAMPTZ NOT NULL,
                                completed_at      TIMESTAMPTZ
);

CREATE TABLE tracking_events (
                                 id         UUID PRIMARY KEY DEFAULT gen_random_uuid(),
                                 tracking_id UUID NOT NULL REFERENCES order_tracking(id),
                                 event_type VARCHAR(50) NOT NULL,
                                 timestamp  TIMESTAMPTZ NOT NULL,
                                 module     VARCHAR(50) NOT NULL,
                                 detail     JSONB NOT NULL DEFAULT '{}',
                                 CONSTRAINT fk_tracking FOREIGN KEY (tracking_id) REFERENCES order_tracking(id)
);

CREATE INDEX idx_tracking_order ON order_tracking (order_id);
CREATE INDEX idx_tracking_events_tracking ON tracking_events (tracking_id);