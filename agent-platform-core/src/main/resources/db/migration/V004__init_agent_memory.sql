create table conversations (
    id bigserial primary key,
    tenant_id bigint not null references tenants (id),
    application_id bigint not null references applications (id),
    user_id bigint not null references users (id),
    profile_id bigint not null references agent_profiles (id),
    title varchar(255) not null,
    channel varchar(32) not null,
    status varchar(32) not null,
    metadata jsonb not null default '{}'::jsonb,
    created_at timestamp not null default current_timestamp,
    updated_at timestamp not null default current_timestamp
);

create table conversation_messages (
    id bigserial primary key,
    conversation_id bigint not null references conversations (id),
    trace_id varchar(64),
    role varchar(32) not null,
    content text not null,
    token_count int,
    tool_call_id varchar(128),
    metadata jsonb not null default '{}'::jsonb,
    created_at timestamp not null default current_timestamp
);

create table memories (
    id bigserial primary key,
    tenant_id bigint not null references tenants (id),
    user_id bigint not null references users (id),
    application_id bigint references applications (id),
    profile_id bigint references agent_profiles (id),
    memory_type varchar(32) not null,
    content text not null,
    keywords text[],
    source_conversation_id bigint references conversations (id),
    status varchar(32) not null,
    created_at timestamp not null default current_timestamp,
    updated_at timestamp not null default current_timestamp
);

create index idx_conversations_tenant_user_updated on conversations (tenant_id, user_id, updated_at);
create index idx_conversations_application_updated on conversations (application_id, updated_at);
create index idx_conversation_messages_conversation_created on conversation_messages (conversation_id, created_at);
create index idx_conversation_messages_trace on conversation_messages (trace_id);
create index idx_memories_tenant_user_status on memories (tenant_id, user_id, status);
create index idx_memories_application_profile on memories (application_id, profile_id);
