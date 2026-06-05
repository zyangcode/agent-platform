create table knowledge_documents (
    id bigserial primary key,
    tenant_id bigint not null references tenants (id),
    application_id bigint not null references applications (id),
    profile_id bigint references agent_profiles (id),
    owner_user_id bigint not null references users (id),
    title varchar(255) not null,
    source_type varchar(32) not null,
    source_uri varchar(512),
    doc_hash varchar(128) not null,
    status varchar(32) not null,
    metadata jsonb not null default '{}'::jsonb,
    created_at timestamp not null default current_timestamp,
    updated_at timestamp not null default current_timestamp
);

create table knowledge_chunks (
    id bigserial primary key,
    tenant_id bigint not null references tenants (id),
    application_id bigint not null references applications (id),
    document_id bigint not null references knowledge_documents (id),
    chunk_index int not null,
    content text not null,
    content_hash varchar(128) not null,
    token_count int not null,
    vector_id varchar(128),
    status varchar(32) not null,
    metadata jsonb not null default '{}'::jsonb,
    created_at timestamp not null default current_timestamp,
    updated_at timestamp not null default current_timestamp
);

create unique index uk_knowledge_documents_scope_hash
    on knowledge_documents (tenant_id, application_id, owner_user_id, coalesce(profile_id, 0), doc_hash)
    where status = 'INDEXED';

create index idx_knowledge_documents_scope_status
    on knowledge_documents (tenant_id, application_id, owner_user_id, profile_id, status);

create index idx_knowledge_chunks_document_status
    on knowledge_chunks (tenant_id, application_id, document_id, status);

create index idx_knowledge_chunks_vector
    on knowledge_chunks (vector_id)
    where vector_id is not null;
