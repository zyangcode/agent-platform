import type { ChatStreamEvent } from './types'

export function nextAssistantContent(currentContent: string, event: ChatStreamEvent) {
  if (!event.content) {
    return currentContent
  }
  if (event.type === 'message_delta') {
    if (currentContent.length === 0 && event.content === 'null') {
      return currentContent
    }
    return `${currentContent}${event.content}`
  }
  if (event.type === 'message' || event.type === 'team_final') {
    if (currentContent.trim().length > event.content.trim().length) {
      return currentContent
    }
    return event.content
  }
  return currentContent
}

export function isAssistantContentEvent(event: ChatStreamEvent) {
  return event.type === 'message' || event.type === 'message_delta' || event.type === 'team_final'
}

export function shouldShowRuntimeTimelineEvent(event: ChatStreamEvent) {
  return event.type !== 'message_delta'
}
