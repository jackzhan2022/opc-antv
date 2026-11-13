// Serve the production build for browser tests; API requests must be explicitly mocked.
const http=require('node:http');
const fs=require('node:fs');
const path=require('node:path');
const root=path.resolve(__dirname,'../dist');
const types={'.html':'text/html; charset=utf-8','.js':'text/javascript; charset=utf-8','.css':'text/css; charset=utf-8','.svg':'image/svg+xml','.png':'image/png','.woff2':'font/woff2'};
http.createServer((req,res)=>{
 let pathname;
 try {pathname=decodeURIComponent(new URL(req.url,'http://localhost').pathname);} catch {res.writeHead(400);res.end();return;}
 if(pathname.startsWith('/api/')){res.writeHead(404);res.end();return;}
 const file=path.resolve(root,'.'+pathname);
 if(file!==root && !file.startsWith(root+path.sep)){res.writeHead(403);res.end();return;}
 const target=fs.existsSync(file)&&fs.statSync(file).isFile()?file:path.join(root,'index.html');
 res.writeHead(200,{'Content-Type':types[path.extname(target)]||'application/octet-stream','Cache-Control':'no-store'});
 fs.createReadStream(target).pipe(res);
}).listen(8000,'127.0.0.1');
