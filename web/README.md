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

## 平板(Pad)适配

宽度 < 1280 视为 Pad:横屏 1024 / 1180 / 1366、竖屏 768 / 820 均已适配,桌面(≥ 1280)外观保持不变。

- 菜单:< 1024 一级菜单收进左侧抽屉(端 → 分组 → 页面,带图标);1024–1279 顶栏只显示图标 + 当前分组名;下拉菜单靠点按开关,不依赖悬停。
- 布局:多栏在窄屏上下排列,宽表格 / 流程画布在卡片内部横向滚动,整页无横向滚动;全息图保持等比缩放,竖屏提示建议横屏。
- 触屏:主要可点区域 ≥ 40px,取消双击缩放延迟。
- 测试:`e2e/pad.spec.ts` 在 4 个 Pad 视口下逐页检查无整页横向滚动 / 无控制台错误,并覆盖抽屉与点按菜单。
- 规则写法:窄屏专用样式用 `max-xl:` / `max-lg:` 前缀,详见 `PORTING.md`。
