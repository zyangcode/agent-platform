alter table memories
    add column if not exists memory_scope varchar(32) not null default 'PROFILE_LONG_TERM';

update memories
set memory_scope = 'PROFILE_LONG_TERM'
where memory_scope is null;

create index if not exists idx_memories_scope_conversation
    on memories (tenant_id, user_id, memory_scope, source_conversation_id);
