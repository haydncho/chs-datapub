-- 数据归集(A3)、指标配置(A4)、模板(A5)、智能推荐(A6)、病种专题工作台(A7)的操作状态。
-- 按 页面 + 键 存一份 JSON(例如 A3 / source:异地就医、A7 / review:T-BR25),由各页面的
-- PageOverlay 合并进页面载荷,刷新后保持。
create table page_state (
    page        varchar(16)  not null,
    key         varchar(96)  not null,
    value       jsonb        not null,
    updated_by  varchar(32)  not null,
    updated_at  timestamptz  not null default now(),
    primary key (page, key)
);

-- A7 中 BR25 专题仍处于「七段成稿」(3/7 审定),与 A8 发布流程第 3 步「分析成稿」对齐;
-- A7 提交核对与审核后再推进到第 4 步「专家组审核」。
update publish_task set step = 3 where id = 'br25' and step = 4;
