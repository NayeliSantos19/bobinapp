// Pruebas de subida y descarga de fotos contra PostgreSQL real.
const { test, before, after } = require('node:test');
const assert = require('node:assert/strict');
const crypto = require('node:crypto');
const request = require('supertest');
const { crearPool } = require('../src/db');
const { crearApp } = require('../src/app');
const { migrar } = require('../scripts/migrate');
const { tipoReal } = require('../src/routes/fotos');

let pool;
let app;

before(async () => {
  pool = crearPool(process.env.DATABASE_URL_TEST || process.env.DATABASE_URL);
  await pool.query('DROP SCHEMA public CASCADE; CREATE SCHEMA public;');
  await migrar(pool);
  app = crearApp(pool);
});
after(() => pool.end());

// JPEG mínimo: basta con la firma FF D8 FF para la validación del servidor.
const JPEG = Buffer.concat([Buffer.from([0xff, 0xd8, 0xff, 0xe0]), crypto.randomBytes(200)]);

async function fincaConAnimal() {
  const { body } = await request(app).post('/api/fincas').send({ nombre: 'Finca Fotos' }).expect(201);
  const auth = ['authorization', `Bearer ${body.token}`];
  const id = crypto.randomUUID();
  await request(app).post('/api/sync/push').set(...auth).send({
    animales: [{ id, arete: 'SV-9', sexo: 'H', nacimiento: '2022-01-01', actualizadoEn: 1000, fotoActualizadaEn: 5000 }],
  }).expect(200);
  return { auth, id };
}

test('detecta el tipo real por la firma del archivo', () => {
  assert.equal(tipoReal(JPEG), 'image/jpeg');
  assert.equal(tipoReal(Buffer.from('<svg></svg>')), null);
});

test('sube y descarga la foto de un animal', async () => {
  const { auth, id } = await fincaConAnimal();
  await request(app).put(`/api/fotos/${id}`).set(...auth).set('content-type', 'image/jpeg')
    .set('x-foto-actualizada-en', '5000').send(JPEG).expect(204);
  const r = await request(app).get(`/api/fotos/${id}`).set(...auth).buffer(true)
    .parse((res, cb) => { const d = []; res.on('data', (c) => d.push(c)); res.on('end', () => cb(null, Buffer.concat(d))); })
    .expect(200);
  assert.equal(r.headers['content-type'], 'image/jpeg');
  assert.equal(r.headers['x-foto-actualizada-en'], '5000');
  assert.ok(Buffer.compare(r.body, JPEG) === 0);

  const pull = await request(app).get('/api/sync/cambios?desde=0').set(...auth);
  assert.equal(pull.body.animales[0].fotoActualizadaEn, 5000);
});

test('una foto más vieja no reemplaza a una más nueva', async () => {
  const { auth, id } = await fincaConAnimal();
  const subir = (v) => request(app).put(`/api/fotos/${id}`).set(...auth).set('content-type', 'image/jpeg').set('x-foto-actualizada-en', String(v)).send(JPEG);
  await subir(5000).expect(204);
  await subir(4000).expect(409);
  await subir(6000).expect(204);
});

test('rechaza archivos que no son imagen aunque digan serlo', async () => {
  const { auth, id } = await fincaConAnimal();
  await request(app).put(`/api/fotos/${id}`).set(...auth).set('content-type', 'image/jpeg')
    .set('x-foto-actualizada-en', '5000').send(Buffer.from('esto no es una foto')).expect(415);
});

test('otra finca no puede ver ni subir fotos de un animal ajeno', async () => {
  const { auth, id } = await fincaConAnimal();
  await request(app).put(`/api/fotos/${id}`).set(...auth).set('content-type', 'image/jpeg').set('x-foto-actualizada-en', '5000').send(JPEG).expect(204);
  const otra = await request(app).post('/api/fincas').send({ nombre: 'Intrusa' }).expect(201);
  const authOtra = ['authorization', `Bearer ${otra.body.token}`];
  await request(app).get(`/api/fotos/${id}`).set(...authOtra).expect(404);
  await request(app).put(`/api/fotos/${id}`).set(...authOtra).set('content-type', 'image/jpeg').set('x-foto-actualizada-en', '9999').send(JPEG).expect(404);
});
