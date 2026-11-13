const express = require('express');
const cors = require('cors');
const healthRoutes = require('./routes/health.routes');
const rebootRoutes = require('./routes/reboot.routes');
const authRoutes = require('./routes/auth.routes');
const jobsRoutes = require('./routes/jobs.routes');
const clientesRoutes = require('./routes/clientes.routes');
const solicitacoesRoutes = require('./routes/solicitacoes.routes');
const relatorioRoutes = require('./routes/relatorio.routes');
const proxyRoutes = require('./routes/proxy.routes');
const { reescreverLinksDeRespostaJson } = require('./middlewares/links.middleware');
const { descartarIdentidadeDoCliente } = require('./middlewares/auth.middleware');
const { rotaNaoEncontrada, tratarErro } = require('./middlewares/erro.middleware');

const app = express();

// Location vai exposto para o front poder ler o endereço de polling do job (202)
app.use(cors({ origin: process.env.CORS_ORIGIN || '*', exposedHeaders: ['Location'] }));
app.use(express.json());
app.use(descartarIdentidadeDoCliente);
app.use(reescreverLinksDeRespostaJson);

app.use(healthRoutes);
app.use(rebootRoutes);
app.use(authRoutes);
app.use(jobsRoutes);
app.use(clientesRoutes);
app.use(solicitacoesRoutes);
app.use(relatorioRoutes);
app.use(proxyRoutes);

app.use(rotaNaoEncontrada);
app.use(tratarErro);

module.exports = app;
