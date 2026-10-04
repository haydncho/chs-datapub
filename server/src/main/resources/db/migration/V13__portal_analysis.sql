-- 第三批 · 机构门户分析（B2 病组下钻与专题、B3 对标与PK、B7 区域外数据）
-- 只建 pa_ 前缀表；对标档位只读第二批 benchmark_tier（A13 / A8 改档后 B3 随之变化）。
-- 机构以名称（会话身份 org）为键；接口一律按当前会话机构过滤，只返回本院数据。

-- ---------------------------------------------------------------- 同级组（B2 / B3）
create table pa_peer_org (
    org        varchar(64) primary key,
    peer_group varchar(32) not null,              -- 同级组，如「市三级」
    sort       int         not null
);

-- 对标指标的展示属性（档位不在此表，读 benchmark_tier）
create table pa_bench_indicator (
    indicator         varchar(32) primary key,
    unit              varchar(8)  not null,       -- 元 / % / 空
    decimals          int         not null,
    signed            boolean     not null default false,   -- 数值带 +/− 号（例均基金差额）
    higher_is_better  boolean     not null,
    note              varchar(128) not null
);

-- 同级机构指标值（发布期次）：仅 server 持有，按档位裁剪后下发
create table pa_bench_value (
    period    varchar(7)    not null,
    org       varchar(64)   not null references pa_peer_org(org),
    indicator varchar(32)   not null references pa_bench_indicator(indicator),
    value     numeric(12,2) not null,
    primary key (period, org, indicator)
);

-- ---------------------------------------------------------------- 本院病组（B2）
create table pa_group (
    org        varchar(64)  not null,
    period     varchar(7)   not null,
    code       varchar(8)   not null,
    name       varchar(64)  not null,
    cases      int          not null,
    avg_cost   int          not null,             -- 本院例均费用（元）
    bench_cost int          not null,             -- 标杆组例均费用（元，匿名）
    avg_diff   int          not null,             -- 例均基金差额（元；正 = 逆差）
    cost_pct   int          not null,             -- 次均费用同级分位（发布值）
    los        numeric(4,1) not null,             -- 本院平均住院日
    bench_los  numeric(4,1) not null,
    sort       int          not null,
    primary key (org, period, code)
);

create table pa_group_mix (
    org       varchar(64) not null,
    period    varchar(7)  not null,
    code      varchar(8)  not null,
    category  varchar(16) not null,               -- 药品 / 耗材 / 检查检验 / 治疗/手术 / 护理及其他
    own_pct   int         not null,
    bench_pct int         not null,
    sort      int         not null,
    primary key (org, period, code, category),
    foreign key (org, period, code) references pa_group(org, period, code)
);

create table pa_group_behavior (
    org             varchar(64) not null,
    period          varchar(7)  not null,
    code            varchar(8)  not null,
    name            varchar(32) not null,
    own_pct         numeric(5,1) not null,
    peer_median_pct numeric(5,1) not null,
    higher_is_worse boolean     not null,
    sort            int         not null,
    primary key (org, period, code, name),
    foreign key (org, period, code) references pa_group(org, period, code)
);

-- 相关专题入口（跳转到 B4 / B5）
create table pa_related_topic (
    id          serial primary key,
    org         varchar(64) not null,
    title       varchar(64) not null,
    status      varchar(32),
    target_page varchar(4)  not null,
    link_label  varchar(8)  not null,
    sort        int         not null
);

-- ---------------------------------------------------------------- 区域外数据（B7，省平台回流；按地区、等级、病种汇总，无就医地机构明细）
create table pa_offsite_summary (
    as_of            date        primary key,
    source           varchar(32) not null,
    scope_note       varchar(64) not null,
    visits           int         not null,         -- 异地住院人次
    fund_yi          numeric(6,2) not null,        -- 异地就医基金支出（亿元）
    fund_share_pct   numeric(4,1) not null         -- 占全市统筹基金支出
);

create table pa_offsite_capacity (
    org    varchar(64) not null,
    as_of  date        not null references pa_offsite_summary(as_of),
    visits int         not null,                   -- 与本院收治能力相关的外流人次
    note   varchar(32) not null,
    primary key (org, as_of)
);

create table pa_offsite_disease (
    as_of          date        not null references pa_offsite_summary(as_of),
    name           varchar(32) not null,
    visits         int         not null,
    fund_share_pct numeric(4,1) not null,
    sort           int         not null,
    primary key (as_of, name)
);

create table pa_offsite_region (
    as_of          date        not null references pa_offsite_summary(as_of),
    name           varchar(16) not null,
    scope          varchar(4)  not null,           -- 省内 / 省外 / —
    fund_share_pct numeric(4,1) not null,
    sort           int         not null,
    primary key (as_of, name)
);

create table pa_offsite_level (
    as_of     date        not null references pa_offsite_summary(as_of),
    level     varchar(8)  not null,
    share_pct int         not null,
    sort      int         not null,
    primary key (as_of, level)
);

-- ================================================================ 种子数据（设计稿第三批样例）

insert into pa_peer_org values
 ('示例市妇幼保健院',   '市三级', 1),
 ('示例市第二人民医院', '市三级', 2),
 ('示例市第一人民医院', '市三级', 3),
 ('示例市中医院',       '市三级', 4),
 ('示例市第三人民医院', '市三级', 5),
 ('示例市肿瘤医院',     '市三级', 6);

insert into pa_bench_indicator values
 ('例均基金差额',   '元', 0, true,  false, '医保记账 − DRG 支付标准,正值为逆差'),
 ('次均总费用',     '元', 0, false, false, '住院病例次均总费用'),
 ('CMI值',          '',   2, false, true,  '病例组合指数'),
 ('费用消耗指数',   '',   2, false, false, '本院费用 / 同级同病组平均费用'),
 ('结算清单质控率', '%',  1, false, true,  '结算清单一次质控通过率'),
 ('14天再住院率',   '%',  1, false, false, '出院 14 天内同病再住院比例');

-- 6 家 × 6 指标（2026-08）
insert into pa_bench_value (period, org, indicator, value)
select '2026-08', o, i, v from (values
 ('示例市第一人民医院', '例均基金差额', 486),   ('示例市第一人民医院', '次均总费用', 12860), ('示例市第一人民医院', 'CMI值', 1.12),
 ('示例市第一人民医院', '费用消耗指数', 1.04),  ('示例市第一人民医院', '结算清单质控率', 96.4), ('示例市第一人民医院', '14天再住院率', 3.2),
 ('示例市妇幼保健院',   '例均基金差额', -233),  ('示例市妇幼保健院',   '次均总费用', 10120), ('示例市妇幼保健院',   'CMI值', 0.91),
 ('示例市妇幼保健院',   '费用消耗指数', 0.88),  ('示例市妇幼保健院',   '结算清单质控率', 98.3), ('示例市妇幼保健院',   '14天再住院率', 1.9),
 ('示例市第二人民医院', '例均基金差额', 610),   ('示例市第二人民医院', '次均总费用', 13607), ('示例市第二人民医院', 'CMI值', 1.18),
 ('示例市第二人民医院', '费用消耗指数', 1.01),  ('示例市第二人民医院', '结算清单质控率', 97.4), ('示例市第二人民医院', '14天再住院率', 4.1),
 ('示例市中医院',       '例均基金差额', -410),  ('示例市中医院',       '次均总费用', 10833), ('示例市中医院',       'CMI值', 0.98),
 ('示例市中医院',       '费用消耗指数', 0.95),  ('示例市中医院',       '结算清单质控率', 96.2), ('示例市中医院',       '14天再住院率', 2.3),
 ('示例市第三人民医院', '例均基金差额', -140),  ('示例市第三人民医院', '次均总费用', 11420), ('示例市第三人民医院', 'CMI值', 1.06),
 ('示例市第三人民医院', '费用消耗指数', 0.97),  ('示例市第三人民医院', '结算清单质控率', 95.1), ('示例市第三人民医院', '14天再住院率', 2.8),
 ('示例市肿瘤医院',     '例均基金差额', 880),   ('示例市肿瘤医院',     '次均总费用', 14580), ('示例市肿瘤医院',     'CMI值', 1.31),
 ('示例市肿瘤医院',     '费用消耗指数', 1.12),  ('示例市肿瘤医院',     '结算清单质控率', 94.0), ('示例市肿瘤医院',     '14天再住院率', 4.6)
) as t(o, i, v);

-- 本院病组：5 个重点病组 + 4 个小样本病组（病例 < 30，接口不单独下发）
insert into pa_group values
 ('示例市第一人民医院', '2026-08', 'BR25', '脑缺血性疾患,伴并发症',         253, 15194, 12459,  2219, 89, 10.5, 9.5, 1),
 ('示例市第一人民医院', '2026-08', 'IU29', '骨病及其他关节病',               110, 10176,  8344,  1553, 70,  9.7, 8.1, 2),
 ('示例市第一人民医院', '2026-08', 'BR11', '颅内出血性疾患,伴严重并发症',    36, 51840, 42509,  3472, 84, 11.6, 7.5, 3),
 ('示例市第一人民医院', '2026-08', 'ES35', '呼吸系统感染/炎症,伴并发症',    248, 10094,  8277,   276, 67, 10.1, 8.4, 4),
 ('示例市第一人民医院', '2026-08', 'FM19', '经皮心血管操作及支架置入',       123, 40144, 32918, -2432, 38, 11.8, 9.0, 5),
 ('示例市第一人民医院', '2026-08', 'AH29', '气管切开伴呼吸机支持≥96小时',    12, 98600, 91200,  1420, 76, 24.6, 22.1, 6),
 ('示例市第一人民医院', '2026-08', 'IB39', '脊柱融合手术',                     9, 72300, 70100,  -540, 41, 13.2, 12.4, 7),
 ('示例市第一人民医院', '2026-08', 'NS15', '女性生殖系统其他疾患',            19,  6820,  6950,  -162, 35,  5.4, 5.6, 8),
 ('示例市第一人民医院', '2026-08', 'JR15', '乳房良性病变',                    14,  8150,  8010,    44, 52,  4.1, 4.0, 9);

insert into pa_group_mix (org, period, code, category, own_pct, bench_pct, sort)
select '示例市第一人民医院', '2026-08', c, cat, o, b, s from (values
 ('BR25', '药品', 35, 28, 1), ('BR25', '耗材', 9, 6, 2),  ('BR25', '检查检验', 22, 19, 3), ('BR25', '治疗/手术', 18, 25, 4), ('BR25', '护理及其他', 16, 22, 5),
 ('IU29', '药品', 34, 27, 1), ('IU29', '耗材', 8, 5, 2),  ('IU29', '检查检验', 18, 15, 3), ('IU29', '治疗/手术', 14, 21, 4), ('IU29', '护理及其他', 26, 32, 5),
 ('BR11', '药品', 30, 23, 1), ('BR11', '耗材', 10, 7, 2), ('BR11', '检查检验', 22, 19, 3), ('BR11', '治疗/手术', 18, 25, 4), ('BR11', '护理及其他', 20, 26, 5),
 ('ES35', '药品', 31, 24, 1), ('ES35', '耗材', 9, 6, 2),  ('ES35', '检查检验', 20, 17, 3), ('ES35', '治疗/手术', 16, 23, 4), ('ES35', '护理及其他', 24, 30, 5),
 ('FM19', '药品', 18, 11, 1), ('FM19', '耗材', 34, 31, 2), ('FM19', '检查检验', 21, 18, 3), ('FM19', '治疗/手术', 17, 24, 4), ('FM19', '护理及其他', 10, 16, 5)
) as t(c, cat, o, b, s);

insert into pa_group_behavior (org, period, code, name, own_pct, peer_median_pct, higher_is_worse, sort)
select '示例市第一人民医院', '2026-08', c, n, o, b, w, s from (values
 ('BR25', '入院72小时内重复检查', 33, 27, true, 1), ('BR25', '使用辅助用药', 37, 34, true, 2), ('BR25', '使用高值耗材', 20, 18, true, 3), ('BR25', '康复治疗介入', 11, 15, false, 4),
 ('IU29', '入院72小时内重复检查', 35, 27, true, 1), ('IU29', '使用辅助用药', 43, 34, true, 2), ('IU29', '使用高值耗材', 23, 18, true, 3), ('IU29', '康复治疗介入',  8, 15, false, 4),
 ('BR11', '入院72小时内重复检查', 34, 27, true, 1), ('BR11', '使用辅助用药', 38, 34, true, 2), ('BR11', '使用高值耗材', 20, 18, true, 3), ('BR11', '康复治疗介入',  6, 15, false, 4),
 ('ES35', '入院72小时内重复检查', 29, 27, true, 1), ('ES35', '使用辅助用药', 35, 34, true, 2), ('ES35', '使用高值耗材', 19, 18, true, 3), ('ES35', '康复治疗介入',  6, 15, false, 4),
 ('FM19', '入院72小时内重复检查', 36, 27, true, 1), ('FM19', '使用辅助用药', 39, 34, true, 2), ('FM19', '使用高值耗材', 25, 18, true, 3), ('FM19', '康复治疗介入', 13, 15, false, 4)
) as t(c, n, o, b, w, s);

insert into pa_related_topic (org, title, status, target_page, link_label, sort) values
 ('示例市第一人民医院', 'BR25 专题(核对稿)', '待本院核对', 'B5', '去核对', 1),
 ('示例市第一人民医院', '2026年上半年 机构体检报告', null, 'B4', '查看', 2);

-- 区域外（截至 2026-08-31）
insert into pa_offsite_summary values
 ('2026-08-31', '省平台回流', '示例市参保人异地住院汇总 · 按地区、等级、病种 · 不含就医地机构明细', 4286, 4.87, 11.6);

insert into pa_offsite_capacity values
 ('示例市第一人民医院', '2026-08-31', 1104, '本院可开展的病种');

insert into pa_offsite_disease values
 ('2026-08-31', '恶性肿瘤化疗/放疗', 1286, 22.4, 1),
 ('2026-08-31', '冠心病介入治疗',     522,  9.1, 2),
 ('2026-08-31', '髋、膝关节置换',     301,  6.8, 3),
 ('2026-08-31', '先天性心脏病手术',    96,  4.2, 4),
 ('2026-08-31', '脑血管介入',         181,  3.9, 5);

insert into pa_offsite_region values
 ('2026-08-31', '省会市',   '省内', 38.2, 1),
 ('2026-08-31', '邻市',     '省内', 14.5, 2),
 ('2026-08-31', '外省A市',  '省外', 12.1, 3),
 ('2026-08-31', '外省B市',  '省外',  9.8, 4),
 ('2026-08-31', '外省C市',  '省外',  6.4, 5),
 ('2026-08-31', '其他地区', '—',    19.0, 6);

insert into pa_offsite_level values
 ('2026-08-31', '三级', 71, 1),
 ('2026-08-31', '二级', 21, 2),
 ('2026-08-31', '其他',  8, 3);
