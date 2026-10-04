-- Analytics base data for A11 预警提醒 (rule engine) and A6 智能推荐.
-- Read by the Python analytics service (read-only); the core service owns writes.
--
--  * indicator_series — monthly per-institution indicator values
--  * alert_rule       — the 18 预警规则 evaluated by GET /analytics/alerts/evaluate
--
-- Demo data: the 12-month trends of web/src/mock/A11.ts (2025-10 … 2026-09, last
-- point = current period) plus peer values so the P90 rules have a peer group.

create table indicator_series (
    org_id      varchar(32)   not null references org(id),
    metric      varchar(48)   not null,         -- e.g. GG19.cost_per_case
    period      date          not null,         -- first day of the month
    value       numeric(14,4) not null,
    primary key (org_id, metric, period)
);
create index indicator_series_metric_idx on indicator_series(metric, period);

create table alert_rule (
    id          varchar(8)    primary key,      -- R01 …
    metric      varchar(48)   not null,         -- indicator_series.metric
    label       varchar(64)   not null,         -- display name, e.g. "GG19 次均总费用"
    kind        varchar(8)    not null check (kind in ('mom_pct', 'gt_p90', 'gt', 'lt')),
    threshold   numeric(14,4),                  -- mom_pct: % (negative = drop); gt/lt: value; gt_p90: unused
    level       varchar(4)    not null check (level in ('high', 'mid', 'low')),
    unit        varchar(16)   not null,         -- unit shown under the trend chart
    fmt         varchar(4)    not null default 'num' check (fmt in ('pct', 'yuan', 'num')),
    decimals    int           not null default 1,
    min_peers   int           not null default 5,   -- gt_p90: minimum peer group size
    enabled     boolean       not null default true
);

insert into alert_rule (id, metric, label, kind, threshold, level, unit, fmt, decimals) values
  -- the six rules that fire in the demo period (A11 seed AL-01 … AL-06)
  ('R01', 'GG19.cost_per_case', 'GG19 次均总费用',       'mom_pct', 15,   'high', '万元 · 千元', 'num',  1),
  ('R02', 'ES35.readmit14',     'ES35 14天再住院率',     'gt_p90',  null, 'high', '%',          'pct',  1),
  ('R03', 'BR25.border_ratio',  'BR25 临界区病例占比',   'gt',      35,   'mid',  '%',          'pct',  0),
  ('R04', 'qc_pass_rate',       '结算清单质控率',        'lt',      95,   'mid',  '%',          'pct',  1),
  ('R05', 'oop_ratio',          '医保外费用占比',        'gt_p90',  null, 'mid',  '%',          'pct',  1),
  ('R06', 'IU29.diff_per_case', 'IU29 例均基金差额',     'gt',      1500, 'low',  '元',         'yuan', 0),
  -- evaluated every scan, quiet in the demo period
  ('R07', 'BR25.cost_per_case', 'BR25 次均总费用',       'mom_pct', 15,   'high', '千元',       'num',  1),
  ('R08', 'split_stay_ratio',   '分解住院疑似率',        'gt',      3,    'high', '%',          'pct',  1),
  ('R09', 'IC29.diff_per_case', 'IC29 例均基金差额',     'gt',      3000, 'mid',  '元',         'yuan', 0),
  ('R10', 'low_std_admit',      '低标入院占比',          'gt',      10,   'mid',  '%',          'pct',  1),
  ('R11', 'readmit30',          '30天再住院率',          'gt',      9,    'mid',  '%',          'pct',  1),
  ('R12', 'consumable_ratio',   '耗材费用占比',          'mom_pct', 20,   'mid',  '%',          'pct',  1),
  ('R13', 'upload_timely',      '结算清单上传及时率',    'lt',      90,   'mid',  '%',          'pct',  1),
  ('R14', 'grouping_rate',      'DRG 入组率',            'lt',      95,   'mid',  '%',          'pct',  1),
  ('R15', 'drug_ratio',         '药品费用占比',          'mom_pct', 20,   'low',  '%',          'pct',  1),
  ('R16', 'self_pay_ratio',     '自费率',                'gt',      15,   'low',  '%',          'pct',  1),
  ('R17', 'cmi',                '病例组合指数 CMI',      'lt',      0.8,  'low',  '',           'num',  2),
  ('R18', 'avg_cost',           '次均费用',              'mom_pct', -20,  'low',  '千元',       'num',  1);

-- 12-month series for the institutions behind AL-01 … AL-06 (2025-10 … 2026-09)
insert into indicator_series (org_id, metric, period, value)
select s.org_id, s.metric, (date '2025-10-01' + (t.i - 1) * interval '1 month')::date, t.v
from (values
  ('H030', 'GG19.cost_per_case', array[7.1, 7.0, 7.3, 7.2, 7.4, 7.3, 7.5, 7.6, 7.4, 7.7, 7.6, 9.393]),
  ('H010', 'ES35.readmit14',     array[4.8, 5.1, 5.0, 5.4, 5.2, 5.6, 5.9, 6.0, 6.4, 6.8, 7.6, 8.7]),
  ('H004', 'BR25.border_ratio',  array[28, 30, 29, 31, 33, 32, 34, 35, 36, 38, 39, 41]),
  ('H012', 'qc_pass_rate',       array[96.2, 96.0, 95.8, 95.9, 95.4, 95.6, 95.1, 94.8, 94.6, 94.2, 94.0, 93.8]),
  ('H020', 'oop_ratio',          array[8.1, 8.4, 8.2, 8.9, 9.0, 9.3, 9.6, 9.8, 10.2, 10.6, 10.9, 11.2]),
  ('H003', 'IU29.diff_per_case', array[980, 1040, 1100, 1180, 1220, 1260, 1310, 1380, 1420, 1480, 1560, 1620])
) as s(org_id, metric, vals)
cross join lateral unnest(s.vals::numeric[]) with ordinality as t(v, i);

-- peer / other-institution values (previous + current period)
insert into indicator_series (org_id, metric, period, value) values
  -- GG19 次均总费用: stable elsewhere
  ('H001', 'GG19.cost_per_case', '2026-08-01', 7.8), ('H001', 'GG19.cost_per_case', '2026-09-01', 8.0),
  -- ES35 14天再住院率 peer group → P90 = 6.2
  ('H001', 'ES35.readmit14', '2026-09-01', 4.1), ('H002', 'ES35.readmit14', '2026-09-01', 4.5),
  ('H003', 'ES35.readmit14', '2026-09-01', 4.8), ('H004', 'ES35.readmit14', '2026-09-01', 5.0),
  ('H011', 'ES35.readmit14', '2026-09-01', 5.3), ('H030', 'ES35.readmit14', '2026-09-01', 5.6),
  ('H012', 'ES35.readmit14', '2026-09-01', 6.2), ('H020', 'ES35.readmit14', '2026-09-01', 6.2),
  -- BR25 临界区病例占比
  ('H001', 'BR25.border_ratio', '2026-09-01', 22), ('H002', 'BR25.border_ratio', '2026-09-01', 30),
  -- 结算清单质控率
  ('H001', 'qc_pass_rate', '2026-09-01', 98.6), ('H002', 'qc_pass_rate', '2026-09-01', 97.9),
  ('H003', 'qc_pass_rate', '2026-09-01', 99.1), ('H004', 'qc_pass_rate', '2026-09-01', 97.2),
  ('H010', 'qc_pass_rate', '2026-09-01', 96.5), ('H011', 'qc_pass_rate', '2026-09-01', 95.4),
  ('H020', 'qc_pass_rate', '2026-09-01', 96.8), ('H030', 'qc_pass_rate', '2026-09-01', 95.9),
  -- 医保外费用占比 peer group → P90 = 9.4
  ('H001', 'oop_ratio', '2026-09-01', 5.2), ('H002', 'oop_ratio', '2026-09-01', 6.0),
  ('H003', 'oop_ratio', '2026-09-01', 6.4), ('H004', 'oop_ratio', '2026-09-01', 6.9),
  ('H010', 'oop_ratio', '2026-09-01', 7.3), ('H011', 'oop_ratio', '2026-09-01', 8.1),
  ('H012', 'oop_ratio', '2026-09-01', 9.4), ('H030', 'oop_ratio', '2026-09-01', 9.4),
  -- IU29 例均基金差额
  ('H001', 'IU29.diff_per_case', '2026-09-01', 1210), ('H002', 'IU29.diff_per_case', '2026-09-01', 860),
  -- quiet rules
  ('H001', 'BR25.cost_per_case', '2026-08-01', 14.0), ('H001', 'BR25.cost_per_case', '2026-09-01', 14.3),
  ('H004', 'BR25.cost_per_case', '2026-08-01', 13.1), ('H004', 'BR25.cost_per_case', '2026-09-01', 13.6),
  ('H001', 'split_stay_ratio', '2026-09-01', 1.2), ('H010', 'split_stay_ratio', '2026-09-01', 1.8),
  ('H001', 'IC29.diff_per_case', '2026-09-01', 2680), ('H002', 'IC29.diff_per_case', '2026-09-01', 2510),
  ('H010', 'low_std_admit', '2026-09-01', 6.2), ('H012', 'low_std_admit', '2026-09-01', 7.5),
  ('H001', 'readmit30', '2026-09-01', 5.1), ('H010', 'readmit30', '2026-09-01', 6.0),
  ('H001', 'consumable_ratio', '2026-08-01', 21.0), ('H001', 'consumable_ratio', '2026-09-01', 22.4),
  ('H001', 'upload_timely', '2026-09-01', 97.5), ('H012', 'upload_timely', '2026-09-01', 98.2),
  ('H001', 'grouping_rate', '2026-09-01', 98.9), ('H011', 'grouping_rate', '2026-09-01', 99.3),
  ('H001', 'drug_ratio', '2026-08-01', 28.1), ('H001', 'drug_ratio', '2026-09-01', 28.9),
  ('H011', 'self_pay_ratio', '2026-09-01', 8.2),
  ('H011', 'cmi', '2026-09-01', 0.86), ('H020', 'cmi', '2026-09-01', 0.82),
  ('H002', 'avg_cost', '2026-08-01', 9.6), ('H002', 'avg_cost', '2026-09-01', 9.1);
