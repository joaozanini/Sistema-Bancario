const cepService = require('../services/cep.service');
const { responderErro } = require('../services/erro.service');

// R1: a tela de autocadastro preenche o endereço a partir do CEP
async function consultar(req, res) {
  const cep = String(req.params.cep).replace(/\D/g, '');
  if (cep.length !== 8) {
    return responderErro(res, 400, 'CEP deve ter 8 dígitos.');
  }

  try {
    const endereco = await cepService.buscar(cep);
    return res.json({ ...endereco, _links: { self: { href: `/cep/${cep}` } } });
  } catch (err) {
    if (err instanceof cepService.CepNaoEncontradoError) {
      return responderErro(res, 404, err.message);
    }
    // ViaCEP fora do ar ou sem resposta no prazo: a tela segue com o preenchimento manual
    console.error('Falha ao consultar o CEP:', err.message);
    return responderErro(res, 502, 'Não foi possível consultar o CEP.');
  }
}

module.exports = { consultar };
