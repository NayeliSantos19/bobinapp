-- Bobinapp · esquema inicial
-- Modelo multi-finca: cada finca (tenant) ve solo sus animales y eventos.
-- La sincronización usa dos relojes distintos:
--   * actualizado_cliente: epoch ms del dispositivo que hizo el cambio. Decide conflictos (last-writer-wins).
--   * version_servidor: número creciente asignado por el servidor. Es el cursor de descarga (pull),
--     así un reloj de teléfono mal configurado nunca hace que otro dispositivo se pierda cambios.

CREATE EXTENSION IF NOT EXISTS pgcrypto;

CREATE SEQUENCE IF NOT EXISTS sync_version_seq;

CREATE TABLE IF NOT EXISTS fincas (
    id          UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    nombre      TEXT        NOT NULL CHECK (length(nombre) BETWEEN 1 AND 120),
    token_hash  TEXT        NOT NULL UNIQUE,
    creada_en   TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE TABLE IF NOT EXISTS animales (
    id                   UUID PRIMARY KEY,
    finca_id             UUID    NOT NULL REFERENCES fincas(id) ON DELETE CASCADE,
    arete                TEXT    NOT NULL,
    nombre               TEXT,
    sexo                 CHAR(1) NOT NULL CHECK (sexo IN ('H', 'M')),
    raza_id              TEXT,
    raza_texto           TEXT,
    nacimiento           DATE    NOT NULL,
    -- Sin llave foránea a propósito: en una sincronización la cría puede llegar antes que su madre.
    madre_id             UUID,
    padre_id             UUID,
    padre_externo        TEXT,
    castrado             BOOLEAN NOT NULL DEFAULT false,
    estado               TEXT    NOT NULL DEFAULT 'activa'
                         CHECK (estado IN ('activa', 'vendida', 'muerta', 'descartada')),
    notas                TEXT,
    eliminado            BOOLEAN NOT NULL DEFAULT false,
    actualizado_cliente  BIGINT  NOT NULL,
    version_servidor     BIGINT  NOT NULL DEFAULT nextval('sync_version_seq')
);

CREATE INDEX IF NOT EXISTS animales_finca_version_idx ON animales (finca_id, version_servidor);
CREATE INDEX IF NOT EXISTS animales_finca_madre_idx   ON animales (finca_id, madre_id);
CREATE INDEX IF NOT EXISTS animales_finca_padre_idx   ON animales (finca_id, padre_id);

CREATE TABLE IF NOT EXISTS eventos (
    id                   UUID PRIMARY KEY,
    finca_id             UUID    NOT NULL REFERENCES fincas(id) ON DELETE CASCADE,
    animal_id            UUID    NOT NULL,
    tipo                 TEXT    NOT NULL CHECK (tipo IN (
                             'PESO', 'LECHE', 'VACUNA', 'TRATAMIENTO', 'CELO', 'INSEMINACION',
                             'PALPACION', 'PARTO', 'DESTETE', 'BAJA', 'NOTA')),
    fecha                DATE    NOT NULL,
    kg                   NUMERIC(7, 2),
    litros               NUMERIC(6, 2),
    producto             TEXT,
    dosis                TEXT,
    diagnostico          TEXT,
    proxima_fecha        DATE,
    retiro_dias          INTEGER CHECK (retiro_dias IS NULL OR retiro_dias BETWEEN 0 AND 365),
    costo                NUMERIC(10, 2) CHECK (costo IS NULL OR costo >= 0),
    toro                 TEXT,
    toro_id              UUID,
    tecnico              TEXT,
    resultado            TEXT CHECK (resultado IS NULL OR resultado IN ('PRENADA', 'VACIA')),
    cria_id              UUID,
    nota                 TEXT,
    eliminado            BOOLEAN NOT NULL DEFAULT false,
    actualizado_cliente  BIGINT  NOT NULL,
    version_servidor     BIGINT  NOT NULL DEFAULT nextval('sync_version_seq')
);

CREATE INDEX IF NOT EXISTS eventos_finca_version_idx ON eventos (finca_id, version_servidor);
CREATE INDEX IF NOT EXISTS eventos_animal_fecha_idx  ON eventos (animal_id, fecha);

-- Vista de ejemplo para analíticas en el servidor: gasto de farmacia por mes y finca.
CREATE OR REPLACE VIEW gasto_farmacia_mensual AS
SELECT finca_id,
       date_trunc('month', fecha)::date AS mes,
       sum(costo)                        AS total_usd,
       count(*)                          AS aplicaciones
FROM eventos
WHERE NOT eliminado AND tipo IN ('VACUNA', 'TRATAMIENTO') AND costo IS NOT NULL
GROUP BY finca_id, date_trunc('month', fecha);
