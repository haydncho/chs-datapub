-- B6 政策培训: per-user learning state. A course is completed only after its 测验 is passed
-- (graded on the server against the B6 page payload); nothing is pre-completed.
create table training_progress (
    user_key     varchar(64) not null,            -- app_user.login (or display name for the dev fallback)
    org_id       varchar(32),                     -- organisation of the identity that studied (本院 进度统计)
    course_id    varchar(16) not null,
    started_at   timestamptz not null default now(),
    attempts     int         not null default 0,
    last_score   int,
    best_score   int,
    completed_at timestamptz,
    primary key (user_key, course_id)
);
create index training_progress_org_idx on training_progress(org_id) where completed_at is not null;
