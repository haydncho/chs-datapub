-- 医保数据公开 · 定向发布平台 — core schema (PostgreSQL 16)
--
-- Two kinds of tables:
--  * page_payload: the read model each screen renders (GET /api/v1/pages/{code}),
--    seeded from the frontend demo data and overlaid with live domain state.
--  * domain tables: the state user actions change (sign-offs, approvals,
--    replies, alert receipts …) plus a hash-chained audit trail.

create table page_payload (
    code        varchar(16) primary key,
    payload     jsonb       not null,
    updated_at  timestamptz not null default now()
);

-- ── identity & permissions (A1 / A12) ─────────────────────────────────────
create table org (
    id          varchar(32) primary key,
    name        varchar(64)  not null,
    level       varchar(16)  not null,          -- 市三级 / 县三级 / 二级 / 一级 / 医保局 / 公开
    district    varchar(32)
);

create table app_role (
    code        varchar(32) primary key,         -- convener / admin / analyst / hospital / auditor / observer …
    name        varchar(32) not null,
    data_scope  varchar(32) not null             -- 全域 / 受控环境 / 本院具名 / 本县具名 / 汇总层 / 公开层
);

create table app_user (
    id          bigserial primary key,
    login       varchar(64)  not null unique,
    name        varchar(32)  not null,
    role_code   varchar(32)  not null references app_role(code),
    org_id      varchar(32)  references org(id),
    last_login  timestamptz,
    enabled     boolean      not null default true
);

-- ── indicators (A4) ────────────────────────────────────────────────────────
create table indicator (
    id          bigserial primary key,
    name        varchar(64)  not null unique,
    dimension   varchar(4)   not null,           -- 钱 / 效 / 错
    domain      varchar(32)  not null,
    source      varchar(8)   not null,           -- 必选 / 增选 / 仅内部
    tier        varchar(8)   not null,           -- pct / anon / named / none
    frequency   varchar(4)   not null,
    version     varchar(8)   not null,
    status      varchar(8)   not null,           -- on / review / draft / hold
    numerator   text,
    denominator text,
    pending_tier varchar(8)                      -- tier change awaiting convener approval
);

-- ── reports & sign-off (A5 / B4 / D1) ─────────────────────────────────────
create table report (
    id          varchar(32) primary key,
    title       varchar(128) not null,
    kind        varchar(32)  not null,
    published   date,
    pages       int,
    version     varchar(8)   not null default 'v1',
    status      varchar(8)   not null            -- sign / check / signed / old
);

create table report_signoff (
    report_id   varchar(32) not null references report(id),
    org_id      varchar(32) not null references org(id),
    signed_by   varchar(32) not null,
    channel     varchar(16) not null default 'web',   -- web / mobile
    signed_at   timestamptz not null default now(),
    primary key (report_id, org_id)
);

-- ── publishing workflow (A8 / A9) ─────────────────────────────────────────
create table publish_task (
    id          varchar(32) primary key,
    title       varchar(128) not null,
    kind        varchar(16)  not null,           -- 月告知 / 季公布 / 年通报 / 专题 / 提醒函 / 更正
    step        int          not null,           -- 1..10
    due         date,
    status      varchar(16)  not null default 'open'   -- open / approved / rejected / archived
);

create table publish_decision (
    id          bigserial primary key,
    task_id     varchar(32) not null references publish_task(id),
    decision    varchar(8)  not null,            -- approve / reject
    comment     text,
    back_to     int,
    decided_by  varchar(32) not null,
    decided_at  timestamptz not null default now()
);

-- ── feedback & appeals (A10 / B5) ─────────────────────────────────────────
create table feedback_item (
    id          varchar(16) primary key,         -- YJ-0931
    kind        varchar(8)  not null,            -- 申诉 / 意见 / 纠错
    org_name    varchar(64) not null,
    title       varchar(128) not null,
    location    varchar(64),
    report      varchar(128),
    status      varchar(8)  not null,            -- todo / doing / reply / over / done
    assignee    varchar(32),
    body        text,
    created_at  timestamptz not null default now()
);

create table feedback_reply (
    id          bigserial primary key,
    item_id     varchar(16) not null references feedback_item(id),
    body        text        not null,
    triggers_correction boolean not null default false,
    replied_by  varchar(32) not null,
    replied_at  timestamptz not null default now()
);

-- ── alerts (A11 / D1) ──────────────────────────────────────────────────────
create table alert (
    id          varchar(16) primary key,         -- AL-01
    org_name    varchar(64) not null,
    metric      varchar(64) not null,
    value       varchar(16) not null,
    rule        varchar(32) not null,
    level       varchar(4)  not null,            -- high / mid / low
    status      varchar(8)  not null             -- unsent / sent / ack / ignored / topic
);

create table alert_receipt (
    id          bigserial primary key,
    alert_id    varchar(16) not null references alert(id),
    category    varchar(32),
    body        text,
    received_at timestamptz not null default now()
);

-- ── settings (A13 / A15) ───────────────────────────────────────────────────
create table setting (
    key         varchar(32) primary key,         -- appearance / display_policy
    value       jsonb       not null,
    updated_by  varchar(32),
    updated_at  timestamptz not null default now()
);

-- ── audit trail (A14) — every action, SHA-256 hash-chained ────────────────
create table audit_event (
    id          bigserial primary key,
    at          timestamptz not null default now(),
    actor       varchar(32) not null,
    page        varchar(16) not null,
    action      varchar(64) not null,
    payload     jsonb       not null default '{}'::jsonb,
    terminal    varchar(64),
    prev_hash   char(64)    not null,
    hash        char(64)    not null unique
);
create index audit_event_page_idx on audit_event(page, at desc);

-- ── analytics base data (read by the Python analytics service) ───────────
create table drg_group (
    code        varchar(8) primary key,
    name        varchar(64) not null,
    cases       int         not null,            -- city-wide cases this period
    diff_per_case int       not null,            -- 例均基金差额 (元, + = 逆差)
    cost_per_case int       not null             -- 次均费用 (元)
);
