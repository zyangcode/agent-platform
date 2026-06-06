import { Outlet } from 'react-router-dom'
import { Sidebar } from './Sidebar'
import { TopStatusBar } from './TopStatusBar'

export function ConsoleLayout() {
  return (
    <div className="min-h-[100dvh] bg-bg text-text">
      {/* Fixed substrate grid */}
      <div className="substrate-grid" />

      <div className="relative mx-auto flex min-h-[100dvh] w-full max-w-[1480px] gap-3 px-3 py-3 md:px-4">
        <Sidebar />
        <div className="flex min-h-0 flex-col gap-2 min-w-0 flex-1">
          <TopStatusBar />
          <div className="flex-1 min-h-0">
            <Outlet />
          </div>
        </div>
      </div>
    </div>
  )
}
