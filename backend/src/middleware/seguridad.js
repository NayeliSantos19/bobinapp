// Cabeceras de seguridad y un limitador de peticiones sencillo (en memoria, por IP).
// Para varias instancias del servidor habría que mover el contador a Redis o PostgreSQL.

function cabecerasSeguras(req, res, next) {
  res.set({
    'X-Content-Type-Options': 'nosniff',
    'X-Frame-Options': 'DENY',
    'Referrer-Policy': 'no-referrer',
    'Cross-Origin-Resource-Policy': 'same-site',
  });
  // Solo tiene sentido detrás de HTTPS (Render, por ejemplo); en local se omite.
  if (req.secure) res.set('Strict-Transport-Security', 'max-age=31536000; includeSubDomains');
  next();
}

function limitar({ ventanaMs, max }) {
  const cuentas = new Map();
  return (req, res, next) => {
    const ahora = Date.now();
    const clave = req.ip;
    let c = cuentas.get(clave);
    if (!c || ahora - c.inicio > ventanaMs) {
      c = { inicio: ahora, n: 0 };
      cuentas.set(clave, c);
    }
    c.n += 1;
    if (cuentas.size > 10_000) {
      for (const [k, v] of cuentas) if (ahora - v.inicio > ventanaMs) cuentas.delete(k);
    }
    if (c.n > max) {
      res.set('Retry-After', String(Math.ceil((c.inicio + ventanaMs - ahora) / 1000)));
      return res.status(429).json({ error: 'demasiadas_peticiones' });
    }
    next();
  };
}

module.exports = { cabecerasSeguras, limitar };
