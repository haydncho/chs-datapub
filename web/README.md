# web — 医保数据公开 · 定向发布平台 (frontend)

Vue 3 + TypeScript + Vite, **shadcn-vue** components on **Tailwind CSS v4**.

```bash
npm install
npm run dev          # http://localhost:5173 — /api is proxied to the core service on :8080
npm run build        # type-check (vue-tsc) + production build
npm run export-seed  # write src/mock/*_SEED → server/core/src/main/resources/seed/pages/*.json
```

Works without a backend: every page renders from its seed in `src/mock/` and
switches to `GET /api/v1/pages/{code}` when the core service answers.

## Layout

| Path | What |
|---|---|
| `src/app/nav.ts` | five-stage information architecture (stages, pages, viewer identity per page) |
| `src/app/pages.ts`, `router.ts` | page registry (layout: workbench / cockpit / bare), hash routes `#/A3`, `#/cockpit?who=hosp` |
| `src/app/appearance.ts` | 外观配置 (A15) — applied platform-wide via `<html data-theme/radius/font/wm/motion>` |
| `src/components/yb/` | app shell (security strip, stage nav, sub-tabs, real-name watermark, page skeleton, toast) and page building blocks |
| `src/components/ui/` | shadcn-vue components (button/badge variants extended with the design's styles) |
| `src/pages/` | one file per screen, large screens split into `src/pages/<code>/` |
| `src/mock/` | typed demo dataset per screen — the API contract; ACTIONS each page posts are documented at the bottom of each file |
| `src/style.css` | design tokens (colours, radii, fonts) as CSS variables + Tailwind theme |
| `PORTING.md` | how the Claude Design prototypes were translated (conventions for new pages) |

Fonts (Noto Sans SC, Barlow) are self-hosted via `@fontsource` — the platform runs on the 医保专网 without internet access.
