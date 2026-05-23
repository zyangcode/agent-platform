create table trace_roots (
    id bigserial primary key,
    trace_id varchar(64) not null unique,
    tenant_id bigint not null references tenants (id),
    application_id bigint references applications (id),
    user_id bigint references users (id),
    profile_id bigint references agent_profiles (id),
    conversation_id bigint references conversations (id),
    client_request_id varchar(128),
    entrypoint varchar(32) not null,
    agent_mode varchar(32),
    status varchar(32) not null,
    error_code varchar(64),
    error_message text,
    started_at timestamp not null,
    ended_at timestamp,
    latency_ms bigint,
    metadata jsonb not null default '{}'::jsonb,
    created_at timestamp not null default current_timestamp,
    updated_at timestamp not null default current_timestamp
);

create table trace_spans (
    id bigserial primary key,
    trace_id varchar(64) not null references trace_roots (trace_id),
    parent_span_id bigint references trace_spans (id),
    span_name varchar(128) not null,
    span_type varchar(64) not null,
    component varchar(64) not null,
    status varchar(32) not null,
    started_at timestamp not null,
    ended_at timestamp,
    latency_ms bigint,
    error_code varchar(64),
    error_message text,
    attributes jsonb not null default '{}'::jsonb,
    created_at timestamp not null default current_timestamp
);

create table token_usage_logs (
    id bigserial primary key,
    trace_id varchar(64) not null references trace_roots (trace_id),
    span_id bigint references trace_spans (id),
    tenant_id bigint not null references tenants (id),
    application_id bigint references applications (id),
    user_id bigint references users (id),
    profile_id bigint references agent_profiles (id),
    model_config_id bigint not null references model_configs (id),
    provider_id bigint not null references model_providers (id),
    model_name varchar(128) not null,
    provider_type varchar(32) not null,
    prompt_tokens int not null default 0,
    completion_tokens int not null default 0,
    total_tokens int not null default 0,
    estimated boolean not null default false,
    created_at timestamp not null default current_timestamp
);

create index idx_trace_roots_tenant_started on trace_roots (tenant_id, started_at desc);
create index idx_trace_roots_application_started on trace_roots (application_id, started_at desc);
create index idx_trace_roots_profile_started on trace_roots (profile_id, started_at desc);
create index idx_trace_roots_status_started on trace_roots (status, started_at desc);

create index idx_trace_spans_trace_started on trace_spans (trace_id, started_at);
create index idx_trace_spans_type_started on trace_spans (span_type, started_at desc);

create index idx_token_usage_trace on token_usage_logs (trace_id);
create index idx_token_usage_application_created on token_usage_logs (application_id, created_at desc);
create index idx_token_usage_model_created on token_usage_logs (model_config_id, created_at desc);
create index idx_token_usage_provider_created on token_usage_logs (provider_id, created_at desc);
