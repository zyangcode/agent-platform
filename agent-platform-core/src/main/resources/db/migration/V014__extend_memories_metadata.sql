alter table memories
    add column memory_category varchar(32),
    add column tags text[] not null default '{}',
    add column importance numeric(4,3) not null default 0.5,
    add column slot_hint varchar(64),
    add column metadata jsonb not null default '{}'::jsonb;

update memories
set memory_category = lower(memory_type)
where memory_category is null;

alter table memories
    alter column memory_category set not null;

create index idx_memories_category_importance on memories (tenant_id, user_id, memory_category, importance desc);
create index idx_memories_tags_gin on memories using gin (tags);
