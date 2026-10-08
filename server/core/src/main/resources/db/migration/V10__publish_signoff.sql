-- 发布工作流 (A8) / 流程设计器 (A9) / 报告签收 (B4 · D1) — persisted state that used to live only in the browser.

-- ── A8: approved scope, linked report, origin of 更正 / 撤回 tasks ─────────────
alter table publish_task add column coverage    int;
alter table publish_task add column scope       jsonb;          -- {"institutions": [...], "filters": {...}} as approved
alter table publish_task add column report_id   varchar(32) references report(id);
alter table publish_task add column origin_task varchar(32);    -- 更正 / 撤回: the published task it revises
alter table publish_task add column approved_at timestamptz;
-- only explicitly flagged demo seed tasks may be put back at the approval node (A8/resetDemo, dev mode only)
alter table publish_task add column demo_seed   boolean not null default false;

update publish_task set report_id = 'R-2026-08', demo_seed = true where id = 'm8';
update publish_task set report_id = 'R-BR25' where id = 'br25';
update publish_task set report_id = 'R-2026-07' where id = 'c7';

-- 操作日志 of a task: submit / approve / release / reject / urge / correction / withdraw
create table publish_event (
    id          bigserial primary key,
    task_id     varchar(32) not null references publish_task(id),
    kind        varchar(16) not null,
    who         varchar(32) not null,
    tag         varchar(32) not null,
    what        text        not null,
    target      varchar(64),                 -- urge: the institution
    at          timestamptz not null default now()
);
create index publish_event_task_idx on publish_event (task_id, at);

-- ── B4 / D1: sign-off is per institution (report_signoff); report.status only says whether the
--    report can be signed at all (sign = 已发布、可签收 · check = 核对稿 · old = 已被更正版取代)
update report set status = 'sign' where status = 'signed';
insert into report_signoff (report_id, org_id, signed_by, channel, signed_at) values
  ('R-2026-07', 'H001', '李敏', 'web', '2026-08-14 10:12+08'),
  ('R-2026-Q2', 'H001', '李敏', 'web', '2026-07-21 09:30+08')
on conflict do nothing;

-- ── A9: published flow versions (the newest one is in force) ────────────────
create table flow_version (
    flow         varchar(16)  not null,      -- 月告知 / 专题 / 预警
    version      int          not null,
    nodes        jsonb        not null,
    total        int          not null,      -- critical path, working days
    note         varchar(128),
    published_by varchar(32)  not null,
    published_at timestamptz  not null default now(),
    primary key (flow, version)
);
