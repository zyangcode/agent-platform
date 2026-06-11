export const CHAT_AUTO_SCROLL_THRESHOLD_PX = 72

export type ScrollSnapshot = {
  clientHeight: number
  scrollHeight: number
  scrollTop: number
}

export function isNearScrollBottom(
  snapshot: ScrollSnapshot,
  thresholdPx = CHAT_AUTO_SCROLL_THRESHOLD_PX,
) {
  return snapshot.scrollHeight - snapshot.scrollTop - snapshot.clientHeight <= thresholdPx
}

export type AutoScrollDecision = {
  isStreaming: boolean
  wasNearBottom: boolean
}

export function shouldAutoScrollMessages({ isStreaming, wasNearBottom }: AutoScrollDecision) {
  return isStreaming || wasNearBottom
}
