CREATE TABLE admin_user (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    username VARCHAR(100) NOT NULL UNIQUE,
    password_hash VARCHAR(255) NOT NULL,
    role VARCHAR(20) NOT NULL DEFAULT 'ADMIN' CHECK (role IN ('ADMIN')),
    active BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

-- Los datos personales pueden estar incompletos mientras se aclara un pedido.
CREATE TABLE customer (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    first_name VARCHAR(100),
    last_name VARCHAR(100),
    phone_e164 VARCHAR(16) UNIQUE CHECK (phone_e164 ~ '^\+[1-9][0-9]{6,14}$'),
    whatsapp_id VARCHAR(100) UNIQUE,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    CHECK (phone_e164 IS NOT NULL OR whatsapp_id IS NOT NULL)
);

CREATE TABLE customer_address (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    customer_id UUID NOT NULL REFERENCES customer(id),
    address_text TEXT NOT NULL CHECK (btrim(address_text) <> ''),
    notes TEXT,
    is_default BOOLEAN NOT NULL DEFAULT FALSE,
    active BOOLEAN NOT NULL DEFAULT TRUE
);
CREATE UNIQUE INDEX uq_customer_default_address ON customer_address(customer_id)
    WHERE is_default AND active;

CREATE TABLE product (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    name VARCHAR(150) NOT NULL CHECK (btrim(name) <> ''),
    category VARCHAR(20) NOT NULL CHECK (category IN ('FRUIT', 'VEGETABLE')),
    sale_unit VARCHAR(20) NOT NULL CHECK (sale_unit IN ('KG', 'GRAM', 'UNIT', 'BUNCH')),
    current_price NUMERIC(14,2) CHECK (current_price >= 0),
    available BOOLEAN NOT NULL DEFAULT TRUE,
    active BOOLEAN NOT NULL DEFAULT TRUE,
    version BIGINT NOT NULL DEFAULT 0,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE TABLE product_alias (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    product_id UUID NOT NULL REFERENCES product(id),
    normalized_alias VARCHAR(150) NOT NULL UNIQUE CHECK (btrim(normalized_alias) <> '')
);

CREATE TABLE customer_order (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    order_number BIGINT GENERATED ALWAYS AS IDENTITY UNIQUE,
    customer_id UUID NOT NULL REFERENCES customer(id),
    source VARCHAR(20) NOT NULL CHECK (source IN ('MANUAL', 'WHATSAPP')),
    status VARCHAR(30) NOT NULL DEFAULT 'PENDING'
        CHECK (status IN ('PENDING', 'CONFIRMED', 'PREPARING', 'READY', 'OUT_FOR_DELIVERY', 'DELIVERED', 'CANCELLED')),
    validation_status VARCHAR(30) NOT NULL DEFAULT 'INCOMPLETE'
        CHECK (validation_status IN ('INCOMPLETE', 'REQUIRES_REVIEW', 'VALID')),
    delivery_first_name VARCHAR(100),
    delivery_last_name VARCHAR(100),
    delivery_phone VARCHAR(16),
    delivery_address TEXT,
    total NUMERIC(14,2) CHECK (total >= 0),
    currency CHAR(3) NOT NULL DEFAULT 'ARS' CHECK (currency = 'ARS'),
    notes TEXT,
    revision INTEGER NOT NULL DEFAULT 1 CHECK (revision > 0),
    version BIGINT NOT NULL DEFAULT 0,
    confirmed_at TIMESTAMPTZ,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    CHECK (status NOT IN ('CONFIRMED', 'PREPARING', 'READY', 'OUT_FOR_DELIVERY', 'DELIVERED')
        OR (validation_status = 'VALID' AND total IS NOT NULL AND confirmed_at IS NOT NULL
            AND nullif(btrim(delivery_first_name), '') IS NOT NULL
            AND nullif(btrim(delivery_last_name), '') IS NOT NULL
            AND nullif(btrim(delivery_phone), '') IS NOT NULL
            AND nullif(btrim(delivery_address), '') IS NOT NULL))
);
CREATE INDEX ix_order_status_date ON customer_order(status, created_at DESC);
CREATE INDEX ix_order_customer_date ON customer_order(customer_id, created_at DESC);
CREATE INDEX ix_order_created_at ON customer_order(created_at DESC);

CREATE TABLE order_item (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    order_id UUID NOT NULL REFERENCES customer_order(id),
    product_id UUID REFERENCES product(id),
    requested_text TEXT,
    product_name_snapshot VARCHAR(150),
    requested_quantity NUMERIC(12,3) CHECK (requested_quantity > 0),
    requested_unit VARCHAR(20) CHECK (requested_unit IN ('KG', 'GRAM', 'UNIT', 'BUNCH')),
    sale_quantity NUMERIC(12,3) CHECK (sale_quantity > 0),
    sale_unit_snapshot VARCHAR(20) CHECK (sale_unit_snapshot IN ('KG', 'GRAM', 'UNIT', 'BUNCH')),
    unit_price_snapshot NUMERIC(14,2) CHECK (unit_price_snapshot >= 0),
    subtotal NUMERIC(14,2) CHECK (subtotal >= 0),
    CHECK (subtotal IS NULL OR (product_id IS NOT NULL AND sale_quantity IS NOT NULL
        AND sale_unit_snapshot IS NOT NULL AND unit_price_snapshot IS NOT NULL
        AND subtotal = round(sale_quantity * unit_price_snapshot, 2)))
);
CREATE INDEX ix_item_order ON order_item(order_id);
CREATE TABLE order_issue (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    order_id UUID NOT NULL REFERENCES customer_order(id),
    code VARCHAR(60) NOT NULL,
    description TEXT NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    resolved_at TIMESTAMPTZ,
    resolved_by UUID REFERENCES admin_user(id)
);
CREATE INDEX ix_unresolved_issue ON order_issue(order_id) WHERE resolved_at IS NULL;
CREATE TABLE order_status_history (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    order_id UUID NOT NULL REFERENCES customer_order(id),
    previous_status VARCHAR(30) CHECK (previous_status IN ('PENDING', 'CONFIRMED', 'PREPARING', 'READY', 'OUT_FOR_DELIVERY', 'DELIVERED', 'CANCELLED')),
    new_status VARCHAR(30) NOT NULL CHECK (new_status IN ('PENDING', 'CONFIRMED', 'PREPARING', 'READY', 'OUT_FOR_DELIVERY', 'DELIVERED', 'CANCELLED')),
    actor_id UUID REFERENCES admin_user(id),
    reason TEXT,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX ix_status_history_order ON order_status_history(order_id, created_at);

CREATE TABLE whatsapp_conversation (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    customer_id UUID NOT NULL REFERENCES customer(id),
    whatsapp_id VARCHAR(100) NOT NULL,
    business_phone_number_id VARCHAR(100) NOT NULL,
    last_customer_message_at TIMESTAMPTZ,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    UNIQUE (whatsapp_id, business_phone_number_id)
);
CREATE TABLE whatsapp_message (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    conversation_id UUID NOT NULL REFERENCES whatsapp_conversation(id),
    external_message_id VARCHAR(255) UNIQUE,
    direction VARCHAR(10) NOT NULL CHECK (direction IN ('INBOUND', 'OUTBOUND')),
    message_type VARCHAR(30) NOT NULL,
    text TEXT,
    delivery_status VARCHAR(20) NOT NULL DEFAULT 'RECEIVED'
        CHECK (delivery_status IN ('RECEIVED', 'PENDING', 'SENT', 'DELIVERED', 'READ', 'FAILED')),
    occurred_at TIMESTAMPTZ NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    CHECK (direction <> 'INBOUND' OR external_message_id IS NOT NULL)
);
CREATE INDEX ix_message_conversation ON whatsapp_message(conversation_id, occurred_at);
CREATE TABLE webhook_event (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    deduplication_key VARCHAR(255) NOT NULL UNIQUE,
    payload JSONB NOT NULL,
    processing_status VARCHAR(20) NOT NULL DEFAULT 'PENDING'
        CHECK (processing_status IN ('PENDING', 'PROCESSING', 'PROCESSED', 'FAILED')),
    attempts INTEGER NOT NULL DEFAULT 0 CHECK (attempts >= 0),
    next_attempt_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    locked_at TIMESTAMPTZ,
    last_error_code VARCHAR(100),
    received_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    processed_at TIMESTAMPTZ
);
CREATE INDEX ix_webhook_pending ON webhook_event(next_attempt_at)
    WHERE processing_status IN ('PENDING', 'FAILED');
CREATE TABLE order_message (
    order_id UUID NOT NULL REFERENCES customer_order(id),
    message_id UUID NOT NULL REFERENCES whatsapp_message(id),
    PRIMARY KEY (order_id, message_id)
);
CREATE TABLE order_confirmation (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    order_id UUID NOT NULL REFERENCES customer_order(id),
    order_revision INTEGER NOT NULL CHECK (order_revision > 0),
    confirmation_type VARCHAR(20) NOT NULL CHECK (confirmation_type IN ('CUSTOMER', 'ADMIN')),
    message_id UUID REFERENCES whatsapp_message(id),
    confirmed_by UUID REFERENCES admin_user(id),
    confirmed_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    CHECK ((confirmation_type = 'CUSTOMER' AND message_id IS NOT NULL)
        OR (confirmation_type = 'ADMIN' AND confirmed_by IS NOT NULL))
);
CREATE TABLE outbox_message (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    conversation_id UUID NOT NULL REFERENCES whatsapp_conversation(id),
    order_id UUID REFERENCES customer_order(id),
    idempotency_key VARCHAR(255) NOT NULL UNIQUE,
    message_type VARCHAR(30) NOT NULL,
    payload JSONB NOT NULL,
    status VARCHAR(20) NOT NULL DEFAULT 'PENDING'
        CHECK (status IN ('PENDING', 'PROCESSING', 'SENT', 'FAILED', 'REQUIRES_REVIEW')),
    attempts INTEGER NOT NULL DEFAULT 0 CHECK (attempts >= 0),
    next_attempt_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    locked_at TIMESTAMPTZ,
    external_message_id VARCHAR(255),
    last_error_code VARCHAR(100),
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    sent_at TIMESTAMPTZ
);
CREATE INDEX ix_outbox_pending ON outbox_message(next_attempt_at) WHERE status IN ('PENDING', 'FAILED');
CREATE TABLE audit_event (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    actor_id UUID REFERENCES admin_user(id),
    action VARCHAR(100) NOT NULL,
    entity_type VARCHAR(100) NOT NULL,
    entity_id UUID NOT NULL,
    changes JSONB,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX ix_audit_entity ON audit_event(entity_type, entity_id, created_at);
