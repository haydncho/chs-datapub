-- 召集人 / 行政管理组的首页改为「工作台」(今日待办 + 月度发布主线)
update user_identity set home = 'W0', home_label = '首页:工作台' where role in ('CONVENER', 'ADMIN_GROUP');
