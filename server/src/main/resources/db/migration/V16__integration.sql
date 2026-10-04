-- 合并整理（第一批 / 第三批并行开发后的跨组一致性）

-- 1. C3 外部监督席位：设计稿写死 2026-10-05 18:00，部署后即过期。改为部署时起 7 天（到期日 18:00），身份卡同步。
update rg_seat set valid_from = now() - interval '1 day',
                   valid_until = ((current_date + 7)::timestamp + time '18:00') at time zone 'Asia/Shanghai'
where user_id = (select id from app_user where username = 'liudaibiao');
update user_identity
set scope = '发布会材料 · 至 ' || to_char(current_date + 7, 'MM-DD') || ' 18:00'
where role = 'SUPERVISOR';

-- 2. D1 / A11 预警口径统一：本院「医保外费用占比」预警按同级分位关注线触发（6.8%,P72 > P70），
--    与 A11 已有的「> 8% 且环比上升」规则不是同一口径，补一条分位规则并让触发记录指向它。
insert into alert_rule (name, scope, condition, frequency, hits, enabled, sort)
values ('医保外费用占比 · 同级分位', '三级机构', '> 同级 P70 关注线', '月', 1, true, 6);
update alert_trigger set rule_name = '医保外费用占比 · 同级分位'
where rule_name = '医保外费用占比' and org = '示例市第一人民医院';

-- 3. B5 机构核对截止时间同理：改为部署时起 1 天（次日 18:00），保证演示环境核对期有效。
update pr_check_round set deadline = ((current_date + 1)::timestamp + time '18:00') at time zone 'Asia/Shanghai';
