// reescreve os _links dos MSs com o endereço do Gateway

function baseUrl(req) {
  if (process.env.GATEWAY_PUBLIC_URL) {
    return process.env.GATEWAY_PUBLIC_URL.replace(/\/+$/, '');
  }
  const protocolo = req.headers['x-forwarded-proto'] || 'http';
  return `${protocolo}://${req.headers.host}`;
}

// troca o host do href pelo do Gateway
function reescreverHref(href, base) {
  if (typeof href !== 'string') {
    return href;
  }
  try {
    const url = new URL(href);
    return `${base}${url.pathname}${url.search}${url.hash}`;
  } catch {
    // href relativo recebe a base do Gateway
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
