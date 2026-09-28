const { crearPool } = require('./db');
const { crearApp } = require('./app');
const { migrar } = require('../scripts/migrate');

async function iniciar() {
  const pool = crearPool();
  const aplicadas = await migrar(pool);
  if (aplicadas.length) console.log(`Migraciones aplicadas: ${aplicadas.join(', ')}`);
  const puerto = Number(process.env.PORT) || 3000;
  const servidor = crearApp(pool).listen(puerto, () => console.log(`Bobinapp API escuchando en :${puerto}`));
  const cerrar = () => servidor.close(() => pool.end().then(() => process.exit(0)));
  process.on('SIGTERM', cerrar);
  process.on('SIGINT', cerrar);
}

iniciar().catch((e) => {
  console.error('No se pudo iniciar la API:', e.message);
  process.exit(1);
});
