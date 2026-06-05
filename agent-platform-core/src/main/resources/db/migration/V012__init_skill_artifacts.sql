create table skill_artifacts (
    id bigserial primary key,
    skill_version_id bigint not null references skill_versions (id),
    artifact_type varchar(32) not null,
    storage_path varchar(512) not null,
    file_name varchar(255) not null,
    size_bytes bigint not null,
    checksum varchar(128) not null,
    created_at timestamp not null default current_timestamp
);

create index idx_skill_artifacts_version on skill_artifacts (skill_version_id);
