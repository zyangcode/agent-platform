create table alert_events (
    id bigserial primary key,
    trace_id varchar(64),
    tenant_id bigint not null references tenants (id),
    application_id bigint references applications (id),
    alert_type varchar(64) not null,
    level varchar(32) not null,
    title varchar(255) not null,
    content text not null,
    suggestion text,
    notify_status varchar(32) not null default 'PENDING',
    retry_count int not null default 0,
    created_at timestamp not null default current_timestamp,
    sent_at timestamp
);

create index idx_alert_events_trace on alert_events (trace_id);
create index idx_alert_events_tenant_notify_created on alert_events (tenant_id, notify_status, created_at desc);
create index idx_alert_events_type_created on alert_events (alert_type, created_at desc);
