const http = require('node:http');
const fs = require('node:fs');
const path = require('node:path');
const file = path.join(__dirname, 'creator-contracts-preview.html');
http.createServer((request, response) => {
  const pathname = new URL(request.url, 'http://127.0.0.1').pathname;
  if (request.method !== 'GET' || !['/', '/creator-contracts-preview.html'].includes(pathname)) {
    response.writeHead(404); response.end(); return;
  }
  response.writeHead(200, {
    'Content-Type': 'text/html; charset=utf-8',
    'Cache-Control': 'no-store',
    'Content-Security-Policy': "default-src 'none'; script-src 'unsafe-inline'; style-src 'unsafe-inline'; img-src 'none'; connect-src 'none'; form-action 'none'; base-uri 'none'; frame-ancestors 'none'",
  });
  fs.createReadStream(file).pipe(response);
}).listen(8012, '127.0.0.1', () => console.log('Contract UI preview: http://127.0.0.1:8012/'));
