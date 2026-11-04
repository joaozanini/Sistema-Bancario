// Enunciado 5.6: os MSs montam os _links com a URL interna deles (ex.: http://localhost:8080)
// e o Gateway reescreve para o próprio endereço, para o front nunca enxergar um MS.

function baseUrl(req) {
  if (process.env.GATEWAY_PUBLIC_URL) {
    return process.env.GATEWAY_PUBLIC_URL.replace(/\/+$/, '');
  }
  const protocolo = req.headers['x-forwarded-proto'] || 'http';
  return `${protocolo}://${req.headers.host}`;
}

// só o caminho e a query interessam: o host vem sempre do Gateway
function reescreverHref(href, base) {
  if (typeof href !== 'string') {
    return href;
  }
  try {
    const url = new URL(href);
    return `${base}${url.pathname}${url.search}${url.hash}`;
  } catch {
    // href relativo: já é um caminho do Gateway
    return href.startsWith('/') ? `${base}${href}` : href;
  }
}

function reescreverLinks(corpo, base) {
  if (Array.isArray(corpo)) {
    return corpo.map((item) => reescreverLinks(item, base));
  }
  if (corpo === null || typeof corpo !== 'object') {
    return corpo;
  }

  const copia = {};
  for (const [chave, valor] of Object.entries(corpo)) {
    if (chave === '_links' && valor && typeof valor === 'object') {
      copia[chave] = Object.fromEntries(
        Object.entries(valor).map(([rel, link]) => [
          rel,
          link && typeof link === 'object' ? { ...link, href: reescreverHref(link.href, base) } : link,
        ]),
      );
    } else {
      copia[chave] = reescreverLinks(valor, base);
    }
  }
  return copia;
}

module.exports = { baseUrl, reescreverLinks, reescreverHref };
