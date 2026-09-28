const { test } = require('node:test');
const assert = require('node:assert/strict');
const { parsearRespuesta } = require('../src/routes/identificar');

test('extrae el JSON aunque el modelo agregue texto alrededor', () => {
  const r = parsearRespuesta('Aquí está:\n{"esBovino":true,"razaId":"holstein","razaNombre":"Holstein","confianza":91,"rasgos":["Manchas negras"]}\nFin');
  assert.equal(r.razaId, 'holstein');
  assert.equal(r.confianza, 91);
  assert.deepEqual(r.rasgos, ['Manchas negras']);
});

test('descarta ids que no existen en el catálogo y acota la confianza', () => {
  const r = parsearRespuesta('{"razaId":"unicornio","razaNombre":"Unicornio","confianza":250,"alternativas":[{"razaId":"jersey","confianza":-4}]}');
  assert.equal(r.razaId, null);
  assert.equal(r.razaNombre, 'Unicornio');
  assert.equal(r.confianza, 100);
  assert.equal(r.alternativas[0].razaId, 'jersey');
  assert.equal(r.alternativas[0].confianza, 0);
});

test('falla si no hay JSON', () => {
  assert.throws(() => parsearRespuesta('No sé'), /JSON/);
});
