-- 用户与权限 (A12): 新增用户申请(双人复核)。账号停用/启用沿用 V1 的 app_user.enabled。
create table user_request (
    id           bigserial primary key,
    login        varchar(64)  not null,
    name         varchar(32)  not null,
    role_code    varchar(32)  not null references app_role(code),
    org_id       varchar(32)  references org(id),
    requested_by varchar(32)  not null,
    requested_at timestamptz  not null default now(),
    status       varchar(8)   not null default 'pending' check (status in ('pending', 'approved', 'rejected')),
    reviewed_by  varchar(32),
    reviewed_at  timestamptz
);

-- 同一登录名同时只能有一条待复核申请
create unique index user_request_pending_uq on user_request (login) where status = 'pending';
