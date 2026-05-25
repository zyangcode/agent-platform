export type PageNavigationInput = {
  pageNo: number
  totalPages: number
}

export function getPageNavigationState({ pageNo, totalPages }: PageNavigationInput) {
  const stableTotalPages = Math.max(1, totalPages || 0)
  const currentPage = Math.min(Math.max(1, pageNo || 1), stableTotalPages)

  return {
    canGoNext: currentPage < stableTotalPages,
    canGoPrevious: currentPage > 1,
    currentPage,
    totalPages: stableTotalPages,
  }
}
