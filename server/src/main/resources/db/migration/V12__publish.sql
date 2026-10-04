-- 发布工作流（A8）：待办分组、发布流程实例、发布包、定向范围、分受众版本、审批留痕、更正与撤回、档位切换审批
-- 只建 pub_ 前缀的表；只读第二批 flow_template / flow_node / report_draft / opinion_ticket，
-- 对 benchmark_tier / tier_change_request 的写入见交付说明（召集人批准档位切换）。

-- ---------------------------------------------------------------- 定向范围：机构名单与维度
create table pub_org (
    id        serial primary key,
    name      varchar(64) not null unique,
    tier      varchar(8)  not null,             -- 等级：市三级 / 县三级 / 二级甲等 / 二级其他 / 一级
    district  varchar(8)  not null,             -- 县区
    batch     varchar(8)  not null,             -- 付费批次
    alliance  varchar(16) not null,             -- 医共体（— 表示不属于医共体）
    groups    text[]      not null,             -- 收治病组（达到发布阈值的重点病组）
    sort      int         not null
);

-- 选择器维度取值（顺序即界面顺序）；单选维度首项为「不限 / 全部」
create table pub_scope_dim (
    dim    varchar(16) not null,                -- tiers / districts / batch / alliance / group
    value  varchar(16) not null,
    label  varchar(16) not null,
    sort   int         not null,
    primary key (dim, value)
);

-- ---------------------------------------------------------------- 发布流程实例
create table pub_flow (
    id              bigserial primary key,
    code            varchar(16) unique,         -- 样例编码；A5 草稿生成的流程为空
    kind            varchar(16) not null,       -- 待办分组：月告知 / 季公布 / 年通报 / 病组与机构专题 / 预警提醒函 / 更正与撤回
    template_id     bigint      not null references flow_template(id),
    name            varchar(64) not null,
    subject         varchar(64) not null,       -- 发布物名称：更正 / 撤回按此关联原版本
    package_version varchar(8)  not null default 'v1',
    step            int         not null,       -- 当前步（流程模板节点序号）；最后一步为已归档
    due_date        date,
    due_prefix      varchar(8)  not null default '',   -- 时限前缀：核对 / 回执
    scope           jsonb       not null,       -- {tiers[], districts[], batch, alliance, group}
    report_draft_id bigint      unique references report_draft(id),
    action          varchar(4),                 -- 更正 / 撤回（仅「更正与撤回」流程）
    explanation     text,                       -- 更正说明 / 撤回理由
    sort            int         not null default 0,
    created_at      timestamptz not null default now()
);

-- 发布包内容；internal = 「仅内部」项，自动排除、不进入发布区
create table pub_package_item (
    flow_id  bigint      not null references pub_flow(id),
    sort     int         not null,
    name     varchar(32) not null,
    detail   varchar(64) not null,
    internal boolean     not null default false,
    primary key (flow_id, sort)
);

-- 分受众版本预览（按「谁在看 × 数据归谁」裁剪后的样例）
create table pub_audience_version (
    id        int primary key,
    title     varchar(32) not null,
    subtitle  varchar(48) not null,
    note      varchar(48),
    note_tone varchar(8)
);

create table pub_audience_line (
    version_id int         not null references pub_audience_version(id),
    sort       int         not null,
    label      varchar(32) not null,
    value      varchar(32) not null,
    tone       varchar(8)  not null default 'default',   -- default / danger / success / muted
    primary key (version_id, sort)
);

-- 审批与全程留痕（只增不改）
create table pub_log (
    id      bigserial primary key,
    flow_id bigint       not null references pub_flow(id),
    at      timestamptz  not null default now(),
    who     varchar(48)  not null,
    what    varchar(256) not null
);
create index ix_pub_log_flow on pub_log(flow_id, at, id);

-- 已发布版本：更正后原版保留只读（SUPERSEDED），撤回标记 WITHDRAWN
create table pub_release (
    id           bigserial primary key,
    subject      varchar(64) not null,
    version      int         not null,
    flow_id      bigint      references pub_flow(id),
    published_on date        not null,
    signed       int         not null default 0,
    total        int         not null,
    status       varchar(12) not null,          -- CURRENT / SUPERSEDED / WITHDRAWN
    note         varchar(64) not null default '',
    unique (subject, version)
);

-- 档位切换单的审批结论（申请单本身在第二批 tier_change_request）
create table pub_tier_decision (
    request_id bigint       primary key references tier_change_request(id),
    decision   varchar(12)  not null,           -- APPROVED / REJECTED
    opinion    varchar(256),
    decided_by bigint       not null references app_user(id),
    decided_at timestamptz  not null default now()
);

-- ================================================================ 样例数据（与设计稿第一批 A8 一致，全部虚构）

insert into pub_org (name, tier, district, batch, alliance, groups, sort) values
 ('示例市第一人民医院', '市三级', '市区', '第二批', '—', '{BR25,GG19}', 1),
 ('示例市第二人民医院', '市三级', '丙区', '第一批', '—', '{BR25,GG19}', 2),
 ('示例市第三人民医院', '市三级', '市区', '第一批', '—', '{BR25}', 3),
 ('示例市中医院', '市三级', '丙区', '第二批', '—', '{BR25}', 4),
 ('示例市妇幼保健院', '市三级', '市区', '第一批', '—', '{BR25}', 5),
 ('示例市肿瘤医院', '市三级', '丙区', '第一批', '—', '{BR25}', 6),
 ('甲县人民医院', '县三级', '甲县', '第一批', '甲县医共体', '{BR25}', 7),
 ('乙县人民医院', '县三级', '乙县', '第一批', '乙县医共体', '{BR25}', 8),
 ('甲县中医院', '县三级', '甲县', '第二批', '甲县医共体', '{BR25}', 9),
 ('乙县中医院', '县三级', '乙县', '第一批', '乙县医共体', '{BR25}', 10),
 ('甲县第1医院', '二级甲等', '甲县', '第一批', '甲县医共体', '{BR25}', 11),
 ('乙县第2医院', '二级甲等', '乙县', '第二批', '乙县医共体', '{BR25,GG19}', 12),
 ('市区第3医院', '二级甲等', '市区', '第一批', '—', '{BR25}', 13),
 ('丙区第4医院', '二级甲等', '丙区', '第一批', '—', '{BR25}', 14),
 ('甲县第5医院', '二级甲等', '甲县', '第二批', '甲县医共体', '{BR25}', 15),
 ('乙县第6医院', '二级甲等', '乙县', '第一批', '乙县医共体', '{BR25,GG19}', 16),
 ('市区第7医院', '二级甲等', '市区', '第一批', '—', '{BR25}', 17),
 ('丙区第8医院', '二级甲等', '丙区', '第二批', '—', '{BR25}', 18),
 ('甲县第9医院', '二级甲等', '甲县', '第一批', '甲县医共体', '{BR25}', 19),
 ('某肛肠专科医院', '二级其他', '乙县', '第二批', '乙县医共体', '{BR25,GG19}', 20),
 ('市区康复医院2院', '二级其他', '市区', '第一批', '—', '{GG19}', 21),
 ('丙区康复医院3院', '二级其他', '丙区', '第一批', '—', '{GG19}', 22),
 ('甲县康复医院4院', '二级其他', '甲县', '第二批', '甲县医共体', '{BR25}', 23),
 ('乙县康复医院5院', '二级其他', '乙县', '第一批', '乙县医共体', '{}', 24),
 ('市区康复医院6院', '二级其他', '市区', '第一批', '—', '{}', 25),
 ('丙区康复医院7院', '二级其他', '丙区', '第二批', '—', '{BR25}', 26),
 ('甲县康复医院8院', '二级其他', '甲县', '第一批', '甲县医共体', '{}', 27),
 ('乙县康复医院9院', '二级其他', '乙县', '第一批', '乙县医共体', '{}', 28),
 ('市区康复医院10院', '二级其他', '市区', '第二批', '—', '{BR25}', 29),
 ('丙区康复医院11院', '二级其他', '丙区', '第一批', '—', '{}', 30),
 ('市区第1社区卫生服务中心', '一级', '市区', '第一批', '—', '{BR25}', 31),
 ('丙区第2社区卫生服务中心', '一级', '丙区', '第一批', '—', '{}', 32),
 ('甲县第3社区卫生服务中心', '一级', '甲县', '第二批', '甲县医共体', '{}', 33),
 ('乙县第4社区卫生服务中心', '一级', '乙县', '第一批', '乙县医共体', '{BR25}', 34),
 ('市区第5社区卫生服务中心', '一级', '市区', '第一批', '—', '{}', 35),
 ('丙区第6社区卫生服务中心', '一级', '丙区', '第二批', '—', '{}', 36),
 ('甲县第7社区卫生服务中心', '一级', '甲县', '第一批', '甲县医共体', '{BR25}', 37),
 ('乙县第8社区卫生服务中心', '一级', '乙县', '第一批', '乙县医共体', '{}', 38),
 ('市区第9社区卫生服务中心', '一级', '市区', '第二批', '—', '{}', 39),
 ('丙区第10社区卫生服务中心', '一级', '丙区', '第一批', '—', '{BR25}', 40),
 ('甲县第11社区卫生服务中心', '一级', '甲县', '第一批', '甲县医共体', '{}', 41),
 ('乙县第12社区卫生服务中心', '一级', '乙县', '第二批', '乙县医共体', '{}', 42),
 ('市区第13社区卫生服务中心', '一级', '市区', '第一批', '—', '{BR25}', 43),
 ('丙区第14社区卫生服务中心', '一级', '丙区', '第一批', '—', '{}', 44),
 ('甲县第15社区卫生服务中心', '一级', '甲县', '第二批', '甲县医共体', '{}', 45),
 ('乙县第16社区卫生服务中心', '一级', '乙县', '第一批', '乙县医共体', '{BR25}', 46),
 ('市区第17社区卫生服务中心', '一级', '市区', '第一批', '—', '{}', 47),
 ('丙区第18社区卫生服务中心', '一级', '丙区', '第二批', '—', '{}', 48),
 ('甲县第19社区卫生服务中心', '一级', '甲县', '第一批', '甲县医共体', '{BR25}', 49),
 ('乙县第20社区卫生服务中心', '一级', '乙县', '第一批', '乙县医共体', '{}', 50),
 ('市区第21社区卫生服务中心', '一级', '市区', '第二批', '—', '{}', 51),
 ('丙区第22社区卫生服务中心', '一级', '丙区', '第一批', '—', '{BR25}', 52);

insert into pub_scope_dim (dim, value, label, sort) values
 ('tiers', '市三级', '市三级', 1), ('tiers', '县三级', '县三级', 2), ('tiers', '二级甲等', '二级甲等', 3),
 ('tiers', '二级其他', '二级其他', 4), ('tiers', '一级', '一级', 5),
 ('districts', '市区', '市区', 1), ('districts', '丙区', '丙区', 2), ('districts', '甲县', '甲县', 3), ('districts', '乙县', '乙县', 4),
 ('batch', '全部', '全部', 1), ('batch', '第一批', '第一批', 2), ('batch', '第二批', '第二批', 3),
 ('alliance', '不限', '不限', 1), ('alliance', '甲县医共体', '甲县医共体', 2), ('alliance', '乙县医共体', '乙县医共体', 3),
 ('group', '不限', '不限', 1), ('group', 'BR25', '收治BR25', 2), ('group', 'GG19', '收治GG19', 3);

-- 定向范围默认全选
insert into pub_flow (id, code, kind, template_id, name, subject, package_version, step, due_date, due_prefix, scope, action, explanation, sort)
select f.id, f.code, f.kind, t.id, f.name, f.subject, f.ver, f.step, f.due, f.prefix,
       jsonb_build_object('tiers', jsonb_build_array('市三级','县三级','二级甲等','二级其他','一级'),
                          'districts', jsonb_build_array('市区','丙区','甲县','乙县'),
                          'batch', '全部', 'alliance', '不限', 'group', f.grp),
       f.action, f.expl, f.id
from (values
 (1, 'm8',   '月告知',         '月告知',     '2026年8月 DRG月度运行告知',     '2026年8月 DRG月度运行报告',      'v3', 5,  current_date + 1,  '',     '不限', null, null),
 (2, 'w8',   '月告知',         '月告知',     '2026年8月 运行预警汇总',        '2026年8月 运行预警汇总',         'v1', 3,  current_date + 4,  '',     '不限', null, null),
 (3, 'q3',   '季公布',         '季公布',     '2026年第三季度运行公布',        '2026年第三季度运行公布',         'v1', 2,  current_date + 18, '',     '不限', null, null),
 (4, 'y25',  '年通报',         '年通报',     '2025年度支付方式改革通报',      '2025年度支付方式改革通报',       'v2', 10, null,              '',     '不限', null, null),
 (5, 'br25', '病组与机构专题', '病组专题',   'BR25 脑缺血性疾患专题',         'BR25 脑缺血性疾患专题报告',      'v2', 4,  current_date + 2,  '核对', 'BR25', null, null),
 (6, 'org',  '病组与机构专题', '病组专题',   '2026年上半年机构体检报告',      '2026年上半年机构体检报告',       'v1', 3,  current_date + 6,  '',     '不限', null, null),
 (7, 'gg19', '预警提醒函',     '预警提醒函', 'GG19 次均费用预警提醒函',       'GG19 次均费用预警提醒函',        'v1', 7,  current_date + 3,  '回执', 'GG19', null, null),
 (8, 'c7',   '更正与撤回',     '更正与撤回', '2026年7月月度报告更正',         '2026年7月 DRG月度运行报告',      'v2', 10, null,              '',     '不限', '更正',
  '第 3.2 节甲县人民医院 BR25 例均基金差额由 +1,320 元更正为 +960 元。原因:8 月补传 7 月清算数据 412 条。受影响机构 3 家已重新签收。')
) as f(id, code, kind, tpl, name, subject, ver, step, due, prefix, grp, action, expl)
join flow_template t on t.kind = f.tpl;
select setval('pub_flow_id_seq', 8);

-- 发布包内容：样例流程共用同一组成，报告项按流程类型命名
insert into pub_package_item (flow_id, sort, name, detail, internal)
select f.id, i.sort, coalesce(case when i.sort = 2 then f.rpt end, i.name), coalesce(case when i.sort = 2 then f.rpt_detail end, i.detail), i.internal
from (values
 (1, '月度运行报告', 'v3 · 18 页'), (2, '运行预警汇总', 'v1 · 6 页'), (3, '季度运行公布', 'v1 · 24 页'),
 (4, '年度通报', 'v2 · 32 页'), (5, '病组专题报告', 'v2 · 七段 · 14 页'), (6, '机构体检报告', 'v1 · 52 份'),
 (7, '预警提醒函', 'v1 · 9 份'), (8, '月度运行报告(更正版)', 'v2 · 18 页')
) as f(id, rpt, rpt_detail)
cross join (values
 (1, '指标集',             '12 项 · 必选 8 / 增选 4', false),
 (2, null,                 null,                      false),
 (3, '解读',               '6 段 · 已人工审定',       false),
 (4, '常见问答',           '9 条',                    false),
 (5, '方法卡',             '4 张',                    false),
 (6, '单病例费用明细',     '病例级 · 仅内部',         true),
 (7, '参保人就医轨迹',     '个人级 · 仅内部',         true)
) as i(sort, name, detail, internal);

insert into pub_audience_version values
 (1, '示例市第一人民医院版',       '市三级 · 本院具名 + 同级匿名分位', '其他医院:不显示名称与数值',      'muted'),
 (2, '甲县第3社区卫生服务中心版',  '一级 · 本院具名 + 同级匿名分位',   '病例数 < 30 的病组已并入“其他”', 'warning'),
 (3, '甲县医保局版',               '本县区机构具名 · 其他县区汇总',    null,                              null);

insert into pub_audience_line values
 (1, 1, '例均基金差额',         '+486 元 · P62',           'danger'),
 (1, 2, 'CMI 值',               '1.12 · P68',              'default'),
 (1, 3, '费用消耗指数',         '1.04 · P61',              'default'),
 (1, 4, '结算清单质控率',       '96.4% · 第 3 名',         'default'),
 (1, 5, '重点病组',             'BR25 / IU29 / BR11',      'default'),
 (2, 1, '次均总费用',           '3,120 元 · P41',          'default'),
 (2, 2, '例均基金差额',         '−64 元 · P35',            'success'),
 (2, 3, '结算清单质控率',       '91.2% · P30',             'default'),
 (2, 4, '病组明细',             '6 个病组',                'default'),
 (3, 1, '甲县人民医院',         '+712 元',                 'danger'),
 (3, 2, '甲县中医院',           '−138 元',                 'success'),
 (3, 3, '甲县基层机构(9 家)',   '−41 元',                  'default'),
 (3, 4, '其他县区汇总',         '例均 +96 元 · 甲县排第 3', 'muted'),
 (3, 5, '医共体监测指标',       '14 项',                   'default');

insert into pub_log (flow_id, at, who, what) values
 (1, '2026-09-08 10:12+08', '李华 · 行政管理组', '提交发布包 v3(指标集 12 项,月度运行报告 18 页)'),
 (1, '2026-09-09 16:40+08', '专家组 3/3',        '审核通过,附 2 条文字修改意见,已采纳'),
 (1, '2026-09-10 09:05+08', '机构核对',          '52 家中 49 家确认,3 家异议已转意见工单'),
 (1, '2026-09-10 14:20+08', '系统',              '进入第 5 步:召集人审批'),
 (2, '2026-09-10 08:30+08', '系统',              '规则扫描生成 8 月运行预警汇总初稿'),
 (2, '2026-09-11 15:02+08', '李华 · 行政管理组', '完成「归集校验」,进入「分析成稿」'),
 (3, '2026-10-01 09:00+08', '系统',              '第三季度归集启动,10 类数据源待到数'),
 (4, '2026-02-10 10:30+08', '陈志远 · 召集人',   '批准发布'),
 (4, '2026-02-10 10:31+08', '系统',              '定向发布至 52 家机构,签收期 5 个工作日'),
 (4, '2026-03-20 16:05+08', '李华 · 行政管理组', '归档复盘完成'),
 (5, '2026-09-20 11:18+08', '张悦 · 委托分析团队', '七段文稿提交审核'),
 (5, '2026-09-22 09:00+08', '系统',              '进入第 4 步:专家组审核,机构同步核对'),
 (6, '2026-09-25 17:40+08', '李华 · 行政管理组', '生成 52 份机构体检报告初稿,进入「分析成稿」'),
 (7, '2026-09-15 10:02+08', '陈志远 · 召集人',   '批准发出'),
 (7, '2026-09-15 10:03+08', '系统',              '定向发送至 7 家机构,回执期 5 个工作日'),
 (8, '2026-08-28 09:40+08', '李华 · 行政管理组', '发起更正:甲县人民医院 BR25 例均基金差额'),
 (8, '2026-09-02 15:10+08', '专家组 3/3',        '复核通过'),
 (8, '2026-09-03 10:20+08', '陈志远 · 召集人',   '批准更正发布'),
 (8, '2026-09-03 10:21+08', '系统',              '原版 v1 保留只读,v2 为现行版本;受影响机构 3 家重新签收');

insert into pub_release (subject, version, flow_id, published_on, signed, total, status, note) values
 ('2026年7月 DRG月度运行报告', 1, null, '2026-08-11', 52, 52, 'SUPERSEDED', '原版本只读可查'),
 ('2026年7月 DRG月度运行报告', 2, 8,    '2026-09-03', 52, 52, 'CURRENT',    '经专家组复核与召集人审批'),
 ('2025年度支付方式改革通报',  1, 4,    '2026-02-10', 52, 52, 'CURRENT',    '经召集人审批'),
 ('GG19 次均费用预警提醒函',   1, 7,    '2026-09-15', 4,  7,  'CURRENT',    '经召集人审批');

-- 待召集人审批的档位切换单（第二批 A13 发起；本迁移写入一张样例，见交付说明）
insert into tier_change_request (indicator, from_tier, to_tier, reason, approver, status, requested_by, created_at) values
 ('14天再住院率', 0, 1, '机构普遍认可该指标口径,拟改为匿名编号便于同级横向对比。', '陈志远(召集人)', 'PENDING', 2, '2026-09-28 10:15+08');
