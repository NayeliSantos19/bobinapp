const express = require('express');
const { pushSchema } = require('../validacion');
const { requiereFinca } = require('../middleware/auth');

// Mapeo entre el JSON del teléfono (camelCase) y las columnas de PostgreSQL.
const CAMPOS_ANIMAL = [
  ['arete', 'arete'], ['nombre', 'nombre'], ['sexo', 'sexo'], ['razaId', 'raza_id'], ['razaTexto', 'raza_texto'],
  ['nacimiento', 'nacimiento'], ['madreId', 'madre_id'], ['padreId', 'padre_id'], ['padreExterno', 'padre_externo'],
  ['castrado', 'castrado'], ['estado', 'estado'], ['notas', 'notas'], ['eliminado', 'eliminado'],
  ['fotoActualizadaEn', 'foto_actualizada_en'],
];
const CAMPOS_EVENTO = [
  ['animalId', 'animal_id'], ['tipo', 'tipo'], ['fecha', 'fecha'], ['kg', 'kg'], ['litros', 'litros'],
  ['producto', 'producto'], ['dosis', 'dosis'], ['diagnostico', 'diagnostico'], ['proximaFecha', 'proxima_fecha'],
  ['retiroDias', 'retiro_dias'], ['costo', 'costo'], ['toro', 'toro'], ['toroId', 'toro_id'], ['tecnico', 'tecnico'],
  ['resultado', 'resultado'], ['criaId', 'cria_id'], ['nota', 'nota'], ['eliminado', 'eliminado'],
];

// Construye un UPSERT con regla last-writer-wins:
// solo sobrescribe si el cambio entrante es más reciente Y la fila pertenece a la misma finca.
function sqlUpsert(tabla, campos) {
  const cols = ['id', 'finca_id', ...campos.map(([, c]) => c), 'actualizado_cliente'];
  const valores = cols.map((_, i) => `$${i + 1}`);
  const sets = [...campos.map(([, c]) => `${c} = EXCLUDED.${c}`), 'actualizado_cliente = EXCLUDED.actualizado_cliente',
    "version_servidor = nextval('sync_version_seq')"];
  return `INSERT INTO ${tabla} (${cols.join(', ')}) VALUES (${valores.join(', ')})
    ON CONFLICT (id) DO UPDATE SET ${sets.join(', ')}
    WHERE ${tabla}.finca_id = EXCLUDED.finca_id
      AND ${tabla}.actualizado_cliente < EXCLUDED.actualizado_cliente
    RETURNING id`;
}
const SQL_ANIMAL = sqlUpsert('animales', CAMPOS_ANIMAL);
const SQL_EVENTO = sqlUpsert('eventos', CAMPOS_EVENTO);

const aFila = (obj, fincaId, campos) => [obj.id, fincaId, ...campos.map(([k]) => obj[k] ?? null), obj.actualizadoEn];
const aDto = (fila, campos) => {
  const o = { id: fila.id };
  for (const [k, c] of campos) o[k] = fila[c];
  o.actualizadoEn = fila.actualizado_cliente;
  o.version = fila.version_servidor;
  return o;
};

module.exports = function rutasSync(pool) {
  const r = express.Router();
  r.use(requiereFinca(pool));

  // PUSH: el teléfono sube su cola de cambios pendientes.
  r.post('/push', async (req, res, next) => {
    const datos = pushSchema.safeParse(req.body);
    if (!datos.success) return res.status(400).json({ error: 'datos_invalidos', detalles: datos.error.issues.slice(0, 20) });
    const { animales, eventos } = datos.data;
    const client = await pool.connect();
    try {
      await client.query('BEGIN');
      // Serializa los push de una misma finca para que version_servidor se confirme en orden
      // y ningún pull concurrente se salte un cambio.
      await client.query('SELECT pg_advisory_xact_lock(hashtext($1))', [req.finca.id]);
      const conflictos = { animales: [], eventos: [] };
      let aplicadosA = 0;
      let aplicadosE = 0;
      for (const a of animales) {
        const { rowCount } = await client.query(SQL_ANIMAL, aFila(a, req.finca.id, CAMPOS_ANIMAL));
        if (rowCount) aplicadosA++; else conflictos.animales.push(a.id);
      }
      for (const e of eventos) {
        const { rowCount } = await client.query(SQL_EVENTO, aFila(e, req.finca.id, CAMPOS_EVENTO));
        if (rowCount) aplicadosE++; else conflictos.eventos.push(e.id);
      }
      await client.query('COMMIT');
      res.json({ aplicados: { animales: aplicadosA, eventos: aplicadosE }, conflictos });
    } catch (err) {
      await client.query('ROLLBACK').catch(() => {});
      next(err);
    } finally {
      client.release();
    }
  });

  // PULL: devuelve todo lo que cambió después del cursor "desde".
  r.get('/cambios', async (req, res, next) => {
    const desde = Number.parseInt(req.query.desde ?? '0', 10);
    const limite = Math.min(Math.max(Number.parseInt(req.query.limite ?? '500', 10) || 500, 1), 1000);
    if (!Number.isFinite(desde) || desde < 0) return res.status(400).json({ error: 'cursor_invalido' });
    try {
      const q = (tabla) => pool.query(
        `SELECT * FROM ${tabla} WHERE finca_id = $1 AND version_servidor > $2 ORDER BY version_servidor LIMIT $3`,
        [req.finca.id, desde, limite],
      );
      const [a, e] = await Promise.all([q('animales'), q('eventos')]);
      // Mezcla ambas tablas por versión y corta en "limite" para que el cursor nunca salte filas.
      const todo = [
        ...a.rows.map((f) => ({ t: 'a', v: f.version_servidor, f })),
        ...e.rows.map((f) => ({ t: 'e', v: f.version_servidor, f })),
      ].sort((x, y) => x.v - y.v);
      const pagina = todo.slice(0, limite);
      const cursor = pagina.length ? pagina[pagina.length - 1].v : desde;
      res.json({
        animales: pagina.filter((x) => x.t === 'a').map((x) => aDto(x.f, CAMPOS_ANIMAL)),
        eventos: pagina.filter((x) => x.t === 'e').map((x) => aDto(x.f, CAMPOS_EVENTO)),
        cursor,
        hayMas: todo.length > limite || a.rows.length === limite || e.rows.length === limite,
      });
    } catch (err) {
      next(err);
    }
  });

  return r;
};
