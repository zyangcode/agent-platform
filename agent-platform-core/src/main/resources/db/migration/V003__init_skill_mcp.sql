create table skills (
    id bigserial primary key,
    tenant_id bigint not null references tenants (id),
    owner_user_id bigint references users (id),
    name varchar(128) not null,
    code varchar(128) not null,
    description text,
    skill_type varchar(32) not null,
    scope varchar(32) not null,
    permission_declaration jsonb not null default '{}'::jsonb,
    status varchar(32) not null,
    current_version_id bigint,
    created_at timestamp not null default current_timestamp,
    updated_at timestamp not null default current_timestamp,
    constraint uk_skills_tenant_code unique (tenant_id, code)
);

create table skill_versions (
    id bigserial primary key,
    skill_id bigint not null references skills (id),
    version varchar(64) not null,
    parameter_schema jsonb not null default '{}'::jsonb,
    return_schema jsonb not null default '{}'::jsonb,
    runtime_config jsonb not null default '{}'::jsonb,
    dependencies jsonb not null default '[]'::jsonb,
    checksum varchar(128),
    validation_result jsonb,
    status varchar(32) not null,
    created_at timestamp not null default current_timestamp,
    constraint uk_skill_versions_skill_version unique (skill_id, version)
);

alter table skills
    add constraint fk_skills_current_version
        foreign key (current_version_id) references skill_versions (id);

create table profile_skills (
    id bigserial primary key,
    profile_id bigint not null references agent_profiles (id),
    skill_id bigint not null references skills (id),
    enabled_by_default boolean not null default true,
    required boolean not null default false,
    config_override jsonb,
    created_at timestamp not null default current_timestamp,
    constraint uk_profile_skills_profile_skill unique (profile_id, skill_id)
);

create table mcp_servers (
    id bigserial primary key,
    tenant_id bigint not null references tenants (id),
    name varchar(128) not null,
    server_type varchar(32) not null,
    connection_config jsonb not null default '{}'::jsonb,
    status varchar(32) not null,
    last_discovered_at timestamp,
    created_at timestamp not null default current_timestamp,
    updated_at timestamp not null default current_timestamp
);

create table mcp_tools (
    id bigserial primary key,
    mcp_server_id bigint not null references mcp_servers (id),
    name varchar(128) not null,
    description text,
    parameter_schema jsonb not null default '{}'::jsonb,
    permission_declaration jsonb not null default '{}'::jsonb,
    status varchar(32) not null,
    created_at timestamp not null default current_timestamp,
    updated_at timestamp not null default current_timestamp,
    constraint uk_mcp_tools_server_name unique (mcp_server_id, name)
);

create table profile_mcp_tools (
    id bigserial primary key,
    profile_id bigint not null references agent_profiles (id),
    mcp_tool_id bigint not null references mcp_tools (id),
    enabled_by_default boolean not null default true,
    config_override jsonb,
    created_at timestamp not null default current_timestamp,
    constraint uk_profile_mcp_tools_profile_tool unique (profile_id, mcp_tool_id)
);

create index idx_skills_tenant_scope_status on skills (tenant_id, scope, status);
create index idx_skill_versions_skill_status on skill_versions (skill_id, status);
create index idx_mcp_servers_tenant_status on mcp_servers (tenant_id, status);
create index idx_mcp_tools_server_status on mcp_tools (mcp_server_id, status);
