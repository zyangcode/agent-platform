import { Outlet } from 'react-router-dom'
import { Sidebar } from './Sidebar'
import { TopStatusBar } from './TopStatusBar'

export function ConsoleLayout() {
  return (
    <main className="min-h-[100dvh] bg-[radial-gradient(circle_at_top_left,rgba(20,184,166,0.12),transparent_34rem),linear-gradient(135deg,#09090b_0%,#111827_45%,#0f172a_100%)] text-zinc-100">
      <div className="mx-auto grid min-h-[100dvh] w-full max-w-[1480px] gap-6 px-4 py-6 md:px-6 lg:grid-cols-[272px_1fr]">
        <Sidebar />
        <section className="min-w-0 space-y-6">
          <TopStatusBar />
          <Outlet />
        </section>
      </div>
    </main>
  )
}
