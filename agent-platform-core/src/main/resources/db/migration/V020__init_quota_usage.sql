create table quota_usage (
    id bigserial primary key,
    tenant_id bigint not null references tenants (id),
    subject_type varchar(32) not null,
    subject_id bigint not null,
    period_type varchar(16) not null,
    period_key varchar(16) not null,
    reserved_tokens bigint not null default 0,
    status varchar(32) not null default 'ACTIVE',
    version int not null default 0,
    created_at timestamp not null default current_timestamp,
    updated_at timestamp not null default current_timestamp,
    unique (tenant_id, subject_type, subject_id, period_type, period_key)
);

create index idx_quota_usage_subject_period on quota_usage (
    tenant_id, subject_type, subject_id, period_type, period_key
);

create index idx_quota_usage_status on quota_usage (status);
