const http = require('http');
const httpProxy = require('http-proxy');

const WSL_HOST = process.env.WSL_HOST || '172.21.223.240';
const APP_TARGET = `http://${WSL_HOST}:9663`;
const WS_TARGET = `http://${WSL_HOST}:9664`;
const PORT = Number(process.env.PORT || 9777);

const proxy = httpProxy.createProxyServer({
  ws: true,
  xfwd: true,
  changeOrigin: false,
});

function isWsLikeRequest(req = {}) {
  const url = req.url || '';
  const upgrade = String(req.headers?.upgrade || '').toLowerCase();
  return (
    upgrade === 'websocket' ||
    url.includes('/socket/') ||
    url.startsWith('/play/') ||
    url.startsWith('/watch/')
  );
}

function pickTarget(req = {}) {
  return isWsLikeRequest(req) ? WS_TARGET : APP_TARGET;
}

proxy.on('error', (err, req, res) => {
  const target = pickTarget(req || {});
  const msg = `proxy error for ${req?.method || 'UPGRADE'} ${req?.url || ''} -> ${target}: ${err.message}`;
  console.error(msg);
  if (res && typeof res.writeHead === 'function') {
    if (!res.headersSent) {
      res.writeHead(502, { 'Content-Type': 'text/plain; charset=utf-8' });
    }
    if (!res.writableEnded) res.end('Bad gateway');
  } else if (res && typeof res.destroy === 'function') {
    res.destroy();
  }
});

const server = http.createServer((req, res) => {
  const target = pickTarget(req);
  console.log(`HTTP ${req.method} ${req.url} -> ${target}${req.headers?.upgrade ? ` (upgrade=${req.headers.upgrade})` : ''}`);
  proxy.web(req, res, { target });
});

server.on('upgrade', (req, socket, head) => {
  console.log(`UPGRADE ${req.method} ${req.url} -> ${WS_TARGET}`);
  proxy.ws(req, socket, head, { target: WS_TARGET });
});

server.listen(PORT, '127.0.0.1', () => {
  console.log(`quick proxy listening on http://127.0.0.1:${PORT}`);
  console.log(`app target: ${APP_TARGET}`);
  console.log(`ws target: ${WS_TARGET}`);
});
