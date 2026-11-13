const http = require("node:http"),
  fs = require("node:fs"),
  path = require("node:path");
const root = path.resolve(__dirname, "../dist");
const types = {
  ".html": "text/html; charset=utf-8",
  ".js": "text/javascript; charset=utf-8",
  ".css": "text/css; charset=utf-8",
  ".svg": "image/svg+xml",
};
http
  .createServer((req, res) => {
    let pathname;
    try {
      pathname = decodeURIComponent(
        new URL(req.url, "http://localhost").pathname,
      );
    } catch {
      res.writeHead(400);
      res.end();
      return;
    }
    if (!pathname.startsWith("/creators/")) {
      res.writeHead(404);
      res.end();
      return;
    }
    const file = path.resolve(root, "." + pathname.slice("/creators".length));
    if (file !== root && !file.startsWith(root + path.sep)) {
      res.writeHead(403);
      res.end();
      return;
    }
    const target =
      fs.existsSync(file) && fs.statSync(file).isFile()
        ? file
        : path.join(root, "index.html");
    res.writeHead(200, {
      "Content-Type": types[path.extname(target)] || "application/octet-stream",
      "Cache-Control": "no-store",
    });
    fs.createReadStream(target).pipe(res);
  })
  .listen(8002, "127.0.0.1");
