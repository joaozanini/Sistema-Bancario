const express = require('express');
const cors = require('cors');
const healthRoutes = require('./routes/health.routes');
const rebootRoutes = require('./routes/reboot.routes');
const proxyRoutes = require('./routes/proxy.routes');

const app = express();

app.use(cors({ origin: process.env.CORS_ORIGIN || '*' }));
app.use(express.json());

app.use(healthRoutes);
app.use(rebootRoutes);
app.use(proxyRoutes);

module.exports = app;
