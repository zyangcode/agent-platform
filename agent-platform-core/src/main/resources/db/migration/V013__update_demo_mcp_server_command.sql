update mcp_servers
set connection_config = jsonb_set(connection_config, '{command}', '"builtin-demo-filesystem-mcp"', true)
where name = 'Readonly Filesystem MCP'
  and server_type = 'STDIO'
  and connection_config ->> 'command' = 'mock-filesystem-mcp';
