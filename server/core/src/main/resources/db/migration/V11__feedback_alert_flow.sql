-- 意见与申诉 (A10) · 意见核对 (B5) · 外部监督建议 (C3) · 预警提醒 (A11 / D1) — 状态机、机构归属与真实轨迹。
--
--  * feedback_item: 机构归属 (org_id)、来源、提交人、分派人/时间、附件元数据;ID 由序列生成(并发安全)。
--    「已超期」不再是存储状态,而是由 created_at + 5 个工作日与当前时间推导。
--  * verification_round / verification_submission: B5 核对轮次(截止时间)与每家机构一次的提交记录。
--  * alert: 机构归属 (org_id)、发送人/时间;alert_receipt: 回执机构与提交人。
--  * 演示数据:示例市第一人民医院 (H001) 的 IU29 例均基金差额超阈值,对应 D1 的「IU29 提醒函」。

-- ── feedback_item ──────────────────────────────────────────────────────────
alter table feedback_item
    add column org_id       varchar(32) references org(id),
    add column source       varchar(8),                       -- b5 / c3 / null (机构直接提交)
    add column round_id     varchar(32),
    add column submitted_by varchar(32),
    add column assigned_by  varchar(32),
    add column assigned_at  timestamptz,
    add column attachments  jsonb not null default '[]'::jsonb;

alter table feedback_item alter column kind type varchar(16);

update feedback_item f set org_id = o.id from org o where o.name = f.org_name;

-- seed items: real submission / assignment times consistent with the demo period
update feedback_item set created_at = x.at::timestamptz,
       assigned_at = case when assignee is null then null else (x.at::timestamptz + interval '4 hours 18 minutes') end,
       assigned_by = case when assignee is null then null else '陈志远' end
from (values
  ('YJ-0931', '2026-09-28 10:12+08'),
  ('YJ-0928', '2026-09-22 09:40+08'),
  ('YJ-0925', '2026-09-24 15:05+08'),
  ('YJ-0922', '2026-09-30 11:20+08'),
  ('YJ-0919', '2026-09-29 16:45+08'),
  ('YJ-0915', '2026-09-29 09:30+08'),
  ('YJ-0910', '2026-09-15 14:00+08')
) as x(id, at)
where feedback_item.id = x.id;

update feedback_item set attachments = '["特例单议批复.pdf","病例清单.xlsx"]'::jsonb where id = 'YJ-0931';
update feedback_item set attachments = '["HIS导出.xlsx"]'::jsonb where id = 'YJ-0925';
update feedback_item set attachments = '["病案首页 9 份.zip"]'::jsonb where id = 'YJ-0915';

-- 「已超期」is derived from the SLA, not stored
update feedback_item set status = 'doing' where status = 'over';

-- the seed's closed item has its reply on record
insert into feedback_reply (item_id, body, triggers_correction, replied_by, replied_at)
select 'YJ-0910', '经核实,医保外费用占比按国家医保局 2026 版口径计算,不含特需服务费用,详见指标卡说明。', false, '王倩', '2026-09-18 10:30+08'
where exists (select 1 from feedback_item where id = 'YJ-0910' and status = 'done')
  and not exists (select 1 from feedback_reply where item_id = 'YJ-0910');

-- 意见单编号:序列生成,避免并发提交时 count(*) 产生的主键冲突
create sequence feedback_item_seq;
select setval('feedback_item_seq', greatest(1015, coalesce(
    (select max(substring(id from 4)::int) from feedback_item where id ~ '^YJ-[0-9]+$'), 0)));

create index feedback_item_org_idx on feedback_item(org_id);
-- C3 监督建议: the same open suggestion from the same person only once (concurrent double submit)
create unique index feedback_c3_open_uq on feedback_item (submitted_by, md5(body)) where source = 'c3' and status <> 'done';
create index feedback_reply_item_idx on feedback_reply(item_id, replied_at);

-- ── B5 核对轮次 ────────────────────────────────────────────────────────────
create table verification_round (
    id          varchar(32) primary key,
    title       varchar(128) not null,
    report      varchar(128) not null,                   -- feedback_item.report of the generated 意见单
    deadline    timestamptz  not null
);

insert into verification_round (id, title, report, deadline) values
  ('BR25-v3', '核对稿 · BR25 脑缺血性疾患专题 v3', 'BR25 专题核对稿', '2026-10-30 18:00+08');

create table verification_submission (
    round_id     varchar(32) not null references verification_round(id),
    org_id       varchar(32) not null references org(id),
    submitted_by varchar(32) not null,
    submitted_at timestamptz not null default now(),
    items        jsonb       not null,
    primary key (round_id, org_id)
);

-- ── alerts ─────────────────────────────────────────────────────────────────
alter table alert
    add column org_id  varchar(32) references org(id),
    add column sent_at timestamptz,
    add column sent_by varchar(32);

update alert a set org_id = o.id from org o where o.name = a.org_name;
update alert set sent_at = '2026-09-26 09:00+08', sent_by = '李华' where status in ('sent', 'ack') and sent_at is null;

alter table alert_receipt
    add column org_id       varchar(32) references org(id),
    add column submitted_by varchar(32);

-- AL-04 was acknowledged in the demo period: keep its receipt on record
insert into alert_receipt (alert_id, category, body, received_at, org_id, submitted_by)
select 'AL-04', '编码调整', '已完成结算清单编码培训,10 月起清单上传前增加院内预审。', '2026-09-30 15:20+08', 'H012', '乙县人民医院医保办'
where exists (select 1 from alert where id = 'AL-04' and status = 'ack')
  and not exists (select 1 from alert_receipt where alert_id = 'AL-04');

-- 示例市第一人民医院 IU29 例均基金差额 (D1 的 IU29 提醒函): the series now crosses the 1,500 元 threshold
delete from indicator_series where org_id = 'H001' and metric = 'IU29.diff_per_case';
insert into indicator_series (org_id, metric, period, value)
select 'H001', 'IU29.diff_per_case', (date '2025-10-01' + (t.i - 1) * interval '1 month')::date, t.v
from unnest(array[1010, 1060, 1120, 1170, 1230, 1280, 1330, 1390, 1440, 1500, 1560, 1620]::numeric[])
     with ordinality as t(v, i);

insert into alert (id, org_name, metric, value, rule, level, status, org_id, sent_at, sent_by) values
  ('AL-07', '示例市第一人民医院', 'IU29 例均基金差额', '+1,620', '> +1,500 元', 'low', 'sent', 'H001', '2026-09-28 09:00+08', '李华')
on conflict (id) do nothing;
