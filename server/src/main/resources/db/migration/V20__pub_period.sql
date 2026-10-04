-- 发布门禁:外部读取(机构 / 县区 / 省级)以「数据期次是否处于已发布状态」为前提;期次被撤回即时失效
create table pub_period (
    period     varchar(7)  primary key,                 -- yyyy-MM
    status     varchar(12) not null default 'PUBLISHED', -- PUBLISHED / WITHDRAWN
    note       varchar(64) not null default '',
    updated_at timestamptz not null default now()
);
insert into pub_period (period, status, note) values ('2026-08', 'PUBLISHED', '示例数据按期发布');
