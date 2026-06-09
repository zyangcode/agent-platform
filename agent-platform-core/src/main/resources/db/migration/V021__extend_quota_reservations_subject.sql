alter table quota_reservations
    add column quota_subject_type varchar(32),
    add column quota_subject_id bigint,
    add column quota_day_period_key varchar(16),
    add column quota_month_period_key varchar(16);

create index idx_quota_reservations_quota_subject on quota_reservations (
    tenant_id, quota_subject_type, quota_subject_id, quota_day_period_key, quota_month_period_key
);
