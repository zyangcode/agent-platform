alter table memories
    add column if not exists search_vector tsvector;

alter table knowledge_chunks
    add column if not exists search_vector tsvector;

create or replace function update_memories_search_vector()
returns trigger as $$
begin
    new.search_vector :=
        setweight(to_tsvector('simple', coalesce(new.memory_category, '')), 'A') ||
        setweight(to_tsvector('simple', coalesce(array_to_string(new.tags, ' '), '')), 'A') ||
        setweight(to_tsvector('simple', coalesce(new.content, '')), 'B');
    return new;
end;
$$ language plpgsql;

create or replace function update_knowledge_chunks_search_vector()
returns trigger as $$
begin
    new.search_vector :=
        setweight(to_tsvector('simple', coalesce(new.content, '')), 'A') ||
        setweight(to_tsvector('simple', coalesce(new.metadata ->> 'documentTitle', '')), 'B') ||
        setweight(to_tsvector('simple', coalesce(new.metadata ->> 'headingPath', '')), 'B');
    return new;
end;
$$ language plpgsql;

drop trigger if exists trg_memories_search_vector on memories;
create trigger trg_memories_search_vector
before insert or update of content, memory_category, tags
on memories
for each row
execute function update_memories_search_vector();

drop trigger if exists trg_knowledge_chunks_search_vector on knowledge_chunks;
create trigger trg_knowledge_chunks_search_vector
before insert or update of content, metadata
on knowledge_chunks
for each row
execute function update_knowledge_chunks_search_vector();

update memories
set search_vector =
    setweight(to_tsvector('simple', coalesce(memory_category, '')), 'A') ||
    setweight(to_tsvector('simple', coalesce(array_to_string(tags, ' '), '')), 'A') ||
    setweight(to_tsvector('simple', coalesce(content, '')), 'B')
where search_vector is null;

update knowledge_chunks
set search_vector =
    setweight(to_tsvector('simple', coalesce(content, '')), 'A') ||
    setweight(to_tsvector('simple', coalesce(metadata ->> 'documentTitle', '')), 'B') ||
    setweight(to_tsvector('simple', coalesce(metadata ->> 'headingPath', '')), 'B')
where search_vector is null;

create index if not exists idx_memories_search_vector
    on memories using gin (search_vector);

create index if not exists idx_knowledge_chunks_search_vector
    on knowledge_chunks using gin (search_vector);
