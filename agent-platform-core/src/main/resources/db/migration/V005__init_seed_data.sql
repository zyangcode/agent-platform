insert into tenants (id, name, code, status)
values (1, 'Default Tenant', 'default', 'ACTIVE');

select setval('tenants_id_seq', (select max(id) from tenants));

insert into roles (id, code, name, description)
values
    (1, 'ADMIN', 'Admin', 'Platform administrator'),
    (2, 'DEVELOPER', 'Developer', 'Application developer'),
    (3, 'USER', 'User', 'Default user');

select setval('roles_id_seq', (select max(id) from roles));

insert into users (id, tenant_id, username, password_hash, display_name, email, status)
values (
    1,
    1,
    'admin',
    '$2a$10$ZI.EthVTQv.Ug8ZwTSnzCeu973.MusQBD298zwwH3xe0GLw.2pVcW',
    'Default Admin',
    null,
    'ACTIVE'
);

select setval('users_id_seq', (select max(id) from users));

insert into user_roles (user_id, role_id)
values
    (1, 1),
    (1, 2),
    (1, 3);

insert into model_providers (id, name, provider_type, base_url, api_key_encrypted, status)
values (
    1,
    'Local Mock Provider',
    'OPENAI_COMPATIBLE',
    'http://localhost:11434/v1',
    null,
    'ACTIVE'
);

select setval('model_providers_id_seq', (select max(id) from model_providers));

insert into model_configs (
    id,
    provider_id,
    model_name,
    display_name,
    capabilities,
    default_temperature,
    max_context_tokens,
    status
)
values (
    1,
    1,
    'mock-chat',
    'Mock Chat Model',
    '{"text": true, "stream": true}'::jsonb,
    0.70,
    8192,
    'ACTIVE'
);

select setval('model_configs_id_seq', (select max(id) from model_configs));

insert into skills (
    id,
    tenant_id,
    owner_user_id,
    name,
    code,
    description,
    skill_type,
    scope,
    permission_declaration,
    status
)
values
    (1, 1, null, 'Calculator', 'calculator', 'Built-in arithmetic calculator', 'BUILTIN', 'GLOBAL', '{"network": false, "file": false}'::jsonb, 'ENABLED'),
    (2, 1, null, 'Weather', 'weather', 'Open-Meteo weather query skill', 'BUILTIN', 'GLOBAL', '{"network": true, "file": false}'::jsonb, 'ENABLED'),
    (3, 1, null, 'Search', 'search', 'Wikipedia OpenSearch skill', 'BUILTIN', 'GLOBAL', '{"network": true, "file": false}'::jsonb, 'ENABLED');

select setval('skills_id_seq', (select max(id) from skills));

insert into skill_versions (
    id,
    skill_id,
    version,
    parameter_schema,
    return_schema,
    runtime_config,
    dependencies,
    status
)
values
    (1, 1, '1.0.0', '{"type": "object", "properties": {"expression": {"type": "string"}}, "required": ["expression"]}'::jsonb, '{"type": "object", "properties": {"result": {"type": "string"}}}'::jsonb, '{"handler": "builtin:calculator"}'::jsonb, '[]'::jsonb, 'READY'),
    (2, 2, '1.0.0', '{"type": "object", "properties": {"city": {"type": "string"}}, "required": ["city"]}'::jsonb, '{"type": "object", "properties": {"source": {"type": "string"}, "city": {"type": "string"}, "country": {"type": "string"}, "temperatureC": {"type": "number"}, "windSpeedKmh": {"type": "number"}, "weather": {"type": "string"}, "summary": {"type": "string"}}}'::jsonb, '{"handler": "builtin:open-meteo-weather"}'::jsonb, '[]'::jsonb, 'READY'),
    (3, 3, '1.0.0', '{"type": "object", "properties": {"query": {"type": "string"}}, "required": ["query"]}'::jsonb, '{"type": "object", "properties": {"source": {"type": "string"}, "query": {"type": "string"}, "results": {"type": "array"}}}'::jsonb, '{"handler": "builtin:wikipedia-opensearch"}'::jsonb, '[]'::jsonb, 'READY');

select setval('skill_versions_id_seq', (select max(id) from skill_versions));

update skills set current_version_id = 1 where id = 1;
update skills set current_version_id = 2 where id = 2;
update skills set current_version_id = 3 where id = 3;

insert into mcp_servers (
    id,
    tenant_id,
    name,
    server_type,
    connection_config,
    status,
    last_discovered_at
)
values (
    1,
    1,
    'Readonly Filesystem MCP',
    'STDIO',
    '{"command": "mock-filesystem-mcp", "readonly": true}'::jsonb,
    'ACTIVE',
    current_timestamp
);

select setval('mcp_servers_id_seq', (select max(id) from mcp_servers));

insert into mcp_tools (
    id,
    mcp_server_id,
    name,
    description,
    parameter_schema,
    permission_declaration,
    status
)
values (
    1,
    1,
    'read_file',
    'Read an allowed local text file in readonly mode',
    '{"type": "object", "properties": {"path": {"type": "string"}}, "required": ["path"]}'::jsonb,
    '{"file": "readonly", "network": false}'::jsonb,
    'AVAILABLE'
);

select setval('mcp_tools_id_seq', (select max(id) from mcp_tools));
