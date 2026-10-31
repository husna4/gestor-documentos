-- Módulo personas
CREATE TABLE persona (
    id         BIGSERIAL    PRIMARY KEY,
    nombre     VARCHAR(100) NOT NULL,
    apellidos  VARCHAR(150),
    notas      TEXT,
    creado_en  TIMESTAMPTZ  NOT NULL DEFAULT now()
);

-- Módulo usuarios
CREATE TABLE usuario (
    id                    BIGSERIAL    PRIMARY KEY,
    username              VARCHAR(50)  NOT NULL UNIQUE,   -- siempre en minúsculas
    password_hash         VARCHAR(100) NOT NULL,          -- BCrypt
    rol                   VARCHAR(10)  NOT NULL CHECK (rol IN ('ADMIN', 'USER')),
    activo                BOOLEAN      NOT NULL DEFAULT TRUE,
    debe_cambiar_password BOOLEAN      NOT NULL DEFAULT FALSE,
    -- Persona vinculada (opcional). Sin clave foránea: es de otro módulo y la
    -- coherencia se mantiene con el evento PersonaEliminadaEvent.
    persona_id            BIGINT,
    creado_en             TIMESTAMPTZ  NOT NULL DEFAULT now()
);
