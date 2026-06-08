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
    'Demo HTTP Calculator MCP',
    'HTTP',
    '{"baseUrl": "http://localhost:8080/demo/mcp"}'::jsonb,
    'ACTIVE',
    current_timestamp
where exists (select 1 from tenants where id = 1)
  and not exists (
      select 1 from mcp_servers
      where tenant_id = 1 and name = 'Demo HTTP Calculator MCP'
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
    'calculator',
    'Calculate a math expression via HTTP MCP',
    '{
        "type": "object",
        "properties": {
            "expression": {
                "type": "string",
                "description": "Math expression, e.g. 128*36+59"
            }
        },
        "required": ["expression"],
        "x-readOnly": true,
        "x-riskLevel": "LOW"
    }'::jsonb,
    '{}'::jsonb,
    'AVAILABLE'
from mcp_servers server
where server.tenant_id = 1
  and server.name = 'Demo HTTP Calculator MCP'
  and not exists (
      select 1 from mcp_tools tool
      where tool.mcp_server_id = server.id and tool.name = 'calculator'
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
    'search',
    'Search Wikipedia via HTTP MCP',
    '{
        "type": "object",
        "properties": {
            "query": {
                "type": "string",
                "description": "Search query"
            }
        },
        "required": ["query"],
        "x-readOnly": true,
        "x-riskLevel": "LOW"
    }'::jsonb,
    '{}'::jsonb,
    'AVAILABLE'
from mcp_servers server
where server.tenant_id = 1
  and server.name = 'Demo HTTP Calculator MCP'
  and not exists (
      select 1 from mcp_tools tool
      where tool.mcp_server_id = server.id and tool.name = 'search'
  );

select setval('mcp_servers_id_seq', greatest((select max(id) from mcp_servers), 1));
select setval('mcp_tools_id_seq', greatest((select max(id) from mcp_tools), 1));
