const express = require('express');
const razas = require('../../data/razas.json');
const { identificarSchema } = require('../validacion');
const { requiereFinca } = require('../middleware/auth');

const CATALOGO = razas.map((r) => `${r.id}: ${r.nombre} — ${r.rasgos.slice(0, 3).join('; ')}`).join('\n');
const IDS = new Set(razas.map((r) => r.id));

const PROMPT = `Eres un zootecnista experto en identificar razas bovinas a partir de fotos. Analiza la imagen.
Catálogo (id: nombre — rasgos clave):
${CATALOGO}

Reglas:
- Si la imagen no muestra un bovino, responde esBovino false.
- Usa el id exacto del catálogo en razaId. Si es una raza fuera del catálogo, razaId null y su nombre en razaNombre.
- Si parece un cruce, esCruce true y lista las razas en cruceDe.
- confianza de 0 a 100, conservadora con fotos borrosas o parciales.
- rasgos: 3 a 5 rasgos visibles que apoyan la respuesta, en español.
- alternativas: hasta 3 razas posibles con su confianza.
Responde SOLO con JSON:
{"esBovino":true,"razaId":"brahman","razaNombre":"Brahman","confianza":80,"esCruce":false,"cruceDe":[],"rasgos":["..."],"alternativas":[{"razaId":"nelore","razaNombre":"Nelore","confianza":10}],"nota":"consejo breve"}`;

// Extrae el primer objeto JSON de la respuesta del modelo.
function parsearRespuesta(texto) {
  const ini = texto.indexOf('{');
  const fin = texto.lastIndexOf('}');
  if (ini < 0 || fin <= ini) throw new Error('La respuesta no contiene JSON');
  const r = JSON.parse(texto.slice(ini, fin + 1));
  const confianza = Math.max(0, Math.min(100, Number(r.confianza) || 0));
  return {
    esBovino: r.esBovino !== false,
    razaId: IDS.has(r.razaId) ? r.razaId : null,
    razaNombre: String(r.razaNombre || ''),
    confianza,
    esCruce: !!r.esCruce,
    cruceDe: Array.isArray(r.cruceDe) ? r.cruceDe.map(String).slice(0, 4) : [],
    rasgos: Array.isArray(r.rasgos) ? r.rasgos.map(String).slice(0, 6) : [],
    alternativas: Array.isArray(r.alternativas)
      ? r.alternativas.slice(0, 3).map((a) => ({
        razaId: IDS.has(a.razaId) ? a.razaId : null,
        razaNombre: String(a.razaNombre || ''),
        confianza: Math.max(0, Math.min(100, Number(a.confianza) || 0)),
      }))
      : [],
    nota: String(r.nota || ''),
  };
}

// La llave de Anthropic vive solo en el servidor; la app nunca la ve.
module.exports = function rutasIdentificar(pool, { fetchImpl = fetch, env = process.env } = {}) {
  const r = express.Router();
  r.use(requiereFinca(pool));

  r.post('/', async (req, res, next) => {
    if (!env.ANTHROPIC_API_KEY) {
      return res.status(503).json({ error: 'identificacion_no_configurada', mensaje: 'Configura ANTHROPIC_API_KEY en el servidor' });
    }
    const datos = identificarSchema.safeParse(req.body);
    if (!datos.success) return res.status(400).json({ error: 'imagen_invalida', detalles: datos.error.issues });
    try {
      const resp = await fetchImpl('https://api.anthropic.com/v1/messages', {
        method: 'POST',
        headers: {
          'content-type': 'application/json',
          'x-api-key': env.ANTHROPIC_API_KEY,
          'anthropic-version': '2023-06-01',
        },
        body: JSON.stringify({
          model: env.ANTHROPIC_MODEL || 'claude-sonnet-4-5',
          max_tokens: 1024,
          messages: [{
            role: 'user',
            content: [
              { type: 'image', source: { type: 'base64', media_type: datos.data.mediaType, data: datos.data.imagenBase64 } },
              { type: 'text', text: PROMPT },
            ],
          }],
        }),
      });
      if (!resp.ok) {
        const detalle = await resp.text();
        return res.status(502).json({ error: 'servicio_vision', estado: resp.status, detalle: detalle.slice(0, 300) });
      }
      const cuerpo = await resp.json();
      const texto = (cuerpo.content || []).filter((b) => b.type === 'text').map((b) => b.text).join('');
      res.json(parsearRespuesta(texto));
    } catch (err) {
      if (err instanceof SyntaxError || /JSON/.test(err.message)) {
        return res.status(502).json({ error: 'respuesta_ilegible' });
      }
      next(err);
    }
  });

  return r;
};

module.exports.parsearRespuesta = parsearRespuesta;
