-- 第三批 / 第一批 · regional 分组：C1 县区医保视图、C2 省级与区域外汇总、C3 外部监督只读席位
-- 表名前缀 rg_；样例数据取自设计稿（第三批 C1H / C1O / MI / MAT，第一批 REG）

-- ---------------------------------------------------------------- C1 县区医保视图
-- 县区汇总层（各县区仅此一行汇总；其他县区的机构明细不下发）
create table rg_county (
    id            bigserial primary key,
    name          varchar(16)  not null unique,     -- 市区 / 甲县 / 乙县 / 丙区
    bureau_org    varchar(64)  not null unique,     -- 对应会话身份机构（user_identity.org）
    period        varchar(16)  not null,            -- 汇总期次，如 2026年8月
    avg_diff      numeric(10,2) not null,           -- 例均基金差额（元，正 = 逆差）
    list_qc_pct   numeric(5,2) not null,            -- 结算清单质控率（%）
    score         numeric(5,2) not null,            -- 县区医保综合考核得分（排名口径，引擎排名）
    sort          int          not null default 0
);

-- 县区内定点机构（具名，仅向本县区医保部门下发）
create table rg_county_institution (
    id           bigserial primary key,
    county_id    bigint       not null references rg_county(id),
    name         varchar(64)  not null,
    level        varchar(16)  not null,             -- 县三级 / 二级甲等 / 二级其他 / 一级
    cases        int          not null,             -- 病例数
    avg_diff     numeric(10,2) not null,            -- 例均基金差额（元）
    list_qc_pct  numeric(5,2) not null,             -- 清单质控率（%）
    cost_pctl    int          not null,             -- 次均费用分位（P0–P100）
    sort         int          not null default 0
);
create index ix_rg_county_institution on rg_county_institution(county_id);

-- 医共体医保监测指标（14 项，引擎按关注线 / 预警线判级）
create table rg_alliance_indicator (
    id          bigserial primary key,
    county_id   bigint       not null references rg_county(id),
    period      varchar(16)  not null,              -- 2026年第三季度
    name        varchar(32)  not null,
    value       numeric(12,2) not null,
    unit        varchar(4)   not null,              -- % / 元
    signed      boolean      not null default false,
    direction   varchar(4)   not null check (direction in ('high', 'low')),
    warn_line   numeric(12,2) not null,
    alarm_line  numeric(12,2) not null,
    sort        int          not null default 0
);

insert into rg_county (name, bureau_org, period, avg_diff, list_qc_pct, score, sort) values
 ('市区', '示例市医保局市区分局', '2026年8月', -82, 91.6, 92.4, 1),
 ('甲县', '甲县医保局',           '2026年8月', 412, 95.1, 90.1, 2),
 ('乙县', '乙县医保局',           '2026年8月',  96, 93.8, 88.7, 3),
 ('丙区', '丙区医保局',           '2026年8月', 138, 92.4, 86.2, 4);

insert into rg_county_institution (county_id, name, level, cases, avg_diff, list_qc_pct, cost_pctl, sort)
select c.id, v.name, v.level, v.cases, v.avg_diff, v.qc, v.pctl, v.sort
from (values
 ('甲县', '甲县人民医院',             '县三级',   2860,  712, 95.4, 78, 1),
 ('甲县', '甲县中医院',               '县三级',   1240, -138, 96.8, 41, 2),
 ('甲县', '甲县妇幼保健院',           '二级甲等',  620,   96, 97.1, 55, 3),
 ('甲县', '甲县康复医院',             '二级其他',  310,  -62, 94.2, 36, 4),
 ('甲县', '甲县第1社区卫生服务中心',  '一级',      180,  -41, 91.2, 30, 5),
 ('甲县', '甲县第3社区卫生服务中心',  '一级',      146,  -24, 92.6, 34, 6),
 -- 其他县区的机构明细：只用于各自县区医保部门，甲县身份不下发
 ('乙县', '乙县人民医院',             '县三级',   2240,  188, 94.6, 62, 1),
 ('乙县', '乙县中医院',               '二级甲等',  860,  -54, 93.1, 44, 2),
 ('丙区', '丙区人民医院',             '二级甲等', 1980,  246, 92.8, 71, 1),
 ('丙区', '丙区第2社区卫生服务中心',  '一级',      210,  -18, 90.6, 28, 2)
) as v(county, name, level, cases, avg_diff, qc, pctl, sort)
join rg_county c on c.name = v.county;

insert into rg_alliance_indicator (county_id, period, name, value, unit, signed, direction, warn_line, alarm_line, sort)
select c.id, '2026年第三季度', v.name, v.value, v.unit, v.signed, v.dir, v.warn, v.alarm, v.sort
from (values
 ('县域内住院率',             88.6, '%',  false, 'high',   85,   80,  1),
 ('县域内基金支出占比',       71.2, '%',  false, 'high',   75,   65,  2),
 ('基层就诊率',               63.4, '%',  false, 'high',   60,   50,  3),
 ('上转率',                    4.1, '%',  false, 'low',     5,    8,  4),
 ('下转率',                    2.2, '%',  false, 'high',    3,  1.5,  5),
 ('医共体基金结余率',          3.8, '%',  false, 'high',    2,    0,  6),
 ('次均住院费用',             7820, '元', false, 'low',  8500, 9500,  7),
 ('例均基金差额',              412, '元', true,  'low',   200,  400,  8),
 ('慢病规范管理率',           76.0, '%',  false, 'high',   70,   60,  9),
 ('县外住院人次占比',         11.4, '%',  false, 'low',    10,   15, 10),
 ('家庭医生签约服务费到位率', 92.0, '%',  false, 'high',   90,   80, 11),
 ('药品耗材联合采购率',       97.5, '%',  false, 'high',   95,   90, 12),
 ('结算清单质控率',           95.1, '%',  false, 'high',   95,   90, 13),
 ('住院患者满意度',           91.3, '%',  false, 'high',   90,   85, 14)
) as v(name, value, unit, signed, dir, warn, alarm, sort)
join rg_county c on c.name = '甲县';

-- ---------------------------------------------------------------- C2 省级与区域外汇总（仅统筹区汇总层，无机构级字段）
create table rg_province_meta (
    id      int primary key check (id = 1),
    as_of   date         not null                   -- 数据截止日（逾期判定基准）
);
insert into rg_province_meta (id, as_of) values (1, '2026-09-30');

create table rg_region (
    id             bigserial primary key,
    name           varchar(16)  not null unique,
    is_self        boolean      not null default false,   -- 本统筹区（加粗）
    last_period    varchar(32)  not null,                 -- 最近发布期次
    last_date      date         not null,                 -- 最近发布日期
    next_due       date         not null,                 -- 下一期应发布截止日
    sign_pct       numeric(5,2) not null,                 -- 签收率
    prev_sign_pct  numeric(5,2) not null,                 -- 上期签收率
    read_pct       numeric(5,2) not null,                 -- 查阅率
    reply_pct      numeric(5,2) not null,                 -- 意见答复率
    balance_pct    numeric(5,2) not null,                 -- 基金当期结余率
    fund_income    numeric(8,2) not null,                 -- 统筹基金当期收入（亿元，全省结余率加权）
    coverage_pct   numeric(5,2) not null,                 -- DRG/DIP 付费覆盖率
    avg_diff       numeric(10,2) not null,                -- 例均基金差额（元）
    sort           int          not null default 0
);

insert into rg_region (name, is_self, last_period, last_date, next_due, sign_pct, prev_sign_pct, read_pct, reply_pct,
                       balance_pct, fund_income, coverage_pct, avg_diff, sort) values
 ('示例市',   true,  '2026年8月 月告知',      '2026-09-12', '2026-10-15', 97, 96, 86, 78,  4.2, 43.7, 98.6,  -38, 1),
 ('统筹区02', false, '2026年8月 月告知',      '2026-09-10', '2026-10-15', 95, 94, 81, 82,  5.1, 18.6, 97.2, -112, 2),
 ('统筹区03', false, '2026年8月 月告知',      '2026-09-14', '2026-10-15', 92, 91, 74, 66,  2.8, 24.6, 96.5,   64, 3),
 ('统筹区04', false, '2026年8月 月告知',      '2026-09-09', '2026-10-15', 99, 98, 90, 88,  6.3, 12.8, 99.1, -205, 4),
 ('统筹区05', false, '2026年第二季度 季公布', '2026-07-20', '2026-10-20', 88, 86, 69, 59,  1.9, 30.5, 94.8,  138, 5),
 ('统筹区06', false, '2026年7月 月告知',      '2026-08-16', '2026-09-18', 81, 79, 52, 44, -0.6, 12.4, 92.3,  322, 6),
 ('统筹区07', false, '2026年8月 月告知',      '2026-09-13', '2026-10-15', 94, 93, 79, 71,  3.5, 19.3, 97.8,  -21, 7),
 ('统筹区08', false, '2026年8月 月告知',      '2026-09-11', '2026-10-15', 96, 92, 84, 80,  4.8, 14.1, 98.0,  -76, 8);

create table rg_province_insight (
    id     bigserial primary key,
    title  varchar(32)  not null,
    body   varchar(256) not null,
    sort   int          not null default 0
);
insert into rg_province_insight (title, body, sort) values
 ('共性逆差病组', 'BR25 在 5 个统筹区进入逆差前三;IC29 在 4 个统筹区例均逆差 > 2,000 元', 1),
 ('省内异地就医', '省会市为主要流入地,占省内异地基金支出 58%;按统筹区汇总,不含就医地机构明细', 2),
 ('对标档位使用', '8 个统筹区均以匿名分位为默认;3 个统筹区对结算清单质控率开放具名排行', 3);

-- ---------------------------------------------------------------- C3 外部监督只读席位
-- 限时席位：有效期由服务端校验，过期后接口一律 403（SEAT_EXPIRED）
create table rg_seat (
    id           bigserial primary key,
    user_id      bigint       not null unique references app_user(id),
    event        varchar(64)  not null,              -- 发布会
    valid_from   timestamptz  not null,
    valid_until  timestamptz  not null
);
insert into rg_seat (user_id, event, valid_from, valid_until)
select id, '2026年第三季度医保数据公开发布会', '2026-09-28 09:00:00+08', '2026-10-05 18:00:00+08'
from app_user where username = 'liudaibiao';

-- 发布会材料（只读预览；不提供下载、打印、导出）
create table rg_material (
    id       bigserial primary key,
    name     varchar(64)  not null,
    format   varchar(32)  not null,                 -- PPT · 28 页
    heading  varchar(64)  not null,                 -- 预览页标题
    kpis     jsonb        not null default '[]',    -- [{label, value, tone}]
    body     jsonb        not null default '[]',    -- 段落
    sort     int          not null default 0
);
insert into rg_material (name, format, heading, kpis, body, sort) values
 ('2026年第三季度医保数据公开发布会 · 演示材料', 'PPT · 28 页', '2026年前三季度 示例市医保基金运行情况',
  '[{"label":"统筹基金支出","value":"41.9 亿","tone":"default"},{"label":"当期结余率","value":"4.2%","tone":"success"},{"label":"DRG 付费覆盖率","value":"98.6%","tone":"default"}]',
  '["本期向 52 家定点医疗机构定向发布月度运行报告 9 期、病种专题 3 期,机构签收率 97%,意见答复率 78%。"]', 1),
 ('新闻通稿', '文稿 · 3 页', '示例市医保局发布2026年前三季度医保数据',
  '[]',
  '["9 月 28 日,示例市医疗保障局召开 2026 年第三季度医保数据公开发布会,通报前三季度全市医保基金运行情况与 DRG 支付方式改革进展。",
    "前三季度,全市统筹基金支出 41.9 亿元,当期结余率 4.2%,基金运行总体平稳;DRG 付费覆盖率达到 98.6%。",
    "市医保局按月向辖区 52 家定点医疗机构定向发布运行报告,机构签收率 97%,意见答复率 78%。下一步将围绕重点病组开展专题分析,持续提升数据公开的针对性。"]', 2),
 ('2026年前三季度医保基金运行统计公报', 'PDF · 16 页', '2026年前三季度医保基金运行统计公报',
  '[{"label":"统筹基金收入","value":"43.7 亿","tone":"default"},{"label":"统筹基金支出","value":"41.9 亿","tone":"default"},{"label":"当期结余率","value":"4.2%","tone":"success"}]',
  '["一、基金收支:统筹基金收入 43.7 亿元,支出 41.9 亿元,当期结余 1.8 亿元。",
    "二、支付方式改革:DRG 付费覆盖率 98.6%,覆盖全部二级及以上定点医疗机构。",
    "三、数据公开:前三季度定向发布月度运行报告 9 期、病种专题 3 期。"]', 3),
 ('常见问答', '文稿 · 9 条', '常见问答',
  '[]',
  '["问:医保数据为什么采用「定向发布」而不是向社会公开?答:发布对象为辖区定点医疗机构,内容涉及机构运行数据,按“谁在看 × 数据归谁”分级呈现。",
    "问:机构能看到其他医院的名称吗?答:默认以匿名分位呈现;个别指标经召集人审批后可开放具名排行。",
    "问:外部监督席位能下载材料吗?答:不能。席位仅在有效期内只读查阅,不提供下载、打印、导出。"]', 4);

-- 发布会录像（限时回看，不可下载）
create table rg_recording (
    id           bigserial primary key,
    title        varchar(64) not null,              -- 09-28 发布会
    duration_s   int         not null,              -- 52:18
    watched_s    int         not null default 0     -- 已观看 17:46
);
insert into rg_recording (title, duration_s, watched_s) values ('09-28 发布会', 3138, 1066);
