# 医保数据公开 · 定向发布平台

Implementation of the Claude Design prototypes (v3) as a real application.

| Dir | What |
|---|---|
| `web/` | frontend — Vue 3 + TypeScript + shadcn-vue + Tailwind CSS v4, all 24 screens,首页即登录页(先选医保局端 / 机构端,再登录并选择身份;两端有各自分类一致的三层菜单)(全息图, A1–A15, B1–B7, C3, D1) — see `web/README.md` |
| `server/core/` | Java 21 · Spring Boot 3 core API, Flyway schema, hash-chained audit trail — see `server/README.md` |
| `server/analytics/` | Python · FastAPI DRG analytics (病组全景, 选题推荐) |
| `docker-compose.yml` | PostgreSQL 16 + core + analytics + nginx-served web → `docker compose up --build`, open http://localhost:8000 |
| `project/`, `chats/` | the original design handoff bundle (prototypes `*.dc.html` and the design conversation) |

---

## Design handoff bundle (original notes)

### CODING AGENTS: READ THIS FIRST

This is a **handoff bundle** from Claude Design (claude.ai/design).

A user mocked up designs in HTML/CSS/JS using an AI design tool, then exported this bundle so a coding agent can implement the designs for real.

## What you should do — IMPORTANT

**Read the chat transcripts first.** There are 1 chat transcript(s) in `chats/`. The transcripts show the full back-and-forth between the user and the design assistant — they tell you **what the user actually wants** and **where they landed** after iterating. Don't skip them. The final HTML files are the output, but the chat is where the intent lives.

**Read `project/医保数据公开平台 v3 重点工作台.dc.html` in full.** The user had this file open when they triggered the handoff, so it's almost certainly the primary design they want built. Read it top to bottom — don't skim. Then **follow its imports**: open every file it pulls in (shared components, CSS, scripts) so you understand how the pieces fit together before you start implementing.

**If anything is ambiguous, ask the user to confirm before you start implementing.** It's much cheaper to clarify scope up front than to build the wrong thing.

## About the design files

The design medium is **HTML/CSS/JS** — these are prototypes, not production code. Your job is to **recreate them pixel-perfectly** in whatever technology makes sense for the target codebase (React, Vue, native, whatever fits). Match the visual output; don't copy the prototype's internal structure unless it happens to fit.

**Don't render these files in a browser or take screenshots unless the user asks you to.** Everything you need — dimensions, colors, layout rules — is spelled out in the source. Read the HTML and CSS directly; a screenshot won't tell you anything they don't.

## Bundle contents

- `README.md` — this file
- `chats/` — conversation transcripts (read these!)
- `project/` — the `医保数据公开平台设计稿` project files (HTML prototypes, assets, components)
