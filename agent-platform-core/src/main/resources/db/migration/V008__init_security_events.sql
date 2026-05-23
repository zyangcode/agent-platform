create table security_events (
    id bigserial primary key,
    trace_id varchar(64),
    tenant_id bigint not null references tenants (id),
    application_id bigint references applications (id),
    user_id bigint references users (id),
    event_type varchar(64) not null,
    location varchar(64) not null,
    source_text_hash varchar(64) not null,
    masked_sample text not null,
    action varchar(32) not null,
    created_at timestamp not null default current_timestamp
);

create index idx_security_events_trace on security_events (trace_id);
create index idx_security_events_tenant_created on security_events (tenant_id, created_at desc);
create index idx_security_events_type_created on security_events (event_type, created_at desc);
