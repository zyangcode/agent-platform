insert into mcp_servers (
    tenant_id,
    name,
    server_type,
    connection_config,
    status,
    last_discovered_at
)
select
    1,
    'Bundled Weather MCP',
    'STDIO',
    '{"command": "builtin-demo-weather-mcp", "demo": true}'::jsonb,
    'ACTIVE',
    current_timestamp
where exists (select 1 from tenants where id = 1)
  and not exists (
      select 1 from mcp_servers
      where tenant_id = 1 and name = 'Bundled Weather MCP'
  );

insert into mcp_tools (
    mcp_server_id,
    name,
    description,
    parameter_schema,
    permission_declaration,
    status
)
select
    server.id,
    'weather.current',
    'Get current demo weather by city',
    '{
        "type": "object",
        "properties": {
            "city": {
                "type": "string",
                "description": "City name, for example 重庆 or Beijing"
            }
        },
        "required": ["city"],
        "x-readOnly": true,
        "x-riskLevel": "LOW"
    }'::jsonb,
    '{"network": false, "file": false, "location": "city"}'::jsonb,
    'AVAILABLE'
from mcp_servers server
where server.tenant_id = 1
  and server.name = 'Bundled Weather MCP'
  and not exists (
      select 1 from mcp_tools tool
      where tool.mcp_server_id = server.id and tool.name = 'weather.current'
  );

select setval('mcp_servers_id_seq', greatest((select max(id) from mcp_servers), 1));
select setval('mcp_tools_id_seq', greatest((select max(id) from mcp_tools), 1));
