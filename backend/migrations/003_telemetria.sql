-- Analítica de uso anónima: qué pantallas se usan, cuánto tarda en abrir la app y qué errores ocurren.
-- No guarda la finca, el token ni la IP: solo un id aleatorio por instalación que el usuario puede borrar.
CREATE TABLE IF NOT EXISTS telemetria (
  id           BIGSERIAL PRIMARY KEY,
  instalacion  UUID        NOT NULL,
  evento       TEXT        NOT NULL,
  pantalla     TEXT,
  datos        JSONB       NOT NULL DEFAULT '{}'::jsonb,
  app_version  TEXT,
  android      INTEGER,
  ocurrido_en  TIMESTAMPTZ NOT NULL,
  recibido_en  TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX IF NOT EXISTS telemetria_ocurrido_idx ON telemetria (ocurrido_en);
CREATE INDEX IF NOT EXISTS telemetria_evento_idx ON telemetria (evento, ocurrido_en);
