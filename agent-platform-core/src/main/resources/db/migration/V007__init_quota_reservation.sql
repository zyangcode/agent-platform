create table quota_configs (
    id bigserial primary key,
    tenant_id bigint not null references tenants (id),
    subject_type varchar(32) not null,
    subject_id bigint not null,
    daily_limit_tokens bigint not null default 0,
    monthly_limit_tokens bigint not null default 0,
    single_request_limit_tokens bigint not null default 0,
    status varchar(32) not null,
    version int not null default 0,
    created_at timestamp not null default current_timestamp,
    updated_at timestamp not null default current_timestamp,
    unique (tenant_id, subject_type, subject_id)
);

create table quota_reservations (
    id bigserial primary key,
    trace_id varchar(64) not null unique,
    tenant_id bigint not null references tenants (id),
    application_id bigint references applications (id),
    user_id bigint references users (id),
    estimated_tokens bigint not null,
    actual_tokens bigint,
    status varchar(32) not null,
    version int not null default 0,
    created_at timestamp not null default current_timestamp,
    updated_at timestamp not null default current_timestamp
);

create index idx_quota_configs_subject on quota_configs (tenant_id, subject_type, subject_id);
create index idx_quota_configs_status on quota_configs (status);

create index idx_quota_reservations_trace on quota_reservations (trace_id);
create index idx_quota_reservations_status on quota_reservations (status);
create index idx_quota_reservations_tenant_created on quota_reservations (tenant_id, created_at desc);
