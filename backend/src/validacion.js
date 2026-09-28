// Esquemas de entrada. Todo lo que llega del teléfono se valida antes de tocar la base.
const { z } = require('zod');

const fecha = z.string().regex(/^\d{4}-\d{2}-\d{2}$/, 'Formato de fecha YYYY-MM-DD');
const texto = (max = 200) => z.string().max(max).nullish();
const uuid = z.string().uuid();

const animalSchema = z.object({
  id: uuid,
  arete: z.string().min(1).max(40),
  nombre: texto(80),
  sexo: z.enum(['H', 'M']),
  razaId: texto(40),
  razaTexto: texto(120),
  nacimiento: fecha,
  madreId: uuid.nullish(),
  padreId: uuid.nullish(),
  padreExterno: texto(160),
  castrado: z.boolean().default(false),
  estado: z.enum(['activa', 'vendida', 'muerta', 'descartada']).default('activa'),
  notas: texto(2000),
  fotoActualizadaEn: z.number().int().positive().nullish(),
  eliminado: z.boolean().default(false),
  actualizadoEn: z.number().int().positive(),
});

const eventoSchema = z.object({
  id: uuid,
  animalId: uuid,
  tipo: z.enum(['PESO', 'LECHE', 'VACUNA', 'TRATAMIENTO', 'CELO', 'INSEMINACION', 'PALPACION', 'PARTO', 'DESTETE', 'BAJA', 'NOTA']),
  fecha,
  kg: z.number().positive().max(3000).nullish(),
  litros: z.number().min(0).max(120).nullish(),
  producto: texto(160),
  dosis: texto(80),
  diagnostico: texto(200),
  proximaFecha: fecha.nullish(),
  retiroDias: z.number().int().min(0).max(365).nullish(),
  costo: z.number().min(0).max(1e7).nullish(),
  toro: texto(160),
  toroId: uuid.nullish(),
  tecnico: texto(120),
  resultado: z.enum(['PRENADA', 'VACIA']).nullish(),
  criaId: uuid.nullish(),
  nota: texto(2000),
  eliminado: z.boolean().default(false),
  actualizadoEn: z.number().int().positive(),
});

const pushSchema = z.object({
  animales: z.array(animalSchema).max(500).default([]),
  eventos: z.array(eventoSchema).max(5000).default([]),
});

const fincaSchema = z.object({ nombre: z.string().trim().min(1).max(120) });

const identificarSchema = z.object({
  imagenBase64: z.string().min(100).max(8_000_000),
  mediaType: z.enum(['image/jpeg', 'image/png', 'image/webp']).default('image/jpeg'),
});

module.exports = { animalSchema, eventoSchema, pushSchema, fincaSchema, identificarSchema };
