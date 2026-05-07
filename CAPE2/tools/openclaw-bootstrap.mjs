import fs from 'fs';
import os from 'os';
import path from 'path';

function parseArgs(argv) {
  const args = new Map();
  for (let i = 0; i < argv.length; i += 1) {
    const token = argv[i];
    if (!token.startsWith('--')) continue;
    const key = token.slice(2);
    const next = argv[i + 1];
    if (next && !next.startsWith('--')) {
      args.set(key, next);
      i += 1;
    } else {
      args.set(key, 'true');
    }
  }
  return args;
}

function ensureDir(dir) {
  fs.mkdirSync(dir, { recursive: true });
}

function copyFile(src, dest) {
  ensureDir(path.dirname(dest));
  fs.copyFileSync(src, dest);
}

function copyDir(srcDir, destDir) {
  ensureDir(destDir);
  for (const entry of fs.readdirSync(srcDir, { withFileTypes: true })) {
    const src = path.join(srcDir, entry.name);
    const dest = path.join(destDir, entry.name);
    if (entry.isDirectory()) copyDir(src, dest);
    else if (entry.isFile()) copyFile(src, dest);
  }
}

function main() {
  const args = parseArgs(process.argv.slice(2));
  const repoRoot = path.resolve(process.cwd());
  const capeOpenclawDir = path.join(repoRoot, 'openclaw');
  if (!fs.existsSync(capeOpenclawDir)) {
    throw new Error(`openclaw folder not found at ${capeOpenclawDir}. Run from CAPE2 repo root.`);
  }

  const workspace = path.resolve(
    args.get('workspace') ??
      process.env.OPENCLAW_WORKSPACE ??
      path.join(os.homedir(), '.openclaw', 'workspace')
  );

  const dryRun = String(args.get('dry-run') ?? 'false').toLowerCase() === 'true';
  const rootFiles = ['AGENTS.md', 'SOUL.md', 'TOOLS.md', 'USER.md', 'IDENTITY.md', 'HEARTBEAT.md'];
  const operations = [];

  for (const file of rootFiles) {
    const src = path.join(capeOpenclawDir, file);
    if (!fs.existsSync(src)) continue;
    operations.push({ kind: 'file', src, dest: path.join(workspace, file) });
  }

  operations.push({ kind: 'dir', src: path.join(capeOpenclawDir, 'memory'), dest: path.join(workspace, 'memory') });
  operations.push({ kind: 'dir', src: path.join(capeOpenclawDir, 'skills'), dest: path.join(workspace, 'skills') });

  const summary = operations.map(op => `${op.kind.toUpperCase()} ${op.src} -> ${op.dest}`).join('\n');
  process.stdout.write(`OpenClaw bootstrap\nWorkspace: ${workspace}\nDry run: ${dryRun}\n\n${summary}\n\n`);
  if (dryRun) return;

  ensureDir(workspace);
  for (const op of operations) {
    if (op.kind === 'file') copyFile(op.src, op.dest);
    else copyDir(op.src, op.dest);
  }

  process.stdout.write('Done. Your OpenClaw workspace now contains CAPE agents, memory, and skills.\n');
}

main();

