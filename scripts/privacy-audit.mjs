#!/usr/bin/env node
import { spawnSync } from 'node:child_process';
import { existsSync, lstatSync, mkdtempSync, mkdirSync, readFileSync, rmSync, writeFileSync } from 'node:fs';
import { createHash } from 'node:crypto';
import { tmpdir } from 'node:os';
import { dirname, isAbsolute, join, relative, resolve, sep } from 'node:path';
import { fileURLToPath } from 'node:url';

const maxBuffer = 256 * 1024 * 1024;
const runtimeDirectory = /(^|\/)(?:\.sai|\.private|\.cache|\.local-tools|private|private-data|local-data|user-data|sai-data|uploads|backups|generated-documents|private-rules|conversations|drafts|pgdata|postgres-data|database-data|secrets|\.secrets|\.aws|\.ssh|\.gnupg)(\/|$)/i;
const privateExtension = /\.(?:pdf|docx?|xlsx?|pptx?|odt|ods|odp|rtf|eml|msg|mbox|pst|ics|vcf|csv|tsv|png|jpe?g|webp|tiff?|heic|heif|bmp|gif|pem|key|p12|pfx|crt|cer|cert|der|jks|keystore|dump|backup|bak|sqlite3?|db3?(?:-wal|-shm)?|zip|7z|tar|tgz|gz|bz2|xz|rar)$/i;
const migration = /^backend\/src\/main\/resources\/db\/migration\/(?:V[\d._]+__|R__)[\w.-]+\.sql$/;

function git(cwd, args, { input, optional = false } = {}) {
  const result = spawnSync('git', args, { cwd, input, maxBuffer, windowsHide: true });
  if (result.error || result.status !== 0) {
    if (optional) return null;
    throw new Error(`Git inspection failed (${args[0]}). No file contents were printed.`);
  }
  return result.stdout;
}

function safePath(path) {
  return !isAbsolute(path) && !path.split(/[\\/]/).some(part => part === '..') && !/[\\\x00-\x1f\x7f:<>"|?*]/.test(path);
}

export function pathRisk(path) {
  if (!safePath(path)) return 'unsafe or nonportable path';
  if (runtimeDirectory.test(path) || /^(?:data|runtime|storage)\//i.test(path)) return 'private runtime directory';
  const name = path.slice(path.lastIndexOf('/') + 1);
  if (/^\.env(?:\.|$)/i.test(name) && !/\.example$/i.test(name)) return 'local environment configuration';
  if (/^(?:credentials?|tokens?|client_secret[^/]*|oauth[-_]?tokens?[^/]*)\.(?:json|ya?ml|toml|ini|properties|xml|txt)$/i.test(name)) return 'credential configuration';
  if (/(?:^|\.)local(?:\.|$)/i.test(name) && !/\.example$/i.test(name)) return 'local configuration';
  if (/^(?:application-local\.(?:properties|ya?ml)|(?:docker-)?compose\.override\.ya?ml)$/i.test(name)) return 'local application configuration';
  if (privateExtension.test(name)) return 'private document, certificate, database, or archive';
  if (/\.(?:txt|md|markdown|json|ya?ml)$/i.test(name) && /(?:^|[._ -])(?:resume|cv|cover[-_ ]?letter|passport|tax[-_ ]?return|bank[-_ ]?statement|medical[-_ ]?record|personal[-_ ]?notes)(?:[._ -]|$)/i.test(name)) return 'possible personal document filename';
  if (/\.sql$/i.test(name) && !migration.test(path)) return 'SQL outside versioned application migrations';
  return null;
}

export function contentRisk(data) {
  const text = data.toString('utf8');
  const start = text.slice(0, 4096);
  if (start.startsWith('PGDMP') || start.startsWith('SQLite format 3\x00') || /^-- (?:(?:PostgreSQL|MySQL) database dump|Dumped from database version)/im.test(start)) return 'database dump content';
  if (/^(?:<!--\s*)?SAI_PRIVATE_DATA(?:\s*-->)?\s*$/m.test(text)) return 'explicit private-data marker';
  if (/-----BEGIN (?:[A-Z ]*PRIVATE KEY|CERTIFICATE)-----/.test(text)) return 'private key or certificate content';
  return null;
}

function readPolicy(cwd) {
  const path = join(cwd, 'scripts/privacy-policy.json');
  if (!existsSync(path)) return [];
  let policy;
  try { policy = JSON.parse(readFileSync(path, 'utf8')); }
  catch { throw new Error('Privacy policy is invalid JSON; its contents were not printed.'); }
  if (policy.version !== 1 || !Array.isArray(policy.syntheticFiles)) throw new Error('Invalid privacy policy.');
  for (const entry of policy.syntheticFiles) {
    if (!entry.path || !safePath(entry.path) || !/^[a-f0-9]{64}$/.test(entry.sha256) || !entry.purpose?.trim()) throw new Error('Synthetic exceptions require a safe exact path, SHA256, and purpose.');
  }
  return policy.syntheticFiles;
}

function scannerExecutable(cwd) {
  const local = join(cwd, '.local-tools', 'privacy', process.platform === 'win32' ? 'gitleaks.exe' : 'gitleaks');
  return existsSync(local) ? local : 'gitleaks';
}

export function createSecretScanner(cwd, temporary) {
  const executable = scannerExecutable(cwd);
  const version = spawnSync(executable, ['version'], { cwd, encoding: 'utf8', windowsHide: true });
  if (version.error || version.status !== 0 || !/^8\.30\.1\s*$/.test(version.stdout.trim())) {
    throw new Error('Gitleaks 8.30.1 is required; run node scripts/install-privacy-tools.mjs. Hooks fail closed without it.');
  }
  const sourceConfig = join(dirname(fileURLToPath(import.meta.url)), '..', '.gitleaks.toml');
  const config = join(temporary, 'gitleaks.toml');
  writeFileSync(config, readFileSync(sourceConfig), { mode: 0o600 });
  return {
    scanDirectory(path) {
      const report = join(temporary, 'report.json');
      const result = spawnSync(executable, ['dir', path, '--config', config, '--redact=100', '--ignore-gitleaks-allow', '--gitleaks-ignore-path', temporary, '--no-banner', '--no-color', '--log-level', 'error', '--max-decode-depth', '2', '--max-archive-depth', '2', '--report-format', 'json', '--report-path', report], { cwd, maxBuffer, windowsHide: true });
      // Never forward scanner stdout/stderr: scanners may include matching text.
      if (result.error || ![0, 1].includes(result.status)) throw new Error('Gitleaks could not complete; no scanner output was exposed.');
      if (result.status === 1) {
        let findings;
        try { findings = existsSync(report) ? JSON.parse(readFileSync(report, 'utf8')) : []; }
        catch { throw new Error('Gitleaks report is unreadable; its contents were not printed.'); }
        if (!findings.length) throw new Error('Gitleaks reported a finding without a readable report.');
        return findings.map(item => ({ path: relative(path, item.File).split(sep).join('/'), category: `secret detector (${item.RuleID})` }));
      }
      return [];
    }
  };
}

function collectIndex(cwd) {
  const records = git(cwd, ['ls-files', '--stage', '-z']).toString('utf8').split('\0').filter(Boolean);
  return records.map(record => {
    const match = /^(\d+) ([a-f0-9]+) (\d)\t([\s\S]+)$/.exec(record);
    if (!match || match[3] !== '0') throw new Error('Resolve unmerged index entries before auditing.');
    return { mode: match[1], oid: match[2], path: match[4], scope: 'index' };
  });
}

function collectHistory(cwd, target) {
  // pre-commit may supply only the first revision of a multi-ref push, or omit
  // it for an initial push. Scan every local ref as well as any supplied target.
  const revisions = ['--all', ...(target ? [target] : [])];
  const commits = git(cwd, ['rev-list', ...revisions]).toString('utf8').trim().split('\n').filter(Boolean);
  const seen = new Set();
  const entries = [];
  for (const commit of commits) {
    const records = git(cwd, ['ls-tree', '-r', '-z', commit]).toString('utf8').split('\0').filter(Boolean);
    for (const record of records) {
      const match = /^(\d+) (\w+) ([a-f0-9]+)\t([\s\S]+)$/.exec(record);
      if (!match) throw new Error('Could not inspect a committed tree.');
      const key = `${match[3]}:${match[4]}`;
      if (seen.has(key)) continue;
      seen.add(key);
      entries.push({ mode: match[1], oid: match[3], path: match[4], scope: 'history' });
    }
  }
  // Commit messages can contain pasted credentials too; scanner checks them locally.
  const messages = commits.length ? git(cwd, ['log', '--format=%B', ...revisions]) : null;
  return { entries, messages };
}

export function runAudit({ cwd = process.cwd(), mode = 'full', skipSecrets = false, target, secretScanner } = {}) {
  const root = git(cwd, ['rev-parse', '--show-toplevel']).toString('utf8').trim();
  const temporary = mkdtempSync(join(tmpdir(), 'sai-privacy-'));
  const snapshot = join(temporary, 'snapshot');
  mkdirSync(snapshot, { mode: 0o700 });
  const findings = [];
  let files = 0;
  try {
    const exceptions = readPolicy(root);
    const entries = mode === 'pre-push' ? [] : collectIndex(root);
    let messages;
    if (mode !== 'staged') {
      const history = collectHistory(root, mode === 'pre-push' ? target : undefined);
      entries.push(...history.entries);
      messages = history.messages;
    }
    if (mode === 'full') {
      const paths = git(root, ['ls-files', '--cached', '--others', '--exclude-standard', '-z']).toString('utf8').split('\0').filter(Boolean);
      for (const path of new Set(paths)) {
        const absolute = resolve(root, path);
        if (!existsSync(absolute)) continue;
        const stat = lstatSync(absolute);
        entries.push({ path, scope: 'worktree', mode: stat.isSymbolicLink() ? '120000' : '100644', data: stat.isFile() ? readFileSync(absolute) : Buffer.alloc(0) });
      }
    }
    const seenBlobs = new Set();
    const seenNamedBlobs = new Set();
    const blobCache = new Map();
    const secretPaths = new Map();
    for (const entry of entries) {
      files++;
      if (!safePath(entry.path)) {
        findings.push({ path: '[unsafe path]', category: 'unsafe or nonportable path', scope: entry.scope });
        continue;
      }
      if (!['100644', '100755'].includes(entry.mode)) {
        findings.push({ path: entry.path, category: 'symlink or submodule requires explicit review', scope: entry.scope });
        continue;
      }
      let data = entry.data;
      if (!data) {
        if (!blobCache.has(entry.oid)) blobCache.set(entry.oid, git(root, ['cat-file', 'blob', entry.oid]));
        data = blobCache.get(entry.oid);
      }
      const hash = createHash('sha256').update(data).digest('hex');
      const approvedSynthetic = exceptions.some(item => item.path === entry.path && item.sha256 === hash);
      const risk = pathRisk(entry.path);
      // Synthetic exceptions only permit document formats; never credentials/runtime dirs/dumps.
      if (risk && !(approvedSynthetic && ['private document, certificate, database, or archive', 'possible personal document filename'].includes(risk) && !/\.(?:pem|key|p12|pfx|crt|cer|cert|der|jks|keystore|dump|backup|bak|sqlite3?|db3?(?:-wal|-shm)?)$/i.test(entry.path))) findings.push({ path: entry.path, category: risk, scope: entry.scope });
      const bodyRisk = contentRisk(data);
      if (bodyRisk) findings.push({ path: entry.path, category: bodyRisk, scope: entry.scope });
      if (!seenBlobs.has(hash)) {
        seenBlobs.add(hash);
        // Neutral filenames prevent the detector's default file-path exclusions from
        // hiding credentials in lockfiles, SVGs, wrappers, or scanner configuration.
        const neutralPath = `content/${seenBlobs.size}.txt`;
        const destination = join(snapshot, neutralPath);
        mkdirSync(dirname(destination), { recursive: true, mode: 0o700 });
        writeFileSync(destination, data, { mode: 0o600 });
        secretPaths.set(neutralPath, { path: entry.path, scope: entry.scope });
      }
      const namedKey = `${hash}:${entry.path}`;
      if (!seenNamedBlobs.has(namedKey)) {
        seenNamedBlobs.add(namedKey);
        // Keep original names too, because some detector rules use filename context.
        const namedPath = `files/${seenNamedBlobs.size}/${entry.path}`;
        const destination = join(snapshot, namedPath);
        mkdirSync(dirname(destination), { recursive: true, mode: 0o700 });
        writeFileSync(destination, data, { mode: 0o600 });
        secretPaths.set(namedPath, { path: entry.path, scope: entry.scope });
      }
    }
    if (messages) writeFileSync(join(snapshot, 'commit-messages.txt'), messages, { mode: 0o600 });
    if (!skipSecrets) {
      const scanner = secretScanner ?? createSecretScanner(root, temporary);
      findings.push(...scanner.scanDirectory(snapshot).map(finding => ({ ...finding, ...secretPaths.get(finding.path) })));
    }
    return { files, findings: [...new Map(findings.map(item => [`${item.scope}:${item.path}:${item.category}`, item])).values()], partial: skipSecrets };
  } finally {
    // Only this freshly allocated OS temporary directory is removed.
    if (dirname(temporary) === tmpdir() && temporary.startsWith(join(tmpdir(), 'sai-privacy-'))) rmSync(temporary, { recursive: true, force: true });
  }
}

export function main(args = process.argv.slice(2)) {
  if (args.some(arg => !['--staged', '--pre-push', '--skip-secrets', '--help'].includes(arg))) throw new Error('Unknown option. Use --help.');
  if (args.includes('--help')) {
    process.stdout.write('Usage: node scripts/privacy-audit.mjs [--staged | --pre-push] [--skip-secrets]\nDefault: tracked/index/worktree plus all reachable history. --skip-secrets is a PARTIAL path/content audit only.\n');
    return 0;
  }
  if (args.includes('--staged') && args.includes('--pre-push')) throw new Error('Select one audit mode.');
  if (args.includes('--skip-secrets') && (args.includes('--staged') || args.includes('--pre-push'))) throw new Error('Hooks require secret detection; --skip-secrets is only available for a manual partial audit.');
  const result = runAudit({ mode: args.includes('--staged') ? 'staged' : args.includes('--pre-push') ? 'pre-push' : 'full', skipSecrets: args.includes('--skip-secrets'), target: process.env.PRE_COMMIT_TO_REF });
  for (const finding of result.findings) process.stderr.write(`${JSON.stringify(finding.path)}: ${finding.category}${finding.scope ? ` [${finding.scope}]` : ''}\n`);
  process.stdout.write(`${result.partial ? 'PARTIAL audit (secret detection skipped)' : 'Privacy audit'}: ${result.files} file versions, ${result.findings.length} findings.\n`);
  return result.findings.length ? 1 : 0;
}

if (process.argv[1] && resolve(process.argv[1]) === fileURLToPath(import.meta.url)) {
  try { process.exitCode = main(); }
  catch (error) { process.stderr.write(`Privacy audit failed: ${error.message}\n`); process.exitCode = 2; }
}
