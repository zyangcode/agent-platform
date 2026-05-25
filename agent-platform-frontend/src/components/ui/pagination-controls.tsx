import { ChevronLeft, ChevronRight } from 'lucide-react'
import { Button } from './button'
import { getPageNavigationState } from './pagination-utils'

type PaginationControlsProps = {
  onPageChange: (pageNo: number) => void
  pageNo: number
  total: number
  totalPages: number
}

export function PaginationControls({
  onPageChange,
  pageNo,
  total,
  totalPages,
}: PaginationControlsProps) {
  const navigation = getPageNavigationState({ pageNo, totalPages })

  return (
    <div className="mt-4 flex flex-col gap-3 border-t border-white/10 pt-4 md:flex-row md:items-center md:justify-between">
      <p className="text-sm text-zinc-500">
        <span className="font-mono text-zinc-300">{total}</span> records · page{' '}
        <span className="font-mono text-zinc-300">{navigation.currentPage}</span> /{' '}
        <span className="font-mono text-zinc-300">{navigation.totalPages}</span>
      </p>
      <div className="flex items-center gap-2">
        <Button
          disabled={!navigation.canGoPrevious}
          onClick={() => onPageChange(navigation.currentPage - 1)}
          size="sm"
          variant="secondary"
        >
          <ChevronLeft className="h-4 w-4" strokeWidth={1.75} />
          Previous
        </Button>
        <Button
          disabled={!navigation.canGoNext}
          onClick={() => onPageChange(navigation.currentPage + 1)}
          size="sm"
          variant="secondary"
        >
          Next
          <ChevronRight className="h-4 w-4" strokeWidth={1.75} />
        </Button>
      </div>
    </div>
  )
}
