# Phase 3 Frontend Console Design

## 1. Goal

Stage 3 turns the backend-only Agent platform into a demonstrable web console.

The MVP must let a user:

- Log in and keep a JWT session.
- View a console dashboard.
- Create and view Applications and one-time API Keys.
- Create, view, and configure Agent Profiles.
- Chat with an Agent through the Web entrypoint.
- View Trace lists and Trace details.
- View Token Usage summaries and call details.
- View existing Skill and MCP tool information.

Stage 3 is not a full admin system. It does not include Skill upload, Jar hot loading UI, Team orchestration, Feishu bot configuration, complete model provider management, full RBAC/user management, or complete security policy management.

## 2. Current Backend Assumptions

The frontend talks to existing Stage 1 and Stage 2 backend APIs.

Local development endpoints:

```text
Frontend: http://localhost:5173
Web:      http://localhost:8080
Gateway:  http://localhost:8081
```

Vite proxy:

```text
/api -> http://localhost:8080
```

The frontend should use the Web module as the browser entrypoint. AI chat from the browser goes through Web, and Web forwards to Gateway. The frontend should not call Core directly.

Known Stage 2 issue: when model invocation returns `MODEL_ERROR`, `trace_roots` may not be written. This does not block Stage 3, but Trace pages must show a clear empty or missing-trace state instead of crashing.

## 3. Tech Stack

The frontend project lives in:

```text
agent-platform-frontend/
```

Fixed stack:

```text
React
Vite
TypeScript
Tailwind CSS
shadcn/ui
ECharts
```

Local Stage 3 development does not use nginx. nginx is reserved for final Docker Compose or deployment delivery.

## 4. Visual Direction

Confirmed visual decisions:

```text
Overall style: high-end dark AI console
Navigation: fixed left sidebar + top status bar
Dashboard: workstation-style density
Agent Chat: center conversation + right runtime detail panel
Trace Detail: timeline + right detail panel
Token Usage: restrained dashboard charts
```

The UI should feel like a serious AI operations console, not a generic white admin panel or decorative landing page.

Design rules:

- Use a dark, layered surface system with restrained accents.
- Keep information dense enough for operational use.
- Avoid oversized marketing hero sections.
- Avoid decorative gradient blobs or one-note purple/blue palettes.
- Use icons for navigation and compact actions.
- Keep cards at modest radius and avoid cards inside cards.
- Make empty, loading, error, unauthorized, and expired-token states explicit.

## 5. Page Scope

### 5.1 Login

Purpose:

- Let a user log in with username and password.
- Store the returned JWT.
- Route authenticated users into the console.

Behavior:

- Calls `POST /api/auth/login`.
- Shows validation errors and backend auth failures.
- Provides a minimal register link or action if the register endpoint is included in the first frontend pass.

### 5.2 Console Dashboard

Purpose:

- Show a first-glance overview of the platform state.

Content:

- Core metric strip: Applications, Profiles, recent Traces, Token Usage.
- Recent Trace list.
- Token Usage summary chart.
- Quick actions: create Application, create Profile, start Chat.

Dashboard data can start with existing list endpoints. If a dedicated aggregate endpoint does not exist, the frontend should derive simple counts from page/list responses for MVP.

### 5.3 Applications / API Keys

Purpose:

- Create and list Applications.
- Display the one-time API Key after creation.

Behavior:

- Calls Application APIs under Web.
- API Key must be visually treated as one-time sensitive data.
- Provide copy action and clear warning that it will not be shown again.

### 5.4 Profiles

Purpose:

- Create and list Agent Profiles.
- Bind existing Skills and MCP tools to a Profile where current APIs support it.

Behavior:

- Uses existing Profile, Skill, and MCP query/bind APIs.
- First version should prioritize readable configuration over complex editing workflows.

### 5.5 Agent Chat

Purpose:

- Let a user run an Agent from the browser.
- Demonstrate SSE events and runtime governance context.

Layout:

- Center: conversation stream and message composer.
- Right panel: selected Profile, enabled Skill/MCP summary, Trace ID, run status, Token Usage, and error state.

Behavior:

- Calls Web AI chat endpoint.
- Handles SSE event types such as `thinking`, `action`, `observation`, `message`, `done`, and `error`.
- Keeps partial streaming output stable and readable.
- Provides a clear fallback if SSE fails.

### 5.6 Trace List

Purpose:

- Show recent Trace roots for the current user.

Behavior:

- Supports basic filtering by status, application, profile, and entrypoint if backend query supports it.
- Links each row to Trace Detail.
- Missing or empty data must be treated as a normal state.

### 5.7 Trace Detail

Purpose:

- Make Stage 2 governance visible.

Layout:

- Left/main: ordered span timeline.
- Right: selected span detail panel.

Content:

- Root metadata: trace ID, status, entrypoint, latency, model mode, timestamps.
- Span timeline: component, span name, type, status, latency, error.
- Token usage section for the trace.

The page must handle the known `MODEL_ERROR` missing-trace case with a clear message.

### 5.8 Token Usage

Purpose:

- Show token accounting and usage details.

Layout:

- Summary metrics.
- Restrained ECharts chart area.
- Usage detail table.

If the backend only exposes list/detail style data, the first version should compute simple totals client-side.

### 5.9 Skills / MCP Tools

Purpose:

- Display existing Skills and MCP tools.
- Help users understand what can be bound or used by Profiles.

Out of scope:

- Skill upload.
- Jar hot loading.
- Skill audit/review workflow.

## 6. Architecture

Recommended frontend structure:

```text
agent-platform-frontend/
  src/
    app/
      router.tsx
      providers.tsx
    components/
      layout/
      ui/
      charts/
      feedback/
    features/
      auth/
      dashboard/
      applications/
      profiles/
      chat/
      traces/
      token-usage/
      tools/
    lib/
      api/
      auth/
      sse/
      format/
    styles/
```

Boundaries:

- `lib/api` owns HTTP client, response unwrapping, error normalization, and JWT header injection.
- `lib/auth` owns token persistence and current-user state.
- Feature folders own page-specific components and API adapters.
- Shared UI components stay generic and should not know backend DTO details.
- SSE handling should be isolated in `lib/sse` or the chat feature, not mixed into global HTTP client logic.

## 7. Data Flow

Authentication:

```text
Login form -> POST /api/auth/login -> save JWT -> GET /api/auth/me -> render console
```

Authenticated API calls:

```text
Page -> feature API wrapper -> shared API client -> Web backend
```

Chat:

```text
Chat page -> Web SSE endpoint -> event parser -> conversation state + runtime detail panel
```

Trace:

```text
Trace list -> select trace -> Trace detail -> timeline + token usage detail
```

## 8. Error Handling

Global handling:

- `401`: clear token and return to login.
- `403`: show permission or security-policy message.
- `429`: show quota exceeded state.
- `5xx`: show backend error state with retry action.
- Network failure: show reconnect/retry state.

Page handling:

- Empty lists are valid states.
- Missing trace detail is valid because of the known Stage 2 `MODEL_ERROR` trace gap.
- SSE errors must not leave the chat page in a permanent loading state.

## 9. Testing And Verification

Automated checks:

- `npm run build`
- `npm run lint` if linting is configured in the scaffold.
- Focused component/API utility tests can be added if the chosen scaffold includes a test runner.

Manual checks:

- Login with `admin/admin123`.
- Create Application and copy API Key.
- View model/profile/skill/MCP data.
- Run an Agent chat and observe SSE output.
- Open Trace List and Trace Detail.
- Open Token Usage.
- Confirm unauthorized users are redirected to login.
- Confirm Apifox can still import `http://localhost:8080/v3/api-docs`.

Visual checks:

- Desktop viewport first.
- At least one narrower viewport to ensure no text overlap.
- Check dark theme contrast, navigation clarity, loading states, and empty states.

## 10. Implementation Order

1. Scaffold `agent-platform-frontend`.
2. Configure Vite, TypeScript, Tailwind CSS, shadcn/ui, routing, and path aliases.
3. Build shared API client and auth token storage.
4. Implement Login and authenticated route guard.
5. Implement shell layout: left sidebar and top status bar.
6. Implement Dashboard.
7. Implement Applications / API Keys.
8. Implement Profiles, Skills, and MCP display/bind flows.
9. Implement Agent Chat and SSE handling.
10. Implement Trace List and Trace Detail.
11. Implement Token Usage.
12. Run build, browser verification, screenshot review, and manual acceptance.

## 11. Open Decisions

All major Stage 3 visual decisions have been confirmed.

Remaining decisions can be made during implementation as long as they do not alter the approved visual direction or page scope:

- Exact accent color pair.
- Exact chart palette.
- Exact copy text for empty states.
- Exact table column order.

If a decision changes layout, interaction model, or visual personality, ask the user before implementation.
