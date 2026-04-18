CREATE TABLE subscriptions (
    id              UUID PRIMARY KEY,
    customer_name   VARCHAR(255) NOT NULL,
    tier            VARCHAR(20)  NOT NULL,
    status          VARCHAR(20)  NOT NULL,
    started_at      TIMESTAMPTZ  NOT NULL,
    expires_at      TIMESTAMPTZ  NOT NULL
);

CREATE INDEX idx_subscriptions_customer_status
    ON subscriptions (customer_name, status);
