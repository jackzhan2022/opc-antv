const { spawn } = require('node:child_process');

const child = spawn(process.execPath, [require.resolve('umi/bin/umi'), 'dev'], {
  stdio: 'inherit',
  env: { ...process.env, HOST: '127.0.0.1', PORT: '8000', SINX_ONLINE_API: '1', SOCKET_SERVER: 'http://localhost:8000' },
});
child.on('exit', code => process.exit(code ?? 1));
