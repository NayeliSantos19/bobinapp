const { Pool, types } = require('pg');

// Devuelve DATE como texto 'YYYY-MM-DD' (sin zona horaria) y NUMERIC/BIGINT como número.
types.setTypeParser(1082, (v) => v);
types.setTypeParser(1700, (v) => (v === null ? null : Number(v)));
types.setTypeParser(20, (v) => (v === null ? null : Number(v)));

function crearPool(connectionString = process.env.DATABASE_URL) {
  if (!connectionString) {
    throw new Error('Falta la variable DATABASE_URL (ver .env.example)');
  }
  // Bases administradas fuera de la red privada (Neon, Supabase, Render externo) exigen TLS.
  const ssl = process.env.DATABASE_SSL === 'true' ? { rejectUnauthorized: false } : undefined;
  return new Pool({ connectionString, max: 10, ssl });
}

module.exports = { crearPool };
