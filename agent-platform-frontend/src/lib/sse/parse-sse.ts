export type SseParsedEvent<TData = unknown> = {
  event: string
  data: TData
}

export type ParseSseChunkResult<TData = unknown> = {
  events: SseParsedEvent<TData>[]
  remainder: string
}

export function parseSseChunk<TData = unknown>(
  previousRemainder: string,
  chunk: string,
): ParseSseChunkResult<TData> {
  const text = `${previousRemainder}${chunk}`.replace(/\r\n/g, '\n')
  const parts = text.split('\n\n')
  const remainder = parts.pop() ?? ''
  const events = parts
    .map((part) => parseEvent<TData>(part))
    .filter((event): event is SseParsedEvent<TData> => event !== null)

  return { events, remainder }
}

function parseEvent<TData>(rawEvent: string): SseParsedEvent<TData> | null {
  const lines = rawEvent.split('\n')
  const dataLines: string[] = []
  let eventName = 'message'

  lines.forEach((line) => {
    if (line.startsWith('event:')) {
      eventName = line.slice('event:'.length).trim() || eventName
      return
    }
    if (line.startsWith('data:')) {
      dataLines.push(line.slice('data:'.length).trimStart())
    }
  })

  if (dataLines.length === 0) {
    return null
  }

  return {
    event: eventName,
    data: JSON.parse(dataLines.join('')) as TData,
  }
}
