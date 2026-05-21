create table tenants (
    id bigserial primary key,
    name varchar(128) not null,
    code varchar(64) not null,
    status varchar(32) not null,
    created_at timestamp not null default current_timestamp,
    updated_at timestamp not null default current_timestamp,
    constraint uk_tenants_code unique (code)
);

create table users (
    id bigserial primary key,
    tenant_id bigint not null references tenants (id),
    username varchar(64) not null,
    password_hash varchar(255) not null,
    display_name varchar(128) not null,
    email varchar(128),
    status varchar(32) not null,
    created_at timestamp not null default current_timestamp,
    updated_at timestamp not null default current_timestamp,
    constraint uk_users_tenant_username unique (tenant_id, username)
);

create table roles (
    id bigserial primary key,
    code varchar(64) not null,
    name varchar(128) not null,
    description text,
    created_at timestamp not null default current_timestamp,
    constraint uk_roles_code unique (code)
);

create table user_roles (
    id bigserial primary key,
    user_id bigint not null references users (id),
    role_id bigint not null references roles (id),
    created_at timestamp not null default current_timestamp,
    constraint uk_user_roles_user_role unique (user_id, role_id)
);

create table applications (
    id bigserial primary key,
    tenant_id bigint not null references tenants (id),
    owner_user_id bigint not null references users (id),
    name varchar(128) not null,
    description text,
    status varchar(32) not null,
    created_at timestamp not null default current_timestamp,
    updated_at timestamp not null default current_timestamp,
    constraint uk_applications_owner_name unique (tenant_id, owner_user_id, name)
);

create table api_keys (
    id bigserial primary key,
    tenant_id bigint not null references tenants (id),
    application_id bigint not null references applications (id),
    name varchar(128) not null,
    key_prefix varchar(32) not null,
    key_hash varchar(255) not null,
    status varchar(32) not null,
    last_used_at timestamp,
    expires_at timestamp,
    created_at timestamp not null default current_timestamp,
    revoked_at timestamp,
    constraint uk_api_keys_key_prefix unique (key_prefix)
);

create index idx_users_tenant_status on users (tenant_id, status);
create index idx_applications_tenant_owner on applications (tenant_id, owner_user_id);
create index idx_api_keys_application_status on api_keys (application_id, status);
