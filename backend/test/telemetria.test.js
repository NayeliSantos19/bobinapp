// Pruebas de la analítica anónima y de las protecciones básicas, contra PostgreSQL real.
const { test, before, after } = require('node:test');
const assert = require('node:assert/strict');
const crypto = require('node:crypto');
const request = require('supertest');
const { crearPool } = require('../src/db');
const { crearApp } = require('../src/app');
const { migrar } = require('../scripts/migrate');

let pool;
let app;
const ADMIN = 'clave-admin-de-prueba';

before(async () => {
  pool = crearPool(process.env.DATABASE_URL_TEST || process.env.DATABASE_URL);
  await pool.query('DROP SCHEMA public CASCADE; CREATE SCHEMA public;');
  await migrar(pool);
  app = crearApp(pool, { adminToken: ADMIN });
});
after(() => pool.end());

const ahora = () => Date.now();
const lote = (instalacion, eventos) => ({ instalacion, appVersion: '1.1.0', android: 34, eventos });

test('guarda eventos anónimos y descarta fechas absurdas', async () => {
  const inst = crypto.randomUUID();
  const { body } = await request(app).post('/api/telemetria').send(lote(inst, [
    { evento: 'pantalla', pantalla: 'panel', ocurridoEn: ahora() },
    { evento: 'inicio_app', datos: { ms: 850 }, ocurridoEn: ahora() },
    { evento: 'pantalla', pantalla: 'hato', ocurridoEn: ahora() - 90 * 86_400_000 },
  ])).expect(202);
  assert.deepEqual(body, { recibidos: 2, descartados: 1 });
  const { rows } = await pool.query('SELECT evento, pantalla, app_version, android FROM telemetria WHERE instalacion = $1 ORDER BY id', [inst]);
  assert.deepEqual(rows.map((r) => r.evento), ['pantalla', 'inicio_app']);
  assert.equal(rows[0].app_version, '1.1.0');
  assert.equal(rows[0].android, 34);
});

test('rechaza lotes mal formados o con textos que no son identificadores', async () => {
  const inst = crypto.randomUUID();
  await request(app).post('/api/telemetria').send(lote('no-es-uuid', [{ evento: 'x1', ocurridoEn: ahora() }])).expect(400);
  await request(app).post('/api/telemetria').send(lote(inst, [{ evento: 'Correo de Juan', ocurridoEn: ahora() }])).expect(400);
  await request(app).post('/api/telemetria').send(lote(inst, [{ evento: 'error', datos: { mensaje: 'x'.repeat(500) }, ocurridoEn: ahora() }])).expect(400);
  await request(app).post('/api/telemetria').send(lote(inst, [])).expect(400);
});

test('el resumen exige la clave de administrador y agrega los datos', async () => {
  const a = crypto.randomUUID();
  const b = crypto.randomUUID();
  await request(app).post('/api/telemetria').send(lote(a, [
    { evento: 'pantalla', pantalla: 'alertas', ocurridoEn: ahora() },
    { evento: 'pantalla', pantalla: 'alertas', ocurridoEn: ahora() },
    { evento: 'error', pantalla: 'escaner', datos: { tipo: 'IllegalStateException', lugar: 'EscanerViewModel.kt:42' }, ocurridoEn: ahora() },
    { evento: 'inicio_app', datos: { ms: 1200 }, ocurridoEn: ahora() },
  ])).expect(202);
  await request(app).post('/api/telemetria').send(lote(b, [
    { evento: 'pantalla', pantalla: 'alertas', ocurridoEn: ahora() },
    { evento: 'reporte_pdf', ocurridoEn: ahora() },
  ])).expect(202);

  await request(app).get('/api/telemetria/resumen').expect(401);
  await request(app).get('/api/telemetria/resumen').set('authorization', 'Bearer otra-clave').expect(401);
  const { body } = await request(app).get('/api/telemetria/resumen?dias=7').set('authorization', `Bearer ${ADMIN}`).expect(200);
  assert.ok(body.instalacionesActivas >= 2);
  const alertas = body.pantallas.find((p) => p.pantalla === 'alertas');
  assert.equal(alertas.vistas, 3);
  assert.equal(alertas.instalaciones, 2);
  assert.equal(body.errores[0].tipo, 'IllegalStateException');
  assert.ok(body.eventos.some((e) => e.evento === 'reporte_pdf'));
  assert.ok(body.inicioApp.p50Ms > 0);
});

test('sin ADMIN_TOKEN configurado el resumen no existe', async () => {
  const sinAdmin = crearApp(pool, { adminToken: '' });
  await request(sinAdmin).get('/api/telemetria/resumen').set('authorization', 'Bearer algo').expect(404);
});

test('el usuario puede borrar los datos de su instalación', async () => {
  const inst = crypto.randomUUID();
  await request(app).post('/api/telemetria').send(lote(inst, [{ evento: 'pantalla', pantalla: 'panel', ocurridoEn: ahora() }])).expect(202);
  const { body } = await request(app).delete(`/api/telemetria/${inst}`).expect(200);
  assert.equal(body.borrados, 1);
});

test('cabeceras de seguridad y límite de peticiones', async () => {
  const limitada = crearApp(pool, { limiteTelemetria: 2, limiteFincas: 1 });
  const res = await request(limitada).get('/api/salud');
  assert.equal(res.headers['x-content-type-options'], 'nosniff');
  assert.equal(res.headers['x-frame-options'], 'DENY');
  assert.equal(res.headers['x-powered-by'], undefined);

  const inst = crypto.randomUUID();
  const ev = lote(inst, [{ evento: 'pantalla', pantalla: 'panel', ocurridoEn: ahora() }]);
  await request(limitada).post('/api/telemetria').send(ev).expect(202);
  await request(limitada).post('/api/telemetria').send(ev).expect(202);
  const bloqueada = await request(limitada).post('/api/telemetria').send(ev).expect(429);
  assert.ok(Number(bloqueada.headers['retry-after']) > 0);

  await request(limitada).post('/api/fincas').send({ nombre: 'Una' }).expect(201);
  await request(limitada).post('/api/fincas').send({ nombre: 'Otra' }).expect(429);
});
