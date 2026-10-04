# Porting the Claude Design prototypes → Vue 3 + shadcn-vue + Tailwind v4

Source prototypes live in `../project/*.dc.html`. Each file is a single
"DC" component: an HTML template inside `<x-dc>…</x-dc>` plus a logic class
in `<script type="text/x-dc" data-dc-script>`.

## Prototype template dialect (how to read it)

| Prototype | Meaning | Vue equivalent |
|---|---|---|
| `{{expr}}` (in text or inside `style="…"`) | value from `renderVals()` / page method (e.g. `a3(s)`) | `{{ }}` / `:style` / class binding |
| `<sc-for list="{{xs}}" as="x">` | loop | `v-for="x in xs"` |
| `<sc-if value="{{cond}}">` | conditional | `v-if` |
| `onClick="{{fn}}"`, `onInput=…`, `onChange=…` | handlers | `@click`, `@input`, … |
| `style-hover="…"` / `style-active="…"` | hover / active styles | Tailwind `hover:` / `active:` |
| `hint-placeholder-*` | editor-only hints | ignore |
| `this.setState({...})`, `this.state` (`s`) | component state | `ref` / `reactive` |
| `this.say(msg)` | toast | `say(msg)` from `@/app/shell` |
| `goAny('A8')` / `location.hash` | cross-page navigation | `goPage('A8')` from `@/app/router` |
| `G GS GT A AS AT R RS B BS` | colour constants | Tailwind tokens (`text-ok`, `bg-ok-soft`, `text-ok-ink`, `text-warn`, `bg-warn-soft`, `text-warn-ink`, `text-bad`, `bg-bad-soft`, `text-brand`, `bg-brand-soft`) or `@/lib/palette` for computed values |
| `fmt sign wan pad hsh SPL` | helpers | `@/lib/format` (`SPL` → `splitUnit`) |

The page-level shell (security strip, header with the five-stage nav, sub-tabs,
zone tag, real-name watermark, page-switch skeleton, toast) is ALREADY built in
`src/components/yb/AppShell.vue`. Port only the page `<section data-screen-label=…>`
body and any modals/overlays that belong to that page.

## Goal: pixel-faithful

Recreate each screen so it looks and behaves like the prototype at 1280–1600px
width: same layout grid, sizes, spacing, font sizes/weights, colours, radii,
borders, charts (hand-built SVG / CSS as in the prototype), copy text (Chinese,
verbatim), interactions, demo state machines and toasts. Do not simplify,
drop sections, or invent new content.

## Stack & conventions

- `<script setup lang="ts">`, Composition API. Strict TS must pass: `npx vue-tsc -b`.
- **Styling: Tailwind v4 utility classes.** Arbitrary values are fine
  (`text-[13px]`, `grid-cols-[minmax(0,1.6fr)_70px_36px]`, `rounded-[10px]`).
  Use the design tokens from `src/style.css` (`text-ink-1…6`, `border-line-1…4`,
  `bg-surface-1…3`, `bg-brand`, `bg-brand-soft`, `border-brand-line`, `bg-brand-tint`,
  ok/warn/bad/violet families, `yb-num` for Barlow numerals, `yb-card` for the
  standard white card, `rounded-[var(--radius-card)]`).
  Brand blue `#1E5BD8` must ALWAYS be expressed through the brand tokens /
  `var(--brand)` (never the hex) so 外观配置 can re-theme it. Same for
  `#EBF1FD` → `brand-soft`, `#B9CDF6` → `brand-line`, `#F6F9FF` → `brand-tint`,
  `#A9BCE8` → `brand-mute`.
  Use inline `:style` only for genuinely data-driven values (bar widths,
  SVG coordinates, per-item colours computed in script).
- **Components: shadcn-vue** in `src/components/ui/*` (button, badge, card,
  dialog, alert-dialog, sheet, input, textarea, checkbox, switch, tabs, table,
  tooltip, select, progress, separator, scroll-area, dropdown-menu, popover,
  slider, skeleton, label, radio-group, toggle, toggle-group, sonner).
  Use them where the prototype has the equivalent control:
  - buttons → `<Button>` (variants: `default` = brand primary, `outline`,
    `soft` = light brand, `dark` = cockpit dark, `ghost`, `destructive`;
    sizes `default` h-9 / `sm` h-8). Override sizes with `class` when the
    prototype differs (e.g. `h-[34px]`).
  - status pills → `<Badge variant="ok|warn|bad|brand|violet|muted">`
  - modals → `<Dialog>` / `<AlertDialog>`; text inputs → `<Input>` / `<Textarea>`;
    toggles → `<Switch>`; checkboxes → `<Checkbox>`; segmented tabs → `<Tabs>`
    or `<ToggleGroup>` when visually matching.
  Keep the prototype's exact look — pass `class` to override shadcn defaults.
  Bespoke visuals (charts, pipelines, bubble maps, heat cells, gauges) stay
  as hand-written markup/SVG.
- Shared page building blocks in `src/components/yb`: `PageSection`
  (1600px container, `label` = screen label), `PageHeader` (title/subtitle +
  actions slot), `Panel` (card with title/meta), `StatCard` (gradient KPI card
  with icon; tones ok/warn/bad/info).
- Icons: `@lucide/vue` is available, but where the prototype draws a specific
  inline SVG path, keep that path.
- Helpers: `@/lib/format` (`fmt`, `sign`, `wan`, `pad`, `hsh`, `splitUnit`, `stamp`),
  `@/lib/palette` (literal colours for computed styles), `cn` from `@/lib/utils`.
- Navigation: `goPage(code, query?)` from `@/app/router`. Toast: `say(msg)` from `@/app/shell`.
- Identity override (cockpit 身份切换 only): `setViewer(v)` from `@/app/shell`; reset to `null` on unmount.

## Data: seed + API contract (important — the backend serves the same JSON)

For every page `XX`:

1. `src/mock/XX.ts` — **all static demo data** the page needs, as typed,
   JSON-serialisable objects (no functions, no Dates, no computed colours/CSS):
   ```ts
   export interface A3Source { name: string; provider: string; mode: string; … }
   export interface A3Data { sources: A3Source[]; qc: …; rules: … }
   export const A3_SEED: A3Data = { … }
   ```
   Convert the prototype's positional tuples (`['结算明细…','市医保经办中心',…]`)
   into objects with clear English camelCase keys. Keep Chinese strings verbatim.
   Status values as string enums (`'ok' | 'late' | 'part'`), not colours.
2. In the page: `const data = usePageData('XX', XX_SEED)` from `@/api/client`
   (renders from the seed immediately; the backend at `GET /api/v1/pages/XX`
   returns the same `XXData` shape). Derive everything else (colours, widths,
   filtered lists) with `computed` in the page.
3. User actions that would change server state (approve, sign, reply, submit,
   send reminder, save settings …) additionally call
   `sendAction('XX', '<verbNoun>', payload)` (fire-and-forget) next to the
   local state change + toast the prototype does. Pure UI toggles (tabs,
   selection, filters) do not.
4. At the bottom of `src/mock/XX.ts` add a doc comment block
   `/** ACTIONS: verbNoun(payload shape) — what it does; … */` listing every
   `sendAction` the page uses. The backend is built from these.

## Files you own

Only create/modify: `src/pages/<YourPage>.vue`, `src/pages/<code>/*` (page-local
sub-components, if a page is large — split big pages into sub-components),
and `src/mock/<code>.ts`. Do NOT edit shared files (`src/app/*`,
`src/components/ui/*`, `src/components/yb/*`, `src/style.css`, `src/lib/*`,
`src/api/*`). If you believe a shared change is required, work around it
locally and mention it in your final report.

## Verify

- `npx vue-tsc -b` must pass with no errors in your files.
- Run the dev server on your assigned port (`npx vite --port <port> --strictPort`)
  and screenshot your pages with Playwright (Chromium is pre-installed;
  `PLAYWRIGHT_BROWSERS_PATH=/opt/pw-browsers`; launch with
  `executablePath: '/opt/pw-browsers/chromium'` if needed, viewport 1440×900).
  URL: `http://localhost:<port>/#/<CODE>`. Look at the screenshot, compare with
  the prototype source (and `../project/design_handoff_medicare_data_publish/screenshots/`
  for older-style references — layout ideas only; the v3 source is the truth),
  and fix discrepancies: overflow, wrapping, misalignment, missing content.
  Also click through the main interactions.
  Put screenshots/scripts in your scratch dir, not in the repo.

## Shared building blocks added after the port (use these in new pages)

- `<DialogContent overlay-class="…" :show-close="false">` — custom overlay / no ✕ without rebuilding the dialog from reka-ui parts.
- `<Switch size="lg">` — the prototype's 38×22 toggle (always give it an `aria-label`).
- `<StatCard size="sm|md|lg">` (26/30/32px numerals) and `class` merge; `.yb-stat` for hand-built KPI cards that follow 卡片样式.
- Density utilities driven by 外观配置 → 界面密度: `px-card-x`, `py-card-y`, `py-card-y-sm`, `gap-page`, `py-row`.
- Sticky offsets: table heads `top-(--sticky-top)`, side panels `top-(--sticky-panel)`.
- `v-press` (`@/lib/a11y`) on clickable rows/cards that must stay `div`s (focusable, Enter/Space); everything else clickable is a `<button>`.
- `authHeaders()` from `@/api/client` for raw `fetch` downloads.
- Access: `session.current.pages` (logged-in identity) drives the route guard and nav.
