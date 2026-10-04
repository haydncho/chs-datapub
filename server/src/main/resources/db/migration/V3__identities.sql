-- 第一批 / 第三批共用：机构门户、县区、省级、外部监督身份；召集人首页改为 A2 全息图
-- 迁移版本号分配：V10 全息图(A2/B1)、V11 指标与推荐(A4/A6)、V12 发布工作流(A8)、V13 门户分析(B2/B3/B7)、
--                 V14 门户报告(B4/B5/B6/D1)、V15 县区省级监督(C1/C2/C3)；同组内续用 V10x 小版本（如 V10_1）

insert into app_user (id, username, name, phone_masked) values
 (7,  'limin',       '李敏',   '138****5521'),
 (8,  'qianli',      '钱丽',   '139****3307'),
 (9,  'wanglei',     '王磊',   '137****8810'),
 (10, 'liudaibiao',  '刘代表', '136****4492');
select setval('app_user_id_seq', 10);

insert into user_identity (user_id, role, role_label, org, org_detail, scope, home, home_label, sort) values
 (7,  'INSTITUTION', '医保办主任',              '示例市第一人民医院', '示例市第一人民医院 · 医保办', '本院具名 · 同级匿名分位',        'B1', '首页:本院全息图', 1),
 (8,  'COUNTY',      '县区医保部门',            '甲县医保局',         '甲县医保局',                  '甲县机构具名 · 其他县区汇总',    'C1', '首页:县区视图',   1),
 (9,  'PROVINCE',    '基金监管处',              '省医保局',           '省医保局 · 基金监管处',       '各统筹区汇总层 · 无机构级数据',  'C2', '首页:省级汇总',   1),
 (10, 'SUPERVISOR',  '外部监督 · 市人大代表',   '限时只读席位',       '示例市人大常委会',            '发布会材料 · 至 10-05 18:00',    'C3', '首页:只读席位',   1);

update user_identity set home = 'A2', home_label = '首页:A2 全息图' where role = 'CONVENER';
update user_identity set home = 'A2', home_label = '首页:A2 全息图' where role = 'ADMIN_GROUP';
