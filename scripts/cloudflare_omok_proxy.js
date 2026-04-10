const http = require('http');
const net = require('net');
const { URL } = require('url');

const HTTP_TARGET_PORT = 9663;
const WS_TARGET_PORT = 9664;
const LISTEN_PORT = 18080;
const omokSeatCookies = new Map();
const ASSET_VERSION = 'cloudflare-tunnel-20260410-1';

function redirectFixScript(nonce) {
  return `<script nonce="${nonce}">(function(){const NativeWebSocket=window.WebSocket;window.WebSocket=function(url,protocols){const ws=protocols===undefined?new NativeWebSocket(url):new NativeWebSocket(url,protocols);ws.addEventListener('message',ev=>{try{const msg=JSON.parse(String(ev.data));if(msg&&msg.t==='redirect'&&msg.d&&typeof msg.d.url==='string'&&msg.d.url.includes('/dev/omok/claim/')){const c=msg.d.cookie;if(c)document.cookie=encodeURIComponent(c.name)+'='+c.value+'; max-age='+c.maxAge+'; path=/; domain='+location.hostname;location.href='//'+location.host+'/'+msg.d.url.replace(/^\\//,'');}}catch(_){}});return ws;};window.WebSocket.prototype=NativeWebSocket.prototype;Object.setPrototypeOf(window.WebSocket,NativeWebSocket);})();</script>`;
}

function dialogFixScript(nonce) {
  return `<script nonce="${nonce}">(function(){const openDialog=(dialog)=>{try{dialog.parentElement&&dialog.parentElement.classList&&dialog.parentElement.classList.remove('none');if(!dialog.open){if(typeof dialog.showModal==='function')dialog.showModal();else if(typeof dialog.show==='function')dialog.show();}}catch(_){}};const scan=()=>{document.querySelectorAll('dialog').forEach(dialog=>{if(dialog.querySelector('.game-setup')&&!dialog.open)openDialog(dialog);});};const start=()=>{scan();new MutationObserver(scan).observe(document.body,{childList:true,subtree:true});};if(document.readyState==='loading')document.addEventListener('DOMContentLoaded',start,{once:true});else start();})();</script>`;
}

function omokStyleFix(nonce) {
  return `<style nonce="${nonce}">.round__app__board__omok-placeholder__svg-hover-ring{fill:none;stroke:rgba(18,110,61,.88);stroke-width:.68;filter:drop-shadow(0 0 .24rem rgba(40,131,81,.4));pointer-events:none}.round__app__board__omok-placeholder__svg-ghost{stroke-width:.42;pointer-events:none}.round__app__board__omok-placeholder__svg-ghost.is-black{fill:rgba(16,16,16,.48);stroke:rgba(255,244,224,.48)}.round__app__board__omok-placeholder__svg-ghost.is-white{fill:rgba(246,241,231,.84);stroke:rgba(78,60,35,.34)}.round__app__board__omok-placeholder__svg-candidate-label{font-size:3.2px;font-weight:700;fill:#f6ead0;paint-order:stroke;stroke:#111;stroke-width:.42;pointer-events:none}.round__app__board__omok-placeholder__opening{position:absolute;left:.75rem;right:.75rem;top:3.1rem;z-index:30;display:flex;gap:.5rem;align-items:center;flex-wrap:wrap;width:auto!important;height:auto!important;margin:0;padding:.38rem .55rem;border-radius:.45rem;background:rgba(22,16,10,.86);box-shadow:0 .25rem .7rem rgba(0,0,0,.28);color:#fff3db;pointer-events:auto}.round__app__board__omok-placeholder__opening-text{font-weight:700;line-height:1.25}.round__app__board__omok-placeholder__opening-action{padding:.25rem .6rem;line-height:1.2}</style>`;
}

function getPathname(rawUrl) {
  try {
    return new URL(rawUrl, `http://localhost:${LISTEN_PORT}`).pathname;
  } catch (_) {
    return rawUrl.split('?')[0];
  }
}

function seatCookieForRequest(req) {
  const candidates = [getPathname(req.url)];
  const referer = req.headers.referer || req.headers.referrer;
  if (referer) candidates.push(getPathname(String(referer)));

  for (const candidate of candidates) {
    const direct = candidate.match(/^\/([\w-]{12})(?:\/|$)/);
    if (direct && omokSeatCookies.has(direct[1])) return omokSeatCookies.get(direct[1]);

    const play = candidate.match(/\/play\/([\w-]{12})(?:\/|$)/);
    if (play && omokSeatCookies.has(play[1])) return omokSeatCookies.get(play[1]);
  }
}

function mergeCookieHeader(existing, seatCookie) {
  const cookies = String(existing || '')
    .split(';')
    .map(cookie => cookie.trim())
    .filter(Boolean)
    .filter(cookie => !/^rk2=/i.test(cookie));
  if (seatCookie) cookies.push(seatCookie);
  return cookies.join('; ');
}

function rewriteSetCookie(reqUrl, headers) {
  if (!reqUrl.startsWith('/dev/omok/claim/')) return headers;

  const rewritten = { ...headers };
  const upstreamSetCookie = rewritten['set-cookie'];
  const filtered = Array.isArray(upstreamSetCookie)
    ? upstreamSetCookie.filter(cookie => !/^rk2=/i.test(String(cookie)))
    : upstreamSetCookie && !/^rk2=/i.test(String(upstreamSetCookie))
      ? upstreamSetCookie
      : undefined;

  if (filtered && (!Array.isArray(filtered) || filtered.length)) rewritten['set-cookie'] = filtered;
  else delete rewritten['set-cookie'];

  return rewritten;
}

async function handleOmokClaim(req, res) {
  const requestHost = req.headers.host || `localhost:${LISTEN_PORT}`;
  const pathname = getPathname(req.url);
  const fullId = pathname.slice(pathname.lastIndexOf('/') + 1);
  const playerId = fullId.slice(-4);
  omokSeatCookies.set(fullId, `rk2=${playerId}`);

  res.writeHead(303, {
    location: `https://${requestHost}/${fullId}`,
    'set-cookie': ['rk2=; Max-Age=0; Expires=Thu, 01 Jan 1970 00:00:00 GMT; SameSite=Lax; Path=/'],
    'content-length': '0',
  });
  res.end();
}

function proxyHttp(req, res) {
  const pathname = getPathname(req.url);
  if (req.method === 'GET' && pathname.startsWith('/dev/omok/claim/')) {
    handleOmokClaim(req, res).catch(err => {
      res.writeHead(502, { 'content-type': 'text/plain; charset=utf-8' });
      res.end(`Claim proxy error: ${err.message}`);
    });
    return;
  }

  const requestHost = req.headers.host || `localhost:${LISTEN_PORT}`;
  const seatMatch = pathname.match(/^\/([\w-]{12})$/);
  const seatCookie = seatMatch ? omokSeatCookies.get(seatMatch[1]) : undefined;
  const options = {
    host: '127.0.0.1',
    port: HTTP_TARGET_PORT,
    method: req.method,
    path: req.url,
    headers: {
      ...req.headers,
      host: requestHost,
      ...(seatCookie ? { cookie: mergeCookieHeader(req.headers.cookie, seatCookie) } : {}),
    },
  };

  const upstream = http.request(options, upstreamRes => {
    const contentType = String(upstreamRes.headers['content-type'] || '');
    const isCompiledManifest = pathname.startsWith('/assets/compiled/manifest.') && pathname.endsWith('.js');
    if (!contentType.includes('text/html')) {
      if (isCompiledManifest) {
        const chunks = [];
        upstreamRes.on('data', chunk => chunks.push(chunk));
        upstreamRes.on('end', () => {
          const body = Buffer.concat(chunks).toString('utf8');
          const rewritten = body.replaceAll("'round':'3OKELKM3'", "'round':'GYB7ZO6T'");
          const headers = rewriteSetCookie(req.url, upstreamRes.headers);
          headers['cache-control'] = 'no-cache, no-store, must-revalidate';
          headers['content-length'] = Buffer.byteLength(rewritten);
          res.writeHead(upstreamRes.statusCode || 502, headers);
          res.end(rewritten);
        });
        return;
      }
      const headers = rewriteSetCookie(req.url, upstreamRes.headers);
      if (pathname.startsWith('/assets/compiled/round.')) headers['cache-control'] = 'no-cache, no-store, must-revalidate';
      res.writeHead(upstreamRes.statusCode || 502, headers);
      upstreamRes.pipe(res);
      return;
    }

    const chunks = [];
    upstreamRes.on('data', chunk => chunks.push(chunk));
    upstreamRes.on('end', () => {
      const body = Buffer.concat(chunks).toString('utf8');
      const normalizedHost = requestHost;
      const rewritten = body
        .replaceAll('localhost:9664', normalizedHost)
        .replaceAll('127.0.0.1:9664', normalizedHost)
        .replaceAll('localhost:9663', normalizedHost)
        .replaceAll('127.0.0.1:9663', normalizedHost)
        .replaceAll('/assets/compiled/round.STSFNM5F.js"', `/assets/compiled/round.STSFNM5F.js?v=${ASSET_VERSION}"`)
        .replaceAll(`${normalizedHost}/assets/compiled/round.STSFNM5F.js"`, `${normalizedHost}/assets/compiled/round.STSFNM5F.js?v=${ASSET_VERSION}"`)
        .replaceAll('/assets/compiled/manifest.fb736118.js"', `/assets/compiled/manifest.fb736118.js?v=${ASSET_VERSION}"`)
        .replaceAll(`${normalizedHost}/assets/compiled/manifest.fb736118.js"`, `${normalizedHost}/assets/compiled/manifest.fb736118.js?v=${ASSET_VERSION}"`);
      const nonceMatch = rewritten.match(/nonce="([^"]+)"/);
      const nonce = nonceMatch ? nonceMatch[1] : '';
      const injected = nonce
        ? rewritten
            .replace('</head>', `${omokStyleFix(nonce)}${redirectFixScript(nonce)}</head>`)
            .replace('</body>', `${dialogFixScript(nonce)}</body>`)
        : rewritten;

      const headers = rewriteSetCookie(req.url, upstreamRes.headers);
      headers['content-length'] = Buffer.byteLength(injected);
      res.writeHead(upstreamRes.statusCode || 502, headers);
      res.end(injected);
    });
  });

  upstream.on('error', err => {
    res.writeHead(502, { 'content-type': 'text/plain; charset=utf-8' });
    res.end(`Proxy error: ${err.message}`);
  });

  req.pipe(upstream);
}

function attachUpgradeHandler(server) {
  server.on('upgrade', (req, socket, head) => {
    const seatCookie = seatCookieForRequest(req);
    const upstream = net.connect(WS_TARGET_PORT, '127.0.0.1', () => {
      const headers = {
        ...req.headers,
        host: req.headers.host || `localhost:${LISTEN_PORT}`,
        origin: `https://${req.headers.host || `localhost:${LISTEN_PORT}`}`,
        ...(seatCookie ? { cookie: mergeCookieHeader(req.headers.cookie, seatCookie) } : {}),
      };
      const headerLines = Object.entries(headers).flatMap(([name, value]) =>
        Array.isArray(value) ? value.map(v => `${name}: ${v}`) : value === undefined ? [] : [`${name}: ${value}`],
      );
      upstream.write(`${req.method} ${req.url} HTTP/${req.httpVersion}\r\n${headerLines.join('\r\n')}\r\n\r\n`);
      if (head.length) upstream.write(head);
      socket.pipe(upstream).pipe(socket);
    });

    upstream.on('error', () => socket.destroy());
    upstream.on('close', () => socket.destroy());
    socket.on('error', () => upstream.destroy());
    socket.on('close', () => upstream.destroy());
  });
}

const server = http.createServer(proxyHttp);
attachUpgradeHandler(server);

server.listen(LISTEN_PORT, '0.0.0.0', () => {
  console.log(`cloudflare omok proxy listening on http://0.0.0.0:${LISTEN_PORT}`);
});
