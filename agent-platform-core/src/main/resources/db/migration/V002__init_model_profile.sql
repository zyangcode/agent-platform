create table model_providers (
    id bigserial primary key,
    name varchar(128) not null,
    provider_type varchar(32) not null,
    base_url varchar(512) not null,
    api_key_encrypted text,
    status varchar(32) not null,
    created_at timestamp not null default current_timestamp,
    updated_at timestamp not null default current_timestamp
);

create table model_configs (
    id bigserial primary key,
    provider_id bigint not null references model_providers (id),
    model_name varchar(128) not null,
    display_name varchar(128) not null,
    capabilities jsonb not null default '{}'::jsonb,
    default_temperature numeric(4, 2) not null default 0.70,
    max_context_tokens int not null,
    status varchar(32) not null,
    created_at timestamp not null default current_timestamp,
    updated_at timestamp not null default current_timestamp,
    constraint uk_model_configs_provider_model unique (provider_id, model_name)
);

create table agent_profiles (
    id bigserial primary key,
    tenant_id bigint not null references tenants (id),
    owner_user_id bigint references users (id),
    application_id bigint references applications (id),
    name varchar(128) not null,
    profile_type varchar(32) not null,
    description text,
    model_config_id bigint not null references model_configs (id),
    prompt_extra text,
    memory_strategy jsonb not null default '{}'::jsonb,
    max_steps int not null default 6,
    security_policy_id bigint,
    execution_mode varchar(32) not null default 'BASIC',
    visibility varchar(32) not null,
    status varchar(32) not null,
    version int not null default 0,
    created_at timestamp not null default current_timestamp,
    updated_at timestamp not null default current_timestamp
);

create index idx_model_configs_status on model_configs (status);
create index idx_agent_profiles_tenant_type_status on agent_profiles (tenant_id, profile_type, status);
create index idx_agent_profiles_owner on agent_profiles (tenant_id, owner_user_id);
