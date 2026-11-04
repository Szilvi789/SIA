import { test } from 'node:test';
import assert from 'node:assert/strict';
import { spawnSync } from 'node:child_process';
import { createHash } from 'node:crypto';
import { existsSync, mkdtempSync, mkdirSync, readdirSync, readFileSync, rmSync, writeFileSync } from 'node:fs';
import { tmpdir } from 'node:os';
import { dirname, join, resolve } from 'node:path';
import { fileURLToPath } from 'node:url';
import { contentRisk, createSecretScanner, main, pathRisk, runAudit } from './privacy-audit.mjs';

const noSecrets = { scanDirectory() { return []; } };
const fixtureCredential = ['test', 'only', 'credential', 'not', 'real'].join('-');

function git(cwd, args, input) {
  const result = spawnSync('git', args, { cwd, input, encoding: 'utf8', windowsHide: true });
  assert.equal(result.status, 0, `Fixture Git operation ${args[0]} failed.`);
  return result.stdout.trim();
}

function repository(t) {
  const cwd = mkdtempSync(join(tmpdir(), 'sai-audit-test-'));
  t.after(() => rmSync(cwd, { recursive: true, force: true }));
  git(cwd, ['init', '--quiet']);
  return cwd;
}

function write(cwd, path, data) {
  mkdirSync(dirname(join(cwd, path)), { recursive: true });
  writeFileSync(join(cwd, path), data);
}

function stage(cwd, path, data) {
  write(cwd, path, data);
  git(cwd, ['add', '--', path]);
}

function snapshotCommit(cwd, parent) {
  const tree = git(cwd, ['write-tree']);
  const args = ['-c', 'user.name=Synthetic Fixture', '-c', 'user.email=fixture@example.invalid', 'commit-tree', tree, ...(parent ? ['-p', parent] : [])];
  const commit = git(cwd, args, 'Synthetic test snapshot\n');
  git(cwd, ['update-ref', 'HEAD', commit]);
  return commit;
}

function readSnapshot(directory) {
  const result = [];
  for (const entry of readdirSync(directory, { withFileTypes: true })) {
    const path = join(directory, entry.name);
    if (entry.isDirectory()) result.push(...readSnapshot(path));
    else result.push({ path, content: readFileSync(path, 'utf8') });
  }
  return result;
}

test('blocks private runtime directories, config, documents, dumps, archives, and certificates', () => {
  for (const path of ['data/a.json', 'runtime/content.md', 'storage/content.md', '.cache/browser/History', '.local-tools/settings.ini', 'backend/uploads/a.bin', 'private-rules/writing.md', 'backups/a.dat', '.env', '.env.personal', 'settings.local.json', 'credentials.json', 'oauth-tokens.json', 'documents/report.pdf', 'contacts.csv', 'emails/inbox.eml', 'db.sql', 'db.dump', 'snapshot.zip', 'public/cert.crt', '.ssh/id_rsa', 'images/screenshot.png', 'images/portrait.heic', 'docs/my-resume.md', 'notes/bank-statement.txt']) assert.ok(pathRisk(path), path);
});

test('preserves source, generic Markdown rules, config examples, and versioned migrations', () => {
  for (const path of ['backend/src/main/java/example/data/Record.java', 'backend/src/main/resources/rules/writing.md', 'docs/privacy.md', '.env.example', '.env.personal.example', 'compose.yaml', 'frontend/public/config.json', 'backend/src/main/resources/db/migration/V1__initial.sql', 'backend/src/main/resources/db/migration/V2_1__rules.sql', 'backend/src/main/resources/db/migration/R__views.sql']) assert.equal(pathRisk(path), null, path);
});

test('rejects nonportable paths that could escape snapshots or use Windows streams', () => {
  for (const path of ['../private.md', 'C:private.md', 'safe/file.txt:stream', 'safe\\private.md']) assert.equal(pathRisk(path), 'unsafe or nonportable path');
});

test('recognizes dump contents despite an innocent filename', () => {
  for (const content of ['PG' + 'DMP\0binary', '-- PostgreSQL ' + 'database dump\n', 'SQLite format ' + '3\0content', '<!-- SAI_' + 'PRIVATE_DATA -->\n']) assert.ok(contentRisk(Buffer.from(content)));
  assert.equal(contentRisk(Buffer.from('CREATE TABLE rules (id text);')), null);
  assert.ok(contentRisk(Buffer.from('x'.repeat(5000) + '\nSAI_' + 'PRIVATE_DATA\n')));
});

test('inspects forced tracked private files despite gitignore', t => {
  const cwd = repository(t);
  write(cwd, '.gitignore', 'uploads/\n');
  write(cwd, 'uploads/private.md', 'Synthetic private document');
  git(cwd, ['add', '-f', 'uploads/private.md']);
  const result = runAudit({ cwd, mode: 'staged', secretScanner: noSecrets });
  assert.ok(result.findings.some(item => item.path === 'uploads/private.md'));
});

test('checks staged blob contents even when the working file was replaced', t => {
  const cwd = repository(t);
  stage(cwd, 'source.txt', fixtureCredential);
  write(cwd, 'source.txt', 'Innocent working copy');
  let sawStaged = false;
  const result = runAudit({ cwd, mode: 'staged', secretScanner: {
    scanDirectory(directory) {
      sawStaged = readSnapshot(directory).some(file => file.content === fixtureCredential);
      return sawStaged ? [{ path: 'source.txt', category: 'synthetic scanner finding' }] : [];
    }
  } });
  assert.equal(sawStaged, true);
  assert.equal(result.findings.length, 1);
  assert.equal(JSON.stringify(result).includes(fixtureCredential), false);
});

test('blocks SQL dumps masquerading as versioned migrations', t => {
  const cwd = repository(t);
  stage(cwd, 'backend/src/main/resources/db/migration/V1__schema.sql', '-- PostgreSQL ' + 'database dump\n');
  const result = runAudit({ cwd, mode: 'staged', secretScanner: noSecrets });
  assert.ok(result.findings.some(item => item.category === 'database dump content'));
});

test('allows a real schema migration', t => {
  const cwd = repository(t);
  stage(cwd, 'backend/src/main/resources/db/migration/V1__schema.sql', 'CREATE TABLE rules (id text);');
  assert.deepEqual(runAudit({ cwd, mode: 'staged', secretScanner: noSecrets }).findings, []);
});

test('pre-push catches a removed private document in an earlier reachable commit', t => {
  const cwd = repository(t);
  stage(cwd, 'notes/personal.pdf', Buffer.from('%PDF synthetic')); 
  const first = snapshotCommit(cwd);
  git(cwd, ['rm', '--cached', 'notes/personal.pdf']);
  stage(cwd, 'README.md', 'Public synthetic source');
  const second = snapshotCommit(cwd, first);
  const result = runAudit({ cwd, mode: 'pre-push', target: second, secretScanner: noSecrets });
  assert.ok(result.findings.some(item => item.path === 'notes/personal.pdf' && item.scope === 'history'));
});

test('pre-push sends historical secret blobs to the detector after removal', t => {
  const cwd = repository(t);
  stage(cwd, 'code.txt', fixtureCredential);
  const first = snapshotCommit(cwd);
  stage(cwd, 'code.txt', 'Safe replacement');
  snapshotCommit(cwd, first);
  let sawHistory = false;
  runAudit({ cwd, mode: 'pre-push', secretScanner: { scanDirectory(directory) {
    sawHistory = readSnapshot(directory).some(file => file.content === fixtureCredential);
    return [];
  } } });
  assert.equal(sawHistory, true);
});

test('pre-push also checks a second local branch in a multi-ref push', t => {
  const cwd = repository(t);
  stage(cwd, 'README.md', 'Synthetic public source');
  const first = snapshotCommit(cwd);
  git(cwd, ['update-ref', 'refs/heads/clean-branch', first]);
  stage(cwd, 'second-branch/report.pdf', '%PDF synthetic private fixture');
  const second = snapshotCommit(cwd, first);
  git(cwd, ['update-ref', 'refs/heads/second-branch', second]);
  git(cwd, ['update-ref', 'HEAD', first]);
  assert.ok(runAudit({ cwd, mode: 'pre-push', target: first, secretScanner: noSecrets }).findings.some(item => item.path === 'second-branch/report.pdf'));
});

test('full audit examines unstaged and untracked nonignored files', t => {
  const cwd = repository(t);
  stage(cwd, 'README.md', 'Synthetic public readme');
  write(cwd, 'untracked/report.docx', 'Synthetic Office file');
  const result = runAudit({ cwd, secretScanner: noSecrets });
  assert.ok(result.findings.some(item => item.path === 'untracked/report.docx' && item.scope === 'worktree'));
});

test('permits only exact reviewed synthetic document hashes', t => {
  const cwd = repository(t);
  const path = 'backend/src/test/fixtures/synthetic.pdf';
  const content = '%PDF synthetic sample';
  stage(cwd, path, content);
  write(cwd, 'scripts/privacy-policy.json', JSON.stringify({ version: 1, syntheticFiles: [{ path, sha256: createHash('sha256').update(content).digest('hex'), purpose: 'Synthetic PDF parser fixture' }] }));
  assert.deepEqual(runAudit({ cwd, mode: 'staged', secretScanner: noSecrets }).findings, []);
  stage(cwd, path, content + ' changed');
  assert.ok(runAudit({ cwd, mode: 'staged', secretScanner: noSecrets }).findings.length);
});

test('synthetic exceptions never exempt runtime paths or database content', t => {
  const cwd = repository(t);
  const path = 'uploads/synthetic.pdf';
  const content = '-- PostgreSQL ' + 'database dump\n';
  stage(cwd, path, content);
  write(cwd, 'scripts/privacy-policy.json', JSON.stringify({ version: 1, syntheticFiles: [{ path, sha256: createHash('sha256').update(content).digest('hex'), purpose: 'Synthetic fixture' }] }));
  const categories = runAudit({ cwd, mode: 'staged', secretScanner: noSecrets }).findings.map(item => item.category);
  assert.ok(categories.includes('private runtime directory'));
  assert.ok(categories.includes('database dump content'));
});

test('missing secret detector fails closed', t => {
  const cwd = repository(t);
  const originalPath = process.env.PATH;
  process.env.PATH = '';
  try { assert.throws(() => createSecretScanner(cwd, cwd), /Gitleaks 8\.30\.1 is required/); }
  finally { process.env.PATH = originalPath; }
});

test('malformed policy errors do not expose its contents', t => {
  const cwd = repository(t);
  write(cwd, 'scripts/privacy-policy.json', fixtureCredential);
  assert.throws(() => runAudit({ cwd, mode: 'staged', secretScanner: noSecrets }), error => !error.message.includes(fixtureCredential) && error.message.includes('invalid JSON'));
});

test('hook command refuses a partial secret-free audit', () => {
  assert.throws(() => main(['--staged', '--skip-secrets']), /Hooks require secret detection/);
  assert.throws(() => main(['--pre-push', '--skip-secrets']), /Hooks require secret detection/);
});

test('cleans scanner snapshots even when detector fails', t => {
  const cwd = repository(t);
  stage(cwd, 'README.md', 'Synthetic sample');
  let snapshot;
  assert.throws(() => runAudit({ cwd, mode: 'staged', secretScanner: { scanDirectory(directory) { snapshot = directory; throw new Error('Synthetic failure'); } } }), /Synthetic failure/);
  assert.equal(existsSync(snapshot), false);
});

test('real pinned Gitleaks detects a synthetic token without printing its value', { skip: !existsSync(resolve(dirname(fileURLToPath(import.meta.url)), '../.local-tools/privacy', process.platform === 'win32' ? 'gitleaks.exe' : 'gitleaks')) }, t => {
  const cwd = repository(t);
  const toolRoot = resolve(dirname(fileURLToPath(import.meta.url)), '..');
  const token = 'gh' + 'p_' + createHash('sha256').update('SAI disposable synthetic credential').digest('hex').slice(0, 36);
  stage(cwd, 'source.txt', `const authorization = '${token}';`);
  write(cwd, 'source.txt', 'Innocent working file; staged content must still be scanned.');
  // The scanner belongs to this repository, but scans only the disposable fixture.
  const temp = mkdtempSync(join(tmpdir(), 'sai-scanner-test-'));
  t.after(() => rmSync(temp, { recursive: true, force: true }));
  const scanner = createSecretScanner(toolRoot, temp);
  const result = runAudit({ cwd, mode: 'staged', secretScanner: scanner });
  assert.ok(result.findings.some(item => item.category.startsWith('secret detector')));
  assert.equal(JSON.stringify(result).includes(token), false);
});

test('real scanner checks paths normally skipped by default detector exclusions', { skip: !existsSync(resolve(dirname(fileURLToPath(import.meta.url)), '../.local-tools/privacy', process.platform === 'win32' ? 'gitleaks.exe' : 'gitleaks')) }, t => {
  const cwd = repository(t);
  const toolRoot = resolve(dirname(fileURLToPath(import.meta.url)), '..');
  const token = 'gh' + 'p_' + createHash('sha256').update('SAI synthetic lockfile credential').digest('hex').slice(0, 36);
  stage(cwd, 'package-lock.json', JSON.stringify({ example: token }));
  const otherToken = 'gh' + 'p_' + createHash('sha256').update('SAI synthetic SVG credential').digest('hex').slice(0, 36);
  stage(cwd, 'frontend/public/image.svg', `<svg><!-- ${otherToken} --></svg>`);
  const temp = mkdtempSync(join(tmpdir(), 'sai-scanner-test-'));
  t.after(() => rmSync(temp, { recursive: true, force: true }));
  const result = runAudit({ cwd, mode: 'staged', secretScanner: createSecretScanner(toolRoot, temp) });
  assert.ok(result.findings.some(item => item.path === 'package-lock.json' && item.category.startsWith('secret detector')));
  assert.ok(result.findings.some(item => item.path === 'frontend/public/image.svg' && item.category.startsWith('secret detector')));
});
