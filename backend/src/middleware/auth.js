const crypto = require('node:crypto');

const hashToken = (token) => crypto.createHash('sha256').update(token).digest('hex');

// Autenticación por token de finca: "Authorization: Bearer <token>".
// El token solo se guarda como hash SHA-256, nunca en texto plano.
function requiereFinca(pool) {
  return async (req, res, next) => {
    const cabecera = req.get('authorization') || '';
    const [tipo, token] = cabecera.split(' ');
    if (tipo !== 'Bearer' || !token) {
      return res.status(401).json({ error: 'no_autenticado', mensaje: 'Falta el token de la finca' });
    }
    try {
      const { rows } = await pool.query('SELECT id, nombre FROM fincas WHERE token_hash = $1', [hashToken(token)]);
      if (!rows.length) return res.status(401).json({ error: 'token_invalido', mensaje: 'Token no reconocido' });
      req.finca = rows[0];
      next();
    } catch (err) {
      next(err);
    }
  };
}

module.exports = { requiereFinca, hashToken };
