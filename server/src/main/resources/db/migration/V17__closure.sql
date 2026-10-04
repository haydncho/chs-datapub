-- 流程闭环(合并后补充):A4 指标上线审批 → A8;A8 定向发布 → B4 签收;A6 提醒函 → A11。
-- 跨组关联列与审批结论表(只增不改,不影响各组原有表的既有数据)。

-- 1. 指标上线审批结论(审批单在 ind_draft,A8 召集人批准 / 驳回);驳回后草稿解锁,A4 显示最近一次驳回意见
create table pub_indicator_decision (
    id          bigserial primary key,
    draft_id    bigint       not null references ind_draft(id),
    approval_no varchar(16)  not null,
    decision    varchar(12)  not null,            -- APPROVED / REJECTED
    opinion     varchar(256),
    decided_by  bigint       not null references app_user(id),
    decided_at  timestamptz  not null default now()
);
create index ix_pub_ind_decision_draft on pub_indicator_decision(draft_id, id);

-- 2. 定向发布生成的机构报告关联到发布版本:B4 签收回写 pub_release.signed
alter table pr_report add column release_id bigint references pub_release(id);
create index ix_pr_report_release on pr_report(release_id);

-- 3. A6 异常推荐生成提醒函 → A11 触发记录
alter table rec_anomaly add column alert_trigger_id bigint references alert_trigger(id);
