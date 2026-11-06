# Composio consumer keys (the Connect door)

A consumer key is the one a personal Composio account gets from the "For You" / Connect side, and it usually starts
`ck_`. It does **not** work on the backend REST API: the same key answers 801 on `x-api-key`, 2113 on
`x-user-api-key` and refused on `x-org-api-key`. It only works on Composio's Connect MCP endpoint.

## How the app uses it

| What | Where |
|---|---|
| Endpoint | `POST https://connect.composio.dev/mcp`, header `x-consumer-api-key` |
| Wire | MCP Streamable HTTP: `initialize`, then `notifications/initialized`, then `tools/call`; replies are JSON or `text/event-stream` |
| Catalog | `COMPOSIO_SEARCH_TOOLS` (a set of ordinary read use cases, plus the user's own search text) |
| Connected apps | the `toolkit_connection_statuses` a search returns, then `COMPOSIO_MANAGE_CONNECTIONS` with `action: list` |
| Connecting an app | `COMPOSIO_MANAGE_CONNECTIONS` with `action: add`; the `redirect_url` is opened in the browser |
| Reads | `COMPOSIO_MULTI_EXECUTE_TOOL`, every slug through `ReadToolRules` |
| Key check | the MCP handshake itself; the screen shows "Consumer key (Composio Connect)" |

## Never called

`ConnectRules.blocked` is refused before any request, and the meta tools are a whitelist so a tool Composio adds
tomorrow is refused until someone decides it is safe:

`COMPOSIO_REMOTE_BASH_TOOL`, `COMPOSIO_REMOTE_WORKBENCH`, `COMPOSIO_PROXY_EXECUTE`, `COMPOSIO_MANAGE_SKILL`,
`COMPOSIO_SEARCH_SKILLS`, `COMPOSIO_USE_SKILL`, `COMPOSIO_SUBMIT_FEEDBACK`.

## Which door a key takes

`RoutingComposioApi` asks the stored kind first, then the prefix, then tries the other door once before failing, so a
`ck_` key, an unknown prefix and a project key all end up working. Every header's own status and message is kept for
the screen.

## Tests

- `ComposioConnectTest` (MockWebServer, 13 tests): the handshake over SSE and over plain JSON, a 55 toolkit catalog,
  per toolkit read tools, connected accounts, a connect link, a read through multi execute, the refused-read line, the
  blocked tools with **zero** requests, write slugs refused inside multi execute, a good key, a refused key.
- `ComposioConnectLiveTest` runs only when `COMPOSIO_CONSUMER_KEY` is in the environment (the server runs it with the
  Vault value; nothing secret is written down). It does the handshake, reads the tool list and lists connected
  accounts, nothing else.
