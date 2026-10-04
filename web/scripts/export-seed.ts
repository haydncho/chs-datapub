/**
 * Export the frontend demo datasets (src/mock/*.ts → `*_SEED`) as JSON seeds
 * for the core service: server/core/src/main/resources/seed/pages/{code}.json.
 * The backend serves them verbatim at GET /api/v1/pages/{code}, so the API
 * contract and the UI types stay in lock-step.
 *
 *   npm run export-seed
 */
import { mkdirSync, readdirSync, writeFileSync } from 'node:fs'
import { basename, dirname, join, resolve } from 'node:path'
import { fileURLToPath, pathToFileURL } from 'node:url'

const here = dirname(fileURLToPath(import.meta.url))
const mockDir = resolve(here, '../src/mock')
const outDir = resolve(here, '../../server/core/src/main/resources/seed/pages')
mkdirSync(outDir, { recursive: true })

let n = 0
for (const f of readdirSync(mockDir).filter(f => f.endsWith('.ts')).sort()) {
  const code = basename(f, '.ts')
  const mod = (await import(pathToFileURL(join(mockDir, f)).href)) as Record<string, unknown>
  const seeds = Object.entries(mod).filter(([k]) => k.endsWith('_SEED'))
  if (seeds.length !== 1) {
    console.warn(`skip ${f}: expected exactly one *_SEED export, found ${seeds.map(([k]) => k).join(', ') || 'none'}`)
    continue
  }
  writeFileSync(join(outDir, `${code}.json`), JSON.stringify(seeds[0]![1], null, 2) + '\n')
  n++
}
console.log(`exported ${n} page seeds → ${outDir}`)
