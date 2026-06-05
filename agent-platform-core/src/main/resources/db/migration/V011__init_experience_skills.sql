create table experience_skills (
    id bigserial primary key,
    tenant_id bigint not null references tenants (id),
    application_id bigint references applications (id),
    user_id bigint references users (id),
    profile_id bigint references agent_profiles (id),
    code varchar(128) not null,
    name varchar(128) not null,
    domain varchar(64),
    trigger_keywords text[],
    content text not null,
    status varchar(32) not null,
    created_at timestamp not null default current_timestamp,
    updated_at timestamp not null default current_timestamp
);

create unique index uk_experience_skills_tenant_code on experience_skills (tenant_id, code);
create index idx_experience_skills_scope_status on experience_skills (tenant_id, application_id, user_id, profile_id, status);
create index idx_experience_skills_domain_status on experience_skills (tenant_id, domain, status);
