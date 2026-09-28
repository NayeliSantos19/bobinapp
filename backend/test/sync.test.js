// Pruebas de integración contra un PostgreSQL real (DATABASE_URL de pruebas).
const { test, before, after } = require('node:test');
const assert = require('node:assert/strict');
const crypto = require('node:crypto');
const request = require('supertest');
const { crearPool } = require('../src/db');
const { crearApp } = require('../src/app');
const { migrar } = require('../scripts/migrate');

let pool;
let app;

before(async () => {
  pool = crearPool(process.env.DATABASE_URL_TEST || process.env.DATABASE_URL);
  await pool.query('DROP SCHEMA public CASCADE; CREATE SCHEMA public;');
  await migrar(pool);
  app = crearApp(pool);
});
after(() => pool.end());

const nuevaFinca = async (nombre = 'Finca El Roble') => {
  const r = await request(app).post('/api/fincas').send({ nombre }).expect(201);
  return r.body.token;
};
const animal = (extra = {}) => ({
  id: crypto.randomUUID(), arete: 'SV-0001', nombre: 'Lucero', sexo: 'H', razaId: 'pardo_suizo',
  nacimiento: '2020-03-15', castrado: false, estado: 'activa', eliminado: false, actualizadoEn: 1000, ...extra,
});

test('rechaza peticiones sin token', async () => {
  await request(app).get('/api/sync/cambios').expect(401);
  await request(app).get('/api/sync/cambios').set('authorization', 'Bearer inventado').expect(401);
});

test('push y pull de un animal con sus eventos', async () => {
  const token = await nuevaFinca();
  const a = animal();
  const ev = { id: crypto.randomUUID(), animalId: a.id, tipo: 'PESO', fecha: '2026-09-01', kg: 540.5, actualizadoEn: 1000 };
  const push = await request(app).post('/api/sync/push').set('authorization', `Bearer ${token}`)
    .send({ animales: [a], eventos: [ev] }).expect(200);
  assert.deepEqual(push.body.aplicados, { animales: 1, eventos: 1 });

  const pull = await request(app).get('/api/sync/cambios?desde=0').set('authorization', `Bearer ${token}`).expect(200);
  assert.equal(pull.body.animales.length, 1);
  assert.equal(pull.body.animales[0].nacimiento, '2020-03-15');
  assert.equal(pull.body.eventos[0].kg, 540.5);
  assert.ok(pull.body.cursor > 0);

  const vacio = await request(app).get(`/api/sync/cambios?desde=${pull.body.cursor}`).set('authorization', `Bearer ${token}`);
  assert.equal(vacio.body.animales.length + vacio.body.eventos.length, 0);
});

test('last-writer-wins: un cambio viejo no pisa uno nuevo', async () => {
  const token = await nuevaFinca();
  const a = animal({ actualizadoEn: 2000, nombre: 'Versión nueva' });
  const auth = ['authorization', `Bearer ${token}`];
  await request(app).post('/api/sync/push').set(...auth).send({ animales: [a] }).expect(200);

  const viejo = await request(app).post('/api/sync/push').set(...auth)
    .send({ animales: [{ ...a, nombre: 'Versión vieja', actualizadoEn: 1500 }] }).expect(200);
  assert.deepEqual(viejo.body.conflictos.animales, [a.id]);

  await request(app).post('/api/sync/push').set(...auth)
    .send({ animales: [{ ...a, nombre: 'Versión más nueva', actualizadoEn: 3000 }] }).expect(200);
  const pull = await request(app).get('/api/sync/cambios?desde=0').set(...auth);
  assert.equal(pull.body.animales[0].nombre, 'Versión más nueva');
});

test('una finca no puede ver ni sobrescribir datos de otra', async () => {
  const t1 = await nuevaFinca('Finca A');
  const t2 = await nuevaFinca('Finca B');
  const a = animal({ actualizadoEn: 1000 });
  await request(app).post('/api/sync/push').set('authorization', `Bearer ${t1}`).send({ animales: [a] }).expect(200);

  const intruso = await request(app).post('/api/sync/push').set('authorization', `Bearer ${t2}`)
    .send({ animales: [{ ...a, nombre: 'Robado', actualizadoEn: 9999 }] }).expect(200);
  assert.equal(intruso.body.aplicados.animales, 0);

  const pullB = await request(app).get('/api/sync/cambios?desde=0').set('authorization', `Bearer ${t2}`);
  assert.equal(pullB.body.animales.length, 0);
  const pullA = await request(app).get('/api/sync/cambios?desde=0').set('authorization', `Bearer ${t1}`);
  assert.equal(pullA.body.animales[0].nombre, 'Lucero');
});

test('paginación del pull no se salta cambios', async () => {
  const token = await nuevaFinca();
  const auth = ['authorization', `Bearer ${token}`];
  const animales = Array.from({ length: 7 }, (_, i) => animal({ arete: `SV-${i}` }));
  const eventos = animales.map((a) => ({ id: crypto.randomUUID(), animalId: a.id, tipo: 'CELO', fecha: '2026-09-10', actualizadoEn: 1000 }));
  await request(app).post('/api/sync/push').set(...auth).send({ animales, eventos }).expect(200);

  const vistos = new Set();
  let desde = 0;
  for (let vuelta = 0; vuelta < 10; vuelta++) {
    const r = await request(app).get(`/api/sync/cambios?desde=${desde}&limite=3`).set(...auth).expect(200);
    [...r.body.animales, ...r.body.eventos].forEach((x) => vistos.add(x.id));
    desde = r.body.cursor;
    if (!r.body.hayMas) break;
  }
  assert.equal(vistos.size, 14);
});

test('valida los datos de entrada', async () => {
  const token = await nuevaFinca();
  const r = await request(app).post('/api/sync/push').set('authorization', `Bearer ${token}`)
    .send({ animales: [animal({ sexo: 'X' })] }).expect(400);
  assert.equal(r.body.error, 'datos_invalidos');
});
