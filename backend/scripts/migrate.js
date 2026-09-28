// Aplica las migraciones SQL en orden y registra cuáles ya corrieron.
const fs = require('node:fs');
const path = require('node:path');
const { crearPool } = require('../src/db');

async function migrar(pool) {
  await pool.query(`CREATE TABLE IF NOT EXISTS migraciones (
    nombre TEXT PRIMARY KEY,
    aplicada_en TIMESTAMPTZ NOT NULL DEFAULT now()
  )`);
  const dir = path.join(__dirname, '..', 'migrations');
  const archivos = fs.readdirSync(dir).filter((f) => f.endsWith('.sql')).sort();
  const { rows } = await pool.query('SELECT nombre FROM migraciones');
  const hechas = new Set(rows.map((r) => r.nombre));
  const aplicadas = [];
  for (const archivo of archivos) {
    if (hechas.has(archivo)) continue;
    const sql = fs.readFileSync(path.join(dir, archivo), 'utf8');
    const client = await pool.connect();
    try {
      await client.query('BEGIN');
      await client.query(sql);
      await client.query('INSERT INTO migraciones (nombre) VALUES ($1)', [archivo]);
      await client.query('COMMIT');
      aplicadas.push(archivo);
    } catch (err) {
      await client.query('ROLLBACK');
      throw new Error(`Falló la migración ${archivo}: ${err.message}`);
    } finally {
      client.release();
    }
  }
  return aplicadas;
}

module.exports = { migrar };

if (require.main === module) {
  const pool = crearPool();
  migrar(pool)
    .then((a) => console.log(a.length ? `Migraciones aplicadas: ${a.join(', ')}` : 'La base ya está al día'))
    .catch((e) => {
      console.error(e.message);
      process.exitCode = 1;
    })
    .finally(() => pool.end());
}
