const express = require('express');
const { requiereFinca } = require('../middleware/auth');

const TIPOS = ['image/jpeg', 'image/png', 'image/webp'];
const UUID = /^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$/i;

// Revisa la firma real del archivo, no solo la cabecera Content-Type que manda el cliente.
function tipoReal(buf) {
  if (buf.length > 3 && buf[0] === 0xff && buf[1] === 0xd8 && buf[2] === 0xff) return 'image/jpeg';
  if (buf.length > 8 && buf.subarray(0, 8).equals(Buffer.from([0x89, 0x50, 0x4e, 0x47, 0x0d, 0x0a, 0x1a, 0x0a]))) return 'image/png';
  if (buf.length > 12 && buf.toString('ascii', 0, 4) === 'RIFF' && buf.toString('ascii', 8, 12) === 'WEBP') return 'image/webp';
  return null;
}

module.exports = function rutasFotos(pool) {
  const r = express.Router();
  r.use(requiereFinca(pool));

  r.param('animalId', (req, res, next, id) => (UUID.test(id) ? next() : res.status(400).json({ error: 'id_invalido' })));

  // Sube o reemplaza la foto. Cabecera obligatoria: X-Foto-Actualizada-En (epoch ms del dispositivo).
  r.put('/:animalId', express.raw({ type: TIPOS, limit: '3mb' }), async (req, res, next) => {
    const version = Number(req.get('x-foto-actualizada-en'));
    if (!Number.isSafeInteger(version) || version <= 0) return res.status(400).json({ error: 'falta_version' });
    if (!Buffer.isBuffer(req.body) || req.body.length === 0) return res.status(415).json({ error: 'tipo_no_soportado' });
    const tipo = tipoReal(req.body);
    if (!tipo) return res.status(415).json({ error: 'no_es_imagen' });
    try {
      const animal = await pool.query('SELECT 1 FROM animales WHERE id = $1 AND finca_id = $2', [req.params.animalId, req.finca.id]);
      if (!animal.rowCount) return res.status(404).json({ error: 'animal_no_encontrado' });
      const { rowCount } = await pool.query(
        `INSERT INTO fotos_animal (animal_id, finca_id, tipo_mime, contenido, bytes, actualizada_en)
         VALUES ($1, $2, $3, $4, $5, $6)
         ON CONFLICT (animal_id) DO UPDATE SET tipo_mime = EXCLUDED.tipo_mime, contenido = EXCLUDED.contenido,
           bytes = EXCLUDED.bytes, actualizada_en = EXCLUDED.actualizada_en
         WHERE fotos_animal.finca_id = EXCLUDED.finca_id AND fotos_animal.actualizada_en < EXCLUDED.actualizada_en`,
        [req.params.animalId, req.finca.id, tipo, req.body, req.body.length, version],
      );
      // 409: el servidor ya tiene una foto igual o más nueva. El teléfono la descargará en el siguiente pull.
      res.status(rowCount ? 204 : 409).end();
    } catch (err) {
      next(err);
    }
  });

  r.get('/:animalId', async (req, res, next) => {
    try {
      const { rows } = await pool.query(
        'SELECT tipo_mime, contenido, actualizada_en FROM fotos_animal WHERE animal_id = $1 AND finca_id = $2',
        [req.params.animalId, req.finca.id],
      );
      if (!rows.length) return res.status(404).json({ error: 'sin_foto' });
      res.set({
        'Content-Type': rows[0].tipo_mime,
        'X-Foto-Actualizada-En': String(rows[0].actualizada_en),
        'Cache-Control': 'private, max-age=0',
      });
      res.send(rows[0].contenido);
    } catch (err) {
      next(err);
    }
  });

  return r;
};

module.exports.tipoReal = tipoReal;
