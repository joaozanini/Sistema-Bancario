// JSON e não o texto "OK": é o formato do contrato e o que a aplicação de teste lê
function health(req, res) {
  res.json({ status: 'UP' });
}

module.exports = { health };
