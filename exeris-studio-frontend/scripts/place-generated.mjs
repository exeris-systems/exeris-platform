#!/usr/bin/env node

/**
 * Places the output of `exeris-gen generate` into the Studio source tree.
 *
 * The generator always emits a whole Angular application: its per-entity surface under `src/app/`
 * and an app scaffold (package.json, angular.json, main.ts, the app shell) around it. Studio is
 * already that application and owns its own shell, so of the generator's output it takes only the
 * per-entity surface, and takes it unchanged.
 *
 * This step selects and copies whole files and nothing else. It never reads, rewrites or appends to
 * a file's content: `src/app/generated/` is byte-for-byte what the generator emitted, which is what
 * makes a regeneration a faithful reflection of the metadata corpus. A change that seems to need an
 * edit to a generated file belongs in the domain model, in the generator, or in hand-written code
 * outside `src/app/generated/`.
 *
 * The file list is the generator's own manifest, so a file the generator stops emitting disappears
 * here too, and a file it starts emitting arrives without a change to this script.
 */

import { copyFileSync, existsSync, mkdirSync, readFileSync, rmSync } from 'node:fs';
import { dirname, join, resolve } from 'node:path';
import { fileURLToPath } from 'node:url';

const root = resolve(dirname(fileURLToPath(import.meta.url)), '..');
const config = JSON.parse(readFileSync(join(root, 'exeris-codegen.json'), 'utf8'));
const staging = resolve(root, config.outputPath);
const target = join(root, 'src/app/generated');

/** The generator's per-entity tree, relative to its output root. */
const SOURCE_ROOT = 'src/app/';

/** The app shell the generator scaffolds. Studio authors its own, beside `generated/`. */
const SHELL_FILES = new Set(['app.component.ts', 'app.config.ts', 'app.routes.ts']);

const manifestPath = join(staging, '.exeris-codegen-manifest');
if (!existsSync(manifestPath)) {
  console.error(`No generator manifest at ${manifestPath}; run 'exeris-gen generate' first.`);
  process.exit(1);
}

const selected = readFileSync(manifestPath, 'utf8')
  .split('\n')
  .map((line) => line.trim())
  .filter((line) => line.startsWith(SOURCE_ROOT))
  .map((line) => line.slice(SOURCE_ROOT.length))
  .filter((relative) => !SHELL_FILES.has(relative))
  .sort();

if (selected.length === 0) {
  console.error(`The generator manifest lists no file under ${SOURCE_ROOT}; nothing to place.`);
  process.exit(1);
}

rmSync(target, { recursive: true, force: true });
for (const relative of selected) {
  const destination = join(target, relative);
  mkdirSync(dirname(destination), { recursive: true });
  copyFileSync(join(staging, SOURCE_ROOT, relative), destination);
}

console.log(`Placed ${selected.length} generated file(s) into ${target}`);
