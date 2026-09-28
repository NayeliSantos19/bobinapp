const express = require('express');
const crypto = require('node:crypto');
const { fincaSchema } = require('../validacion');
const { hashToken, requiereFinca } = require('../middleware/auth');

module.exports = function rutasFincas(pool) {
  const r = express.Router();

  // Crea una finca y devuelve su token una sola vez.
  r.post('/', async (req, res, next) => {
    const datos = fincaSchema.safeParse(req.body);
    if (!datos.success) return res.status(400).json({ error: 'datos_invalidos', detalles: datos.error.issues });
    try {
      const token = crypto.randomBytes(32).toString('hex');
      const { rows } = await pool.query(
        'INSERT INTO fincas (nombre, token_hash) VALUES ($1, $2) RETURNING id, nombre, creada_en',
        [datos.data.nombre, hashToken(token)],
      );
      res.status(201).json({ finca: rows[0], token });
    } catch (err) {
      next(err);
    }
  });

  r.get('/yo', requiereFinca(pool), (req, res) => res.json({ finca: req.finca }));

  return r;
};
