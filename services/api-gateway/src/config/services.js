module.exports = {
  contaServiceUrl: process.env.MS_CONTA_URL || 'http://localhost:8080',
  authServiceUrl: process.env.MS_AUTH_URL || 'http://localhost:8081',
  clienteServiceUrl: process.env.MS_CLIENTE_URL || 'http://localhost:8082',
  gerenteServiceUrl: process.env.MS_GERENTE_URL || 'http://localhost:8083',
};
