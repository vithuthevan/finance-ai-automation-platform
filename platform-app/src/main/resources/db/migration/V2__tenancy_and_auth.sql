-- V2: tenancy and authentication

CREATE TABLE firms (
    id               UUID         PRIMARY KEY DEFAULT gen_random_uuid(),
    name             VARCHAR(200) NOT NULL,
    registration_no  VARCHAR(50),
    currency_code    CHAR(3)      NOT NULL DEFAULT 'LKR',
    active           BOOLEAN      NOT NULL DEFAULT TRUE,
    created_at       TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    updated_at       TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    created_by       UUID,
    updated_by       UUID,

    CONSTRAINT uq_firms_name UNIQUE (name)
);

CREATE TABLE clients (
    id              UUID         PRIMARY KEY DEFAULT gen_random_uuid(),
    firm_id         UUID         NOT NULL REFERENCES firms(id) ON DELETE RESTRICT,
    name            VARCHAR(200) NOT NULL,
    business_reg_no VARCHAR(50),
    contact_email   VARCHAR(255),
    active          BOOLEAN      NOT NULL DEFAULT TRUE,
    deleted_at      TIMESTAMPTZ,
    created_at      TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    updated_at      TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    created_by      UUID,
    updated_by      UUID,

    CONSTRAINT uq_clients_firm_name UNIQUE (firm_id, name)
);

CREATE INDEX idx_clients_firm_id ON clients(firm_id);
CREATE INDEX idx_clients_active  ON clients(firm_id) WHERE deleted_at IS NULL;

CREATE TABLE users (
    id              UUID         PRIMARY KEY DEFAULT gen_random_uuid(),
    firm_id         UUID         NOT NULL REFERENCES firms(id) ON DELETE RESTRICT,
    role_id         SMALLINT     NOT NULL REFERENCES roles(id) ON DELETE RESTRICT,
    email           VARCHAR(255) NOT NULL,
    password_hash   VARCHAR(255) NOT NULL,
    full_name       VARCHAR(200) NOT NULL,
    phone           VARCHAR(30),
    active          BOOLEAN      NOT NULL DEFAULT TRUE,
    last_login_at   TIMESTAMPTZ,
    deleted_at      TIMESTAMPTZ,
    created_at      TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    updated_at      TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    created_by      UUID,
    updated_by      UUID,

    CONSTRAINT uq_users_email UNIQUE (email)
);

CREATE INDEX idx_users_firm_id ON users(firm_id);
CREATE INDEX idx_users_role_id ON users(role_id);
CREATE INDEX idx_users_active  ON users(firm_id) WHERE deleted_at IS NULL AND active = TRUE;

CREATE TABLE user_client_access (
    id           UUID        PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id      UUID        NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    client_id    UUID        NOT NULL REFERENCES clients(id) ON DELETE CASCADE,
    access_type  VARCHAR(20) NOT NULL DEFAULT 'FULL'
        CHECK (access_type IN ('FULL', 'READ_ONLY', 'UPLOAD_ONLY')),
    valid_from   DATE,
    valid_to     DATE,
    created_at   TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at   TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    created_by   UUID,
    updated_by   UUID,

    CONSTRAINT uq_user_client UNIQUE (user_id, client_id),
    CONSTRAINT chk_uca_period CHECK (valid_to IS NULL OR valid_from IS NULL OR valid_to >= valid_from)
);

CREATE INDEX idx_uca_user_id   ON user_client_access(user_id);
CREATE INDEX idx_uca_client_id ON user_client_access(client_id);
