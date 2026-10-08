-- 全景图 (cockpit):订阅推送设置与告警确认处置真正落库(此前 saveSubscription / ackAlarm 只记审计)。

-- 订阅推送:每个登录用户在每个视角(conv 市医保局 / hosp 本院)各一份,刷新与重新登录后回显。
create table cockpit_subscription (
    owner       varchar(64)  not null,               -- 登录名(app_user.login);开发回退身份为 dev:<姓名>
    identity    varchar(8)   not null check (identity in ('conv', 'hosp')),
    enabled     boolean      not null default true,
    frequency   varchar(32)  not null,
    channel     varchar(32)  not null,
    contents    jsonb        not null check (jsonb_typeof(contents) = 'array' and jsonb_array_length(contents) > 0),
    recipients  jsonb        not null check (jsonb_typeof(recipients) = 'array' and jsonb_array_length(recipients) > 0),
    updated_by  varchar(32)  not null,
    updated_at  timestamptz  not null default now(),
    primary key (owner, identity)
);

-- 告警确认处置:同一范围内确认一次即视为已处置,跨会话 / 跨终端不再弹出。
-- scope:bureau(市医保局)、org:<机构编码>(医院本院)、county:<机构编码>(县区医保)。
create table cockpit_alarm_ack (
    scope       varchar(48)  not null,
    alarm_key   text         not null,               -- "<类型>|<内容>",与页面提醒一一对应
    acked_by    varchar(32)  not null,
    acked_at    timestamptz  not null default now(),
    primary key (scope, alarm_key)
);
