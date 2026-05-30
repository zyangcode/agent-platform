update skills
set description = 'Open-Meteo weather query skill'
where code = 'weather';

update skills
set description = 'Wikipedia OpenSearch skill'
where code = 'search';

update skill_versions
set
    return_schema = '{"type": "object", "properties": {"source": {"type": "string"}, "city": {"type": "string"}, "country": {"type": "string"}, "temperatureC": {"type": "number"}, "windSpeedKmh": {"type": "number"}, "weather": {"type": "string"}, "summary": {"type": "string"}}}'::jsonb,
    runtime_config = '{"handler": "builtin:open-meteo-weather"}'::jsonb
where skill_id = (select id from skills where code = 'weather');

update skill_versions
set
    return_schema = '{"type": "object", "properties": {"source": {"type": "string"}, "query": {"type": "string"}, "results": {"type": "array"}}}'::jsonb,
    runtime_config = '{"handler": "builtin:wikipedia-opensearch"}'::jsonb
where skill_id = (select id from skills where code = 'search');
