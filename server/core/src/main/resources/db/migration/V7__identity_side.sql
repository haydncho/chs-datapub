-- 登录页「选择端」: 每个身份属于医保局端(bureau)或机构端(org)。
--   bureau: 召集人 / 行政管理组 / 委托分析团队 / 安全审计员
--   org:    医保办主任(hospital) / 县区医保(county) / 外部监督(observer)
-- 同一用户可同时持有两端的身份(如 chenzy);登录时按所选端过滤身份。
alter table user_identity add column side varchar(8);

update user_identity set side = case role_code
    when 'convener' then 'bureau'
    when 'admin'    then 'bureau'
    when 'analyst'  then 'bureau'
    when 'auditor'  then 'bureau'
    else 'org'
end;

-- 之后新增的身份若未写 side,按角色补齐(供后续迁移 / 用户管理使用)
create function user_identity_default_side() returns trigger as $$
begin
    if new.side is null then
        new.side := case when new.role_code in ('convener', 'admin', 'analyst', 'auditor') then 'bureau' else 'org' end;
    end if;
    return new;
end;
$$ language plpgsql;

create trigger user_identity_side_default before insert on user_identity
    for each row execute function user_identity_default_side();

alter table user_identity alter column side set not null;
alter table user_identity add constraint user_identity_side_chk check (side in ('bureau', 'org'));
