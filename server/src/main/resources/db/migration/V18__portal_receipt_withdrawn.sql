-- 补充:机构门户提交预警回执、撤回的发布报告在 B4 显示「已撤回」
-- pr_report.status 增加 WITHDRAWN(原 varchar(8) 放不下)
alter table pr_report alter column status type varchar(10);
