import { describe, expect, it } from 'vitest'
import { parseSseChunk } from './parse-sse'

describe('parseSseChunk', () => {
  it('parses complete SSE events and keeps incomplete trailing data', () => {
    const result = parseSseChunk(
      '',
      'event: thinking\n' +
        'data: {"type":"thinking","traceId":"tr_1","step":1,"content":"accepted"}\n\n' +
        'event: message\n' +
        'data: {"type":"message","traceId":"tr_1","step":2,"content":"hel',
    )

    expect(result.events).toEqual([
      {
        event: 'thinking',
        data: {
          content: 'accepted',
          step: 1,
          traceId: 'tr_1',
          type: 'thinking',
        },
      },
    ])
    expect(result.remainder).toBe(
      'event: message\n' +
        'data: {"type":"message","traceId":"tr_1","step":2,"content":"hel',
    )
  })

  it('joins multi-line data before JSON parsing', () => {
    const result = parseSseChunk(
      '',
      'event: message\n' +
        'data: {"type":"message",\n' +
        'data: "content":"ok"}\n\n',
    )

    expect(result).toEqual({
      events: [
        {
          event: 'message',
          data: {
            content: 'ok',
            type: 'message',
          },
        },
      ],
      remainder: '',
    })
  })

  it('parses team runtime events as ordinary SSE payloads', () => {
    const result = parseSseChunk(
      '',
      'event: team_plan\n' +
        'data: {"type":"team_plan","traceId":"tr_team","step":2,"role":"PLANNER","content":"Planner generated task plan","payload":{"goal":"demo"}}\n\n',
    )

    expect(result).toEqual({
      events: [
        {
          event: 'team_plan',
          data: {
            content: 'Planner generated task plan',
            payload: {
              goal: 'demo',
            },
            role: 'PLANNER',
            step: 2,
            traceId: 'tr_team',
            type: 'team_plan',
          },
        },
      ],
      remainder: '',
    })
  })
})
