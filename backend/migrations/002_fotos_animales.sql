-- Bobinapp · fotos de cada animal
-- La fila del animal lleva la fecha de su foto (foto_actualizada_en), que viaja con la sincronización normal.
-- Los bytes de la imagen van aparte, en su propia tabla y endpoint, para no inflar cada pull.

ALTER TABLE animales ADD COLUMN IF NOT EXISTS foto_actualizada_en BIGINT;

CREATE TABLE IF NOT EXISTS fotos_animal (
    animal_id       UUID PRIMARY KEY,
    finca_id        UUID    NOT NULL REFERENCES fincas(id) ON DELETE CASCADE,
    tipo_mime       TEXT    NOT NULL CHECK (tipo_mime IN ('image/jpeg', 'image/png', 'image/webp')),
    contenido       BYTEA   NOT NULL,
    bytes           INTEGER NOT NULL CHECK (bytes > 0 AND bytes <= 3145728),
    actualizada_en  BIGINT  NOT NULL
);

CREATE INDEX IF NOT EXISTS fotos_animal_finca_idx ON fotos_animal (finca_id);
