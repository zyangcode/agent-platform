alter table memories
    add column if not exists last_accessed_at timestamp,
    add column if not exists access_count int not null default 0,
    add column if not exists confidence numeric(4, 3) not null default 0.8,
    add column if not exists expires_at timestamp;

update memories
set last_accessed_at = updated_at
where last_accessed_at is null;

create index if not exists idx_memories_last_accessed
    on memories (tenant_id, user_id, last_accessed_at desc);

create index if not exists idx_memories_expires_at
    on memories (tenant_id, user_id, expires_at);
