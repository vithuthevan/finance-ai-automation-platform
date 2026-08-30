-- V1: extensions and role lookup

CREATE EXTENSION IF NOT EXISTS "pgcrypto";

CREATE TABLE roles (
    id          SMALLINT     PRIMARY KEY,
    code        VARCHAR(30)  NOT NULL UNIQUE,
    name        VARCHAR(100) NOT NULL,
    description TEXT
);

INSERT INTO roles (id, code, name) VALUES
    (1, 'ADMIN',          'Firm Administrator'),
    (2, 'ACCOUNTANT',     'Accountant'),
    (3, 'AUDITOR',        'Auditor'),
    (4, 'BUSINESS_OWNER', 'Business Owner');
