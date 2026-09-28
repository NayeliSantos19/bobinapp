const express = require('express');
const razas = require('../data/razas.json');
const rutasFincas = require('./routes/fincas');
const rutasSync = require('./routes/sync');
const rutasIdentificar = require('./routes/identificar');
const rutasFotos = require('./routes/fotos');

function crearApp(pool, opciones = {}) {
  const app = express();
  app.disable('x-powered-by');
  app.use(express.json({ limit: '10mb' }));

  app.get('/api/salud', async (req, res) => {
    try {
      await pool.query('SELECT 1');
      res.json({ ok: true, base: 'conectada' });
    } catch {
      res.status(503).json({ ok: false, base: 'sin_conexion' });
    }
  });

  app.get('/api/razas', (req, res) => res.json(razas));
  app.use('/api/fincas', rutasFincas(pool));
  app.use('/api/sync', rutasSync(pool));
  app.use('/api/identificar', rutasIdentificar(pool, opciones));
  app.use('/api/fotos', rutasFotos(pool));

  app.use((req, res) => res.status(404).json({ error: 'no_encontrado' }));
  // Manejador final: registra el error y no filtra detalles internos al cliente.
  // eslint-disable-next-line no-unused-vars
  app.use((err, req, res, next) => {
    if (err.type === 'entity.parse.failed') return res.status(400).json({ error: 'json_invalido' });
    if (err.type === 'entity.too.large') return res.status(413).json({ error: 'cuerpo_muy_grande' });
    console.error(err);
    res.status(500).json({ error: 'error_interno' });
  });
  return app;
}

module.exports = { crearApp };
