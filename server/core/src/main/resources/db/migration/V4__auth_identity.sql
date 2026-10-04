-- 统一身份认证 (A1): identity grants, credentials, SMS codes, login attempts, sessions.
--
-- Sessions are HMAC-SHA256 signed bearer tokens (see cn.ybdata.core.auth.TokenService);
-- auth_session keeps one row per issued token so logout / identity switch can revoke it.

alter table app_user add column phone varchar(20);

update app_user set phone = '13800000001' where login = 'chenzy';
update app_user set phone = '13800000002' where login = 'lihua';
update app_user set phone = '13800000003' where login = 'wangq';
update app_user set phone = '13800000004' where login = 'zhangy';
update app_user set phone = '13800000005' where login = 'limin';
update app_user set phone = '13800000006' where login = 'zhaoan';
update app_user set phone = '13800000007' where login = 'zhoumin';

-- ── identities a user may act as (A1 身份选择) ──────────────────────────────
-- One person can hold several identities (e.g. 召集人 who also views the
-- 定点机构 side). role_code + org_id decide page access and data scope.
create table user_identity (
    id          bigserial primary key,
    user_id     bigint       not null references app_user(id),
    role_code   varchar(32)  not null references app_role(code),
    org_id      varchar(32)  references org(id),
    label       varchar(64)  not null,           -- 市医保局 · 召集人
    description varchar(128) not null,           -- card subtitle
    zone        varchar(32)  not null,           -- 分析监测区 / 发布区 / 公开层
    tone        varchar(8)   not null default 'brand',   -- brand / ok
    initial     varchar(2)   not null,           -- card avatar character
    target      varchar(16)  not null,           -- landing page code
    who         varchar(8),                       -- cockpit ?who= when target = cockpit
    scope       varchar(64)  not null,           -- header scope line
    sort_order  int          not null default 0,
    unique (user_id, role_code, org_id)
);

insert into user_identity (user_id, role_code, org_id, label, description, zone, tone, initial, target, who, scope, sort_order)
select u.id, x.role_code, x.org_id, x.label, x.description, x.zone, x.tone, x.initial, x.target, x.who, x.scope, x.sort_order
from (values
  ('chenzy', 'convener', 'YBJ', '市医保局 · 召集人', '全域数据 · 审批发布 · 可下钻至诊疗行为', '分析监测区', 'brand', '市', 'cockpit', 'conv', '示例市全域', 0),
  ('chenzy', 'admin', 'YBJ', '市医保局 · 行政管理组', '五环节作业 · 归集、配置、发布、答复', '分析监测区', 'brand', '管', 'A3', null, '示例市全域', 1),
  ('chenzy', 'hospital', 'H001', '定点医药机构 · 示例市第一人民医院', '本院具名 · 同级匿名分位 · 签收与核对', '发布区', 'ok', '院', 'cockpit', 'hosp', '示例市第一人民医院 · 本院具名', 2),
  ('lihua', 'admin', 'YBJ', '市医保局 · 行政管理组', '五环节作业 · 归集、配置、发布、答复', '分析监测区', 'brand', '管', 'A3', null, '示例市全域', 0),
  ('wangq', 'admin', 'YBJ', '市医保局 · 行政管理组', '意见与申诉承办 · 全部机构', '分析监测区', 'brand', '管', 'A10', null, '意见与申诉 · 全部机构', 0),
  ('zhangy', 'analyst', 'YBJ', '市医保局 · 委托分析团队', '受控分析环境 · 仅导出审核后聚合结果', '分析监测区 · 受控环境', 'brand', '析', 'A7', null, '受控分析环境', 0),
  ('limin', 'hospital', 'H001', '定点医药机构 · 示例市第一人民医院', '本院具名 · 同级匿名分位 · 签收与核对', '发布区', 'ok', '院', 'B1', null, '示例市第一人民医院 · 本院具名', 0),
  ('zhaoan', 'auditor', 'YBJ', '市医保局 · 安全审计员', '全域只读审计 · 不可修改业务数据', '分析监测区', 'brand', '审', 'A14', null, '全域 · 只读审计', 0),
  ('zhoumin', 'observer', 'PUB', '社会监督员 · 公开汇总层', '仅公开层汇总数据 · 可提交监督建议', '公开层', 'ok', '监', 'C3', null, '公开汇总层', 0)
) as x(login, role_code, org_id, label, description, zone, tone, initial, target, who, scope, sort_order)
join app_user u on u.login = x.login;

-- ── credentials: certificate PIN (PBKDF2-HMAC-SHA256, demo PIN 123456) ────
create table user_credential (
    user_id     bigint      not null references app_user(id),
    kind        varchar(8)  not null,            -- pin
    secret_hash varchar(128) not null,           -- pbkdf2_sha256$iterations$salt_b64$hash_b64
    updated_at  timestamptz not null default now(),
    primary key (user_id, kind)
);

insert into user_credential (user_id, kind, secret_hash)
select u.id, 'pin', x.h
from (values
  ('chenzy', 'pbkdf2_sha256$120000$Jj7tPRx4+GHnZXw9rUJK9w==$6BkP+6umneAjFWJpq/ue4XAwFFKFCdFohHxKaTuvqRU='),
  ('lihua', 'pbkdf2_sha256$120000$xBtY7QGaEaXIOcrlvOFLRg==$PrwuDtyXeTG3fysaLM9mt0Ibnn5rRsv55M/FDmvvLdg='),
  ('wangq', 'pbkdf2_sha256$120000$Wh5Hke4qp9czS0ZTWT8yOQ==$yH05wCxmMQg2r2nLJbYkZQrmQjYeBo0PGCWKRgFP0DU='),
  ('zhangy', 'pbkdf2_sha256$120000$gVek1d8egfQMnmNzykqrAg==$ntkf3ah+VVBgn9mT6+M+viYY5GiAZ9qZGXdUXos1cu0='),
  ('limin', 'pbkdf2_sha256$120000$+8eIZfrt9Soi3JCUh0VjJA==$E8un2RV1cYEqNGBingwfc0gPCRJhW6nyZ9dt75gAJ0I='),
  ('zhaoan', 'pbkdf2_sha256$120000$BIwafcNuzPya4ACuCYzmMA==$hO9rescXk3W+p/ACWcFTbYTomAbWsWPBL3L81uKjH34='),
  ('zhoumin', 'pbkdf2_sha256$120000$EUKFyXZFKxdYtxUUT6bAjA==$cVWe3zT6zta6awNaj3lm+VSM5G29WNM3w5Sb96SJ3Ag=')
) as x(login, h)
join app_user u on u.login = x.login;

-- ── SMS one-time codes (one live code per account, 60s resend cool-down) ──
create table auth_sms_code (
    account     varchar(64) primary key,
    code_hash   char(64)    not null,            -- sha256(account | code)
    sent_at     timestamptz not null default now(),
    expires_at  timestamptz not null,
    attempts    int         not null default 0
);

-- ── login attempts (lock-out after repeated failures) ─────────────────────
create table auth_attempt (
    id          bigserial primary key,
    account     varchar(64) not null,
    method      varchar(8)  not null,            -- cert / sms
    success     boolean     not null,
    reason      varchar(32),
    remote      varchar(64),
    at          timestamptz not null default now()
);
create index auth_attempt_account_idx on auth_attempt(account, at desc);

-- ── issued sessions (bearer tokens); revoked on logout / identity switch ──
create table auth_session (
    id          varchar(36) primary key,         -- token sid (UUID)
    user_id     bigint      not null references app_user(id),
    identity_id bigint      not null references user_identity(id),
    method      varchar(8)  not null,
    issued_at   timestamptz not null default now(),
    expires_at  timestamptz not null,
    revoked_at  timestamptz,
    remote      varchar(64)
);
create index auth_session_user_idx on auth_session(user_id, issued_at desc);
