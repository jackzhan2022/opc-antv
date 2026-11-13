const { spawn } = require("node:child_process");
const child = spawn(process.execPath, [require.resolve("umi/bin/umi"), "dev"], {
  stdio: "inherit",
  env: { ...process.env, PORT: process.env.PORT || "8002" },
});
child.on("exit", (code) => process.exit(code || 0));
