-- 医保数据公开定向发布平台 · 第二批（A1 A3 A5 A7 A9–A14）表结构

-- ---------------------------------------------------------------- 账号与身份（A1）
create table app_user (
    id            bigserial primary key,
    username      varchar(64)  not null unique,
    name          varchar(32)  not null,
    phone_masked  varchar(20),
    -- 已识别数字证书（UKey）：颁发机构与有效期，演示环境由服务端模拟识别
    cert_issuer   varchar(64),
    cert_expires  date,
    enabled       boolean      not null default true,
    failed_count  int          not null default 0,
    locked_until  timestamptz,
    password_hash varchar(72),                  -- BCrypt；演示模式不校验
    token_version bigint       not null default 1
);

-- 一个账号可绑定多个身份，登录后选择本次身份；不同身份数据范围不同
create table user_identity (
    id         bigserial primary key,
    user_id    bigint       not null references app_user(id),
    role       varchar(32)  not null,          -- 角色代码，见 common/Roles.java
    role_label varchar(32)  not null,
    org        varchar(64)  not null,
    org_detail varchar(64)  not null,          -- 身份卡第二行（机构 · 部门）
    scope      varchar(64)  not null,          -- 数据范围
    home       varchar(16)  not null,          -- 首页页面编号
    home_label varchar(32)  not null,
    sort       int          not null default 0
);

-- ---------------------------------------------------------------- 数据归集（A3）
create table collection_period (
    period     varchar(7) primary key,          -- 2026-08
    qc_done    boolean     not null default false,
    qc_at      timestamptz,
    rule_count int         not null,
    org_count  int         not null,
    doctor_count int       not null,
    topic_count int        not null,
    indicator_total int    not null,
    completeness_pct numeric(5,1) not null,      -- 完整性
    consistency_pct  numeric(5,1) not null       -- 一致性
);

create table data_source (
    id            bigserial primary key,
    name          varchar(32)  not null unique,
    provider      varchar(64)  not null,
    access_mode   varchar(16)  not null,         -- 库表直连 / 文件上传 / 接口推送 / 省平台交换 / 标准下发
    frequency     varchar(8)   not null,
    status        varchar(8)   not null,         -- OK 已到数 / LATE 未按时到达 / PART 部分到数
    part_received int,
    part_total    int,
    quality_score int          not null,
    due_date      varchar(8),                    -- 应到数日（MM-DD）
    sort          int          not null
);

create table data_source_indicator (
    source_id bigint      not null references data_source(id),
    indicator varchar(32) not null,
    sort      int         not null,
    primary key (source_id, indicator)
);

create table org_quality (
    org             varchar(64) primary key,
    comorbidity_pct numeric(5,1) not null,      -- 合并症编码率
    list_qc_pct     numeric(5,1) not null,      -- 结算清单质控率
    sort            int          not null
);

create table indicator_lineage (
    indicator   varchar(32) primary key,
    card_version varchar(8) not null,
    batch_no    varchar(16) not null,
    theme_table varchar(32) not null,
    source_tables varchar(128) not null,
    source_id   bigint      not null references data_source(id),
    sort        int         not null
);

-- ---------------------------------------------------------------- 图表与报告模板（A5）
create table chart_template (
    id          bigserial primary key,
    name        varchar(16) not null,
    use_case    varchar(32) not null,
    example     varchar(64) not null,
    tier_scope  varchar(32) not null,           -- 适用档位
    used_by     int         not null,
    version     varchar(8)  not null,
    updated     varchar(16) not null,
    sort        int         not null
);

create table report_block (
    id      int primary key,
    name    varchar(32) not null,
    height  int         not null                -- 画布占位高度（px），用于估算页数
);

create table report_preset (
    name      varchar(16) primary key,
    block_ids int[]       not null,
    sort      int         not null
);

create table report_draft (
    id         bigserial primary key,
    preset     varchar(16) not null,
    period     varchar(7)  not null,
    block_ids  int[]       not null,
    flow_step  int         not null default 3,  -- 进入发布工作流第 3 步「分析成稿」
    created_by bigint      not null references app_user(id),
    created_at timestamptz not null default now()
);

-- ---------------------------------------------------------------- 病组专题（A7）
create table topic (
    code        varchar(8)  primary key,
    name        varchar(32) not null,
    period      varchar(16) not null,
    source      varchar(32) not null,
    -- 整体描述 / 费用结构 / 优化空间 / 建议 等展示数据（聚合结果，不含病例级字段）
    overview    jsonb       not null,
    cost_mix    jsonb       not null,
    attribution jsonb       not null,           -- {cityMean, patientDiff, behaviorDiff, r2}
    optimization jsonb      not null,
    suggestions jsonb       not null,
    submitted_at timestamptz,
    submitted_by bigint references app_user(id)
);

create table topic_behavior (
    topic_code  varchar(8)  not null references topic(code),
    name        varchar(32) not null,
    rate_pct    numeric(5,1) not null,
    with_avg    int         not null,           -- 有该行为次均（元）
    without_avg int         not null,
    sort        int         not null,
    primary key (topic_code, name)
);

create table topic_benchmark (
    topic_code varchar(8)  not null references topic(code),
    metric     varchar(32) not null,
    bench      varchar(16) not null,
    deviant    varchar(16) not null,
    gap        varchar(16) not null,
    sort       int         not null,
    primary key (topic_code, metric)
);

create table topic_section (
    topic_code  varchar(8)  not null references topic(code),
    idx         int         not null,           -- 1..7
    name        varchar(16) not null,
    draft       text        not null,           -- 大模型初稿 / 人工修订稿
    draft_version int       not null default 1,
    approved_by bigint references app_user(id),
    approved_at timestamptz,
    primary key (topic_code, idx)
);

-- ---------------------------------------------------------------- 流程设计器（A9）
create table flow_template (
    id         bigserial primary key,
    kind       varchar(16) not null unique,     -- 月告知 / 季公布 / …
    version    varchar(8)  not null,
    updated_by varchar(16) not null,
    updated_on date        not null,
    pending_version varchar(8),                 -- 已保存待召集人确认的新版本
    sort       int         not null
);

create table flow_node (
    template_id bigint      not null references flow_template(id),
    idx         int         not null,
    name        varchar(16) not null,
    handler     varchar(48) not null,
    mode        varchar(4)  not null,           -- 单人 / 会签 / 或签 / —
    days        int         not null,
    escalate_to varchar(16) not null,
    gate        boolean     not null default false,  -- 必经节点（召集人审批）
    primary key (template_id, idx)
);

-- ---------------------------------------------------------------- 意见与申诉（A10）
create table opinion_ticket (
    no          varchar(16) primary key,
    org         varchar(64) not null,
    ref         varchar(64) not null,           -- 关联指标 / 报告段落
    category    varchar(16) not null,           -- 核对期异议 / 数据异议 / 申诉 / 分组规则 / 咨询
    owner       varchar(32) not null,
    due_date    date,
    status      varchar(8)  not null,           -- WAIT / DOING / DONE
    content     text        not null,
    reply       text,
    replied_at  timestamptz,
    rating      int,                            -- 机构评价 1–5
    typical     boolean     not null default false,
    created_at  date        not null
);

-- ---------------------------------------------------------------- 预警提醒（A11）
create table alert_rule (
    id        bigserial primary key,
    name      varchar(32) not null,
    scope     varchar(32) not null,
    condition varchar(64) not null,
    frequency varchar(4)  not null,
    hits      int         not null,
    enabled   boolean     not null,
    sort      int         not null
);

create table alert_trigger (
    id         bigserial primary key,
    trig_date  varchar(5)  not null,            -- MM-DD
    period     varchar(8)  not null,
    org        varchar(64) not null,
    drg_group  varchar(32) not null,
    rule_name  varchar(32) not null,
    value      varchar(32) not null,
    status     varchar(8)  not null,            -- GEN / SENT / RCPT / FIX / CLOSED
    letter_seq int         not null,            -- 示医保提〔2026〕N号
    receipt    text,
    sent_at    timestamptz,
    receipt_at timestamptz,
    sort       int         not null
);

-- ---------------------------------------------------------------- 用户权限（A12）
create table org_unit (
    id        bigserial primary key,
    name      varchar(64) not null,
    level     int         not null,
    is_virtual boolean    not null default false,
    sort      int         not null
);

create table perm_role (
    id      int primary key,
    name    varchar(32) not null,
    matrix  text[]      not null,               -- 6 列：统筹区汇总 / 机构级数据 / 他院数据 / 病例明细 / 导出 / 配置与审批
    dims    jsonb       not null                -- 五维：功能 / 数据范围 / 指标字段 / 粒度 / 操作
);

create table account_lifecycle (
    id     bigserial primary key,
    name   varchar(32) not null,
    org    varchar(64) not null,
    role   varchar(32) not null,
    stage  varchar(16) not null,
    tone   varchar(8)  not null,               -- warning / success / primary / muted
    note   varchar(64) not null,
    sort   int         not null
);

-- 三员分立：授权类操作须第二名管理员复核
create table admin_operation (
    id          bigserial primary key,
    target      varchar(64) not null,
    action      varchar(32) not null,
    requested_by bigint     not null references app_user(id),
    status      varchar(16) not null default 'PENDING_REVIEW',
    created_at  timestamptz not null default now()
);

-- ---------------------------------------------------------------- 展示策略（A13）
create table display_quadrant (
    id          int primary key,
    title       varchar(32) not null,
    summary     varchar(64) not null,
    tone        varchar(8)  not null,
    audience    varchar(64) not null,
    granularity varchar(32) not null,
    naming      varchar(32) not null,
    threshold   varchar(32) not null,
    fixed_none  boolean     not null default false
);

create table benchmark_tier (
    indicator varchar(32) primary key,
    tier      int         not null,             -- 0 匿名分位 / 1 匿名编号 / 2 具名对比与排行
    sort      int         not null
);

create table tier_change_request (
    id         bigserial primary key,
    indicator  varchar(32) not null references benchmark_tier(indicator),
    from_tier  int         not null,
    to_tier    int         not null,
    reason     varchar(256) not null,
    approver   varchar(32) not null,
    status     varchar(16) not null default 'PENDING',
    requested_by bigint    not null references app_user(id),
    created_at timestamptz not null default now()
);
-- 同一指标同时只能有一张待审批的档位切换单
create unique index ux_tier_change_pending on tier_change_request(indicator) where status = 'PENDING';

-- ---------------------------------------------------------------- 审计与导出（A14 / 全局）
create table audit_log (
    id           bigserial primary key,
    at           timestamptz not null default now(),
    user_name    varchar(32) not null,
    org          varchar(64) not null,
    type         varchar(8)  not null,          -- 登录 / 查阅 / 导出 / 打印 / 授权 / 越权尝试
    object       varchar(128) not null,
    ip           varchar(45),
    watermark_no varchar(24),
    result       varchar(16) not null
);
create index ix_audit_log_at on audit_log(at desc);
create index ix_audit_log_wm on audit_log(watermark_no);

create table export_request (
    id           bigserial primary key,
    watermark_no varchar(24) not null unique,
    content      varchar(128) not null,
    purpose      varchar(16) not null,
    validity     varchar(8)  not null,
    times        varchar(4)  not null,
    status       varchar(16) not null default '待审批',
    user_id      bigint      not null references app_user(id),
    created_at   timestamptz not null default now()
);

-- 水印编号日序号：WM-YYYYMMDD-NNNN
create table watermark_counter (
    day      date primary key,
    next_val int  not null
);
