const express = require('express');
const crypto = require('node:crypto');
const { z } = require('zod');

// Valores pequeños y planos: nada de textos largos donde pudieran colarse datos personales.
const valor = z.union([z.string().max(200), z.number().finite(), z.boolean()]);

const eventoSchema = z.object({
  evento: z.string().regex(/^[a-z][a-z0-9_]{1,39}$/),
  pantalla: z.string().regex(/^[a-z][a-z0-9_]{0,39}$/).nullish(),
  datos: z.record(z.string().regex(/^[a-z][a-zA-Z0-9_]{0,29}$/), valor)
    .refine((d) => Object.keys(d).length <= 12, 'demasiados_campos')
    .default({}),
  ocurridoEn: z.number().int().positive(),
});

const loteSchema = z.object({
  instalacion: z.string().uuid(),
  appVersion: z.string().max(20).optional(),
  android: z.number().int().min(1).max(100).optional(),
  eventos: z.array(eventoSchema).min(1).max(200),
});

const DIA = 86_400_000;

/** Compara dos textos sin filtrar por el tiempo de respuesta cuánto coinciden. */
function igualSeguro(a, b) {
  const x = Buffer.from(String(a));
  const y = Buffer.from(String(b));
  return x.length === y.length && crypto.timingSafeEqual(x, y);
}

module.exports = function rutasTelemetria(pool, opciones = {}) {
  const r = express.Router();

  // Recibe un lote de eventos anónimos. No pide token de finca a propósito: no se relaciona con nadie.
  r.post('/', express.json({ limit: '256kb' }), async (req, res, next) => {
    const datos = loteSchema.safeParse(req.body);
    if (!datos.success) return res.status(400).json({ error: 'datos_invalidos', detalles: datos.error.issues });
    const { instalacion, appVersion, android, eventos } = datos.data;
    const ahora = Date.now();
    // Se descartan fechas absurdas (relojes mal puestos): más de 30 días atrás o 1 día en el futuro.
    const validos = eventos.filter((e) => e.ocurridoEn > ahora - 30 * DIA && e.ocurridoEn < ahora + DIA);
    try {
      if (validos.length) {
        const valores = [];
        const filas = validos.map((e, i) => {
          const b = i * 7;
          valores.push(instalacion, e.evento, e.pantalla ?? null, JSON.stringify(e.datos), appVersion ?? null, android ?? null, new Date(e.ocurridoEn));
          return `($${b + 1}, $${b + 2}, $${b + 3}, $${b + 4}::jsonb, $${b + 5}, $${b + 6}, $${b + 7})`;
        });
        await pool.query(
          `INSERT INTO telemetria (instalacion, evento, pantalla, datos, app_version, android, ocurrido_en) VALUES ${filas.join(',')}`,
          valores,
        );
      }
      res.status(202).json({ recibidos: validos.length, descartados: eventos.length - validos.length });
    } catch (err) {
      next(err);
    }
  });

  // Resumen para el equipo que desarrolla la app. Protegido con ADMIN_TOKEN; si no está configurado, no existe.
  r.get('/resumen', async (req, res, next) => {
    const admin = opciones.adminToken ?? process.env.ADMIN_TOKEN;
    const dado = (req.get('authorization') || '').replace(/^Bearer\s+/i, '');
    if (!admin) return res.status(404).json({ error: 'no_encontrado' });
    if (!dado || !igualSeguro(dado, admin)) return res.status(401).json({ error: 'no_autorizado' });
    const dias = Math.min(Math.max(Number(req.query.dias) || 7, 1), 90);
    const desde = new Date(Date.now() - dias * DIA);
    try {
      const [activas, pantallas, eventos, errores, inicio, versiones] = await Promise.all([
        pool.query('SELECT count(DISTINCT instalacion)::int AS n FROM telemetria WHERE ocurrido_en >= $1', [desde]),
        pool.query(
          `SELECT pantalla, count(*)::int AS vistas, count(DISTINCT instalacion)::int AS instalaciones
             FROM telemetria WHERE evento = 'pantalla' AND ocurrido_en >= $1
            GROUP BY pantalla ORDER BY vistas DESC LIMIT 20`, [desde]),
        pool.query(
          `SELECT evento, count(*)::int AS veces FROM telemetria
            WHERE evento NOT IN ('pantalla', 'error') AND ocurrido_en >= $1
            GROUP BY evento ORDER BY veces DESC LIMIT 20`, [desde]),
        pool.query(
          `SELECT datos->>'tipo' AS tipo, datos->>'lugar' AS lugar, pantalla,
                  count(*)::int AS veces, count(DISTINCT instalacion)::int AS instalaciones, max(ocurrido_en) AS ultima
             FROM telemetria WHERE evento = 'error' AND ocurrido_en >= $1
            GROUP BY 1, 2, 3 ORDER BY veces DESC LIMIT 20`, [desde]),
        pool.query(
          `SELECT percentile_cont(0.5) WITHIN GROUP (ORDER BY (datos->>'ms')::numeric) AS p50,
                  percentile_cont(0.9) WITHIN GROUP (ORDER BY (datos->>'ms')::numeric) AS p90,
                  count(*)::int AS muestras
             FROM telemetria WHERE evento = 'inicio_app' AND datos ? 'ms' AND ocurrido_en >= $1`, [desde]),
        pool.query(
          `SELECT app_version AS version, count(DISTINCT instalacion)::int AS instalaciones
             FROM telemetria WHERE ocurrido_en >= $1 GROUP BY 1 ORDER BY 2 DESC`, [desde]),
      ]);
      const i = inicio.rows[0];
      res.json({
        dias,
        instalacionesActivas: activas.rows[0].n,
        pantallas: pantallas.rows,
        eventos: eventos.rows,
        errores: errores.rows,
        inicioApp: { p50Ms: i.p50 === null ? null : Math.round(i.p50), p90Ms: i.p90 === null ? null : Math.round(i.p90), muestras: i.muestras },
        versiones: versiones.rows,
      });
    } catch (err) {
      next(err);
    }
  });

  // El usuario puede pedir que se borre todo lo de su instalación.
  r.delete('/:instalacion', async (req, res, next) => {
    if (!z.string().uuid().safeParse(req.params.instalacion).success) return res.status(400).json({ error: 'datos_invalidos' });
    try {
      const { rowCount } = await pool.query('DELETE FROM telemetria WHERE instalacion = $1', [req.params.instalacion]);
      res.json({ borrados: rowCount });
    } catch (err) {
      next(err);
    }
  });

  return r;
};
