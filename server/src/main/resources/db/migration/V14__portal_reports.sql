-- 第三批 · 机构门户报告与意见（B4 报告中心 / B5 意见与机构核对 / B6 政策与培训 / D1 移动端）
-- 本组只建 pr_ 前缀表；另向第二批表写入演示数据（见文末）：
--   opinion_ticket：本院一条已答复的数据异议（B5「我的意见」列表的样例）；
--   alert_trigger ：本院「医保外费用占比」预警（D1 预警卡与详情的数据来源，A11 同步可见）。

-- ---------------------------------------------------------------- B4 定向发布报告（按机构定向，每份带机构专属水印编号）
create table pr_report (
    id           bigserial primary key,
    org          varchar(64)  not null,
    title        varchar(64)  not null,
    kind         varchar(8)   not null,          -- 月度报告 / 专题报告 / 体检报告
    published_on date         not null,
    pages        int          not null,
    status       varchar(8)   not null,          -- SIGN 待签收 / CHECK 核对中 / SIGNED 已签收 / OLD 已更正（原版本只读保留）
    wm_no        varchar(24)  not null,          -- 定向发布时嵌入的水印编号
    body         jsonb        not null,          -- 预览正文：[{h, p}] 或 [{h, head[], rows[][], diff?}]（diff = 末列按差额着色）
    signed_at    timestamptz,
    signed_by    varchar(32),
    sort         int          not null
);
create index ix_pr_report_org on pr_report(org, sort);

-- ---------------------------------------------------------------- B5 机构核对（核对期内逐项确认；截止后未处理项视为确认）
create table pr_check_round (
    id        bigserial primary key,
    org       varchar(64)  not null,
    title     varchar(64)  not null,
    report_id bigint       not null references pr_report(id),
    deadline  timestamptz  not null
);

create table pr_check_item (
    round_id   bigint       not null references pr_check_round(id),
    idx        int          not null,
    label      varchar(32)  not null,
    value      varchar(16)  not null,
    decision   varchar(8),                       -- OK 确认 / OBJECT 有异议；null = 未处理
    decided_at timestamptz,
    decided_by varchar(32),
    ticket_no  varchar(16),                      -- 有异议时关联的意见工单（opinion_ticket.no）
    primary key (round_id, idx)
);

-- 意见可关联的指标 / 报告段落（提交意见必须从中选择）
create table pr_opinion_ref (
    id        bigserial primary key,
    org       varchar(64)  not null,
    label     varchar(64)  not null,
    report_id bigint references pr_report(id),   -- 报告段落所属报告（指标类为空）
    sort      int          not null
);

-- 工单号序号：YJ-MMDD-NNN
create sequence pr_opinion_seq start 41;

-- ---------------------------------------------------------------- B6 政策文件、培训课件与随堂试题（全市机构共用）
create table pr_policy_doc (
    id        bigserial primary key,
    category  varchar(8)   not null,             -- 分组方案 / 基准点数 / 特例单议
    title     varchar(64)  not null,
    doc_no    varchar(32)  not null,             -- 文号（检索用）
    issued_on date         not null,
    format    varchar(16)  not null,
    sort      int          not null
);

create table pr_course (
    id      bigserial primary key,
    title   varchar(64) not null,
    minutes int         not null,
    sort    int         not null
);

create table pr_course_progress (
    user_id   bigint not null references app_user(id),
    course_id bigint not null references pr_course(id),
    pct       int    not null,
    primary key (user_id, course_id)
);

-- 随堂单选题：正确答案只在服务端，判分走分析引擎；作答前不下发
create table pr_quiz (
    id        bigserial primary key,
    course_id bigint       not null references pr_course(id),
    question  varchar(128) not null,
    options   text[]       not null,
    answer    int          not null,             -- 正确选项下标（0 起）
    explain   varchar(64)  not null
);

create table pr_quiz_answer (
    user_id     bigint      not null references app_user(id),
    quiz_id     bigint      not null references pr_quiz(id),
    picked      int         not null,
    correct     boolean     not null,
    answered_at timestamptz not null default now(),
    primary key (user_id, quiz_id)
);

-- ---------------------------------------------------------------- D1 移动端摘要（本院当期发布值）与预警详情
create table pr_org_summary (
    org     varchar(64) primary key,
    period  varchar(16) not null,
    kpis    jsonb       not null,                -- [{label, value, sub, tone}]
    groups  jsonb       not null                 -- [{code, name, diff}]
);

create table pr_alert_note (
    trigger_id bigint      primary key references alert_trigger(id),
    title      varchar(32) not null,
    value      varchar(16) not null,
    percentile int         not null,             -- 本院分位
    line       int         not null,             -- 关注线分位
    summary    varchar(64) not null,
    narrative  text        not null
);

-- ================================================================ 样例数据（设计稿第三批 B4 / B5 / B6 / D1）
insert into pr_report (org, title, kind, published_on, pages, status, wm_no, signed_at, signed_by, sort, body) values
 ('示例市第一人民医院', '2026年8月 DRG月度运行报告', '月度报告', '2026-09-12', 18, 'SIGN', 'WM-20260912-4400', null, null, 1,
  '[{"h":"一、本院运行概况","p":"2026年8月,本院 DRG 结算病例 3,214 例,医保记账总额 4,862.4 万元,DRG 支付总额 4,615.7 万元,例均基金差额 +486 元,位于同级 P62。CMI 值 1.12,位于同级 P68。"},
    {"h":"二、重点病组","head":["编码","病组","病例","例均差额"],"diff":true,"rows":[["BR25","脑缺血性疾患,伴并发症","286","+2,140"],["IU29","骨病及其他关节病","148","+1,380"],["FM19","经皮心血管操作及支架置入","132","−1,960"]]},
    {"h":"三、同级对标","p":"医保外费用占比 6.8%,位于同级 P72,高于 P70 关注线;其余核心指标位于 P25–P75 区间。"}]'),
 ('示例市第一人民医院', 'BR25 脑缺血性疾患专题(核对稿)', '专题报告', '2026-09-20', 24, 'CHECK', 'WM-20260920-4407', null, null, 2,
  '[{"h":"一、整体描述","p":"2026年8月,本院 BR25 结算病例 286 例,例均基金差额 +2,140 元,合并症编码率 74%,平均住院日 11.6 天。"},
    {"h":"二、关键行为","head":["行为","本院","同级中位","偏差"],"rows":[["入院72小时内重复检查","31%","27%","+4 pt"],["使用辅助用药","38%","34%","+4 pt"],["康复治疗介入","9%","15%","−6 pt"]]},
    {"h":"三、差异归因","p":"例均基金差额中,患者结构差异约占 38%,诊疗行为差异约占 62%。本稿为核对稿,请于核对期内确认数据,异议请在意见通道提交。"}]'),
 ('示例市第一人民医院', '2026年7月 DRG月度运行报告(更正版 v2)', '月度报告', '2026-09-03', 18, 'SIGNED', 'WM-20260903-4414', '2026-09-13 09:20+08', '李敏', 3,
  '[{"h":"一、本院运行概况","p":"2026年7月,本院 DRG 结算病例 3,108 例,医保记账总额 4,701.6 万元,DRG 支付总额 4,522.9 万元,例均基金差额 +575 元,位于同级 P64。CMI 值 1.11,位于同级 P66。"},
    {"h":"二、重点病组","head":["编码","病组","病例","例均差额"],"diff":true,"rows":[["BR25","脑缺血性疾患,伴并发症","271","+2,310"],["IU29","骨病及其他关节病","139","+1,420"],["FM19","经皮心血管操作及支架置入","127","−1,880"]]},
    {"h":"更正说明","p":"v1 中 BR25 误含 12 例经批复的特例单议病例,本版已剔除,BR25 病例数由 283 例更正为 271 例,例均基金差额相应更正。"}]'),
 ('示例市第一人民医院', '2026年7月 DRG月度运行报告 v1', '月度报告', '2026-08-11', 18, 'OLD', 'WM-20260811-4421', '2026-08-12 10:05+08', '李敏', 4,
  '[{"h":"一、本院运行概况","p":"2026年7月,本院 DRG 结算病例 3,120 例,医保记账总额 4,718.2 万元,DRG 支付总额 4,522.9 万元,例均基金差额 +626 元,位于同级 P66。CMI 值 1.11,位于同级 P66。"},
    {"h":"二、重点病组","head":["编码","病组","病例","例均差额"],"diff":true,"rows":[["BR25","脑缺血性疾患,伴并发症","283","+2,460"],["IU29","骨病及其他关节病","139","+1,420"],["FM19","经皮心血管操作及支架置入","127","−1,880"]]},
    {"h":"三、同级对标","p":"医保外费用占比 6.5%,位于同级 P69;其余核心指标位于 P25–P75 区间。"}]'),
 ('示例市第一人民医院', '2026年上半年 机构体检报告', '体检报告', '2026-07-28', 32, 'SIGNED', 'WM-20260728-4428', '2026-07-30 15:06+08', '李敏', 5,
  '[{"h":"一、总体评价","p":"2026年上半年,本院 DRG 结算病例 18,906 例,CMI 值 1.10,位于同级 P66;医保基金使用总体平稳,例均基金差额 +402 元。"},
    {"h":"二、需关注病组","head":["编码","病组","病例","例均差额"],"diff":true,"rows":[["BR25","脑缺血性疾患,伴并发症","1,602","+1,980"],["IU29","骨病及其他关节病","861","+1,250"],["GG19","肛门及肛周手术","412","+860"]]},
    {"h":"三、改进建议","p":"建议加强 BR25、IU29 病组临床路径管理,规范自费耗材告知与使用;持续提升结算清单质控率至 97% 以上。"}]'),
 -- 他院报告：用于验证「只返回本院数据」
 ('甲县人民医院', '2026年8月 DRG月度运行报告', '月度报告', '2026-09-12', 16, 'SIGN', 'WM-20260912-4435', null, null, 1,
  '[{"h":"一、本院运行概况","p":"2026年8月,本院 DRG 结算病例 2,860 例,例均基金差额 +712 元。"}]');

insert into pr_check_round (org, title, report_id, deadline)
select org, 'BR25 专题(核对稿)', id, '2026-10-05 18:00+08' from pr_report where org = '示例市第一人民医院' and status = 'CHECK';

insert into pr_check_item (round_id, idx, label, value)
select r.id, x.idx, x.label, x.value from pr_check_round r,
 (values (0, 'BR25 本院病例数', '286 例'), (1, 'BR25 例均基金差额', '+2,140 元'), (2, 'BR25 合并症编码率', '74%'), (3, 'BR25 平均住院日', '11.6 天'))
 as x(idx, label, value)
where r.org = '示例市第一人民医院';

insert into pr_opinion_ref (org, label, report_id, sort)
select '示例市第一人民医院', x.label, (select id from pr_report where org = '示例市第一人民医院' and title = x.rpt), x.sort
from (values ('8月月度报告 §3.2 BR25', '2026年8月 DRG月度运行报告', 1),
             ('BR25 专题核对稿 §4 差异归因', 'BR25 脑缺血性疾患专题(核对稿)', 2),
             ('指标:医保外费用占比', null, 3),
             ('指标:14天再住院率', null, 4),
             ('8月月度报告 §2.1', '2026年8月 DRG月度运行报告', 5)) as x(label, rpt, sort);

insert into pr_policy_doc (category, title, doc_no, issued_on, format, sort) values
 ('分组方案', '示例市 DRG 分组方案(2026 版)',      '示医保发〔2025〕41号', '2026-01-01', 'PDF · 86 页', 1),
 ('分组方案', 'CHS-DRG 2.0 细分组目录对照表',      '示医保办〔2025〕77号', '2025-12-15', '表格',        2),
 ('基准点数', '2026年度病组基准点数与费率',        '示医保发〔2026〕3号',  '2026-01-10', '表格',        3),
 ('基准点数', '2026年第三季度点值测算说明',        '示医保办〔2026〕52号', '2026-07-15', 'PDF · 12 页', 4),
 ('特例单议', '特例单议申报与评审办法',            '示医保发〔2025〕38号', '2025-11-20', 'PDF · 9 页',  5),
 ('特例单议', '2026年8月特例单议评审结果',         '示医保办〔2026〕68号', '2026-09-08', '表格',        6);

insert into pr_course (title, minutes, sort) values
 ('DRG 付费下的病案首页填写规范', 45, 1),
 ('结算清单质控要点', 30, 2),
 ('如何读懂月度运行报告', 20, 3);

insert into pr_course_progress (user_id, course_id, pct)
select 7, id, case sort when 1 then 100 when 2 then 60 else 0 end from pr_course;

insert into pr_quiz (course_id, question, options, answer, explain)
select id, '本院在“次均费用”分位条上位于 P72,表示什么?',
       array['本院次均费用高于 72% 的全国医院', '本院次均费用高于同级约 72% 的机构', '本院排名第 72 位', '本院费用超标 72%'],
       1, '分位只在同级机构内比较'
from pr_course where sort = 3;

insert into pr_org_summary (org, period, kpis, groups) values
 ('示例市第一人民医院', '2026年8月',
  '[{"label":"例均基金差额","value":"+486 元","sub":"同级 P62","tone":"danger"},
    {"label":"CMI","value":"1.12","sub":"同级 P68"},
    {"label":"记账偏离","value":"逆差 246.7 万","tone":"danger"},
    {"label":"清单质控率","value":"96.4%"}]',
  '[{"code":"BR25","name":"脑缺血性疾患","diff":2140},{"code":"IU29","name":"骨病及关节病","diff":1380},{"code":"FM19","name":"心血管支架","diff":-1960}]');

-- ================================================================ 写入第二批表（演示数据）
-- 本院一条已答复意见（B5「我的意见」：可五星评价，评价写回 opinion_ticket.rating，A10 可见）
insert into opinion_ticket (no, org, ref, category, owner, due_date, status, content, reply, replied_at, rating, created_at) values
 ('YJ-0906-012', '示例市第一人民医院', '指标:医保外费用占比', '数据异议', '周婷', null, 'DONE',
  '本院 8 月医保外费用中含 3 例临床试验费用,是否剔除?',
  '临床试验费用已于 9 月口径中剔除,本院更正后为 6.1%。', '2026-09-08 16:40+08', null, '2026-09-06');

-- 本院预警（D1 预警卡 → 详情；A11 触发记录同步可见，状态为已发出待回执）
with t as (
    insert into alert_trigger (trig_date, period, org, drg_group, rule_name, value, status, letter_seq, receipt, sent_at, sort)
    values ('09-12', '2026年8月', '示例市第一人民医院', 'BR25、IU29 自费耗材', '医保外费用占比', '6.8%(同级 P72)', 'SENT', 22, null,
            '2026-09-12 10:00+08', 6)
    returning id)
insert into pr_alert_note (trigger_id, title, value, percentile, line, summary, narrative)
select id, '医保外费用占比偏高', '6.8%', 72, 70, '6.8% · 高于同级 P70 关注线',
       '近 3 个月环比上升 1.2 个百分点,主要来自 BR25、IU29 两个病组的自费耗材。请医保办在 PC 端提交原因说明回执。'
from t;
