-- 导出闭环:审批人、有效期、剩余下载次数;申请人角色决定由谁审批(召集人的申请由行政管理组审批)
alter table export_request
    add column requester_role varchar(24) not null default '',
    add column requester_org  varchar(64) not null default '',
    add column decided_by     varchar(32),
    add column decided_at     timestamptz,
    add column opinion        varchar(200),
    add column expires_at     timestamptz,
    add column remaining      int not null default 0;
