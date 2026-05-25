import { Activity, Database, KeyRound, ShieldCheck } from 'lucide-react'

const navigationItems = [
  'Dashboard',
  'Applications',
  'Profiles',
  'Agent Chat',
  'Traces',
  'Token Usage',
  'Tools',
]

const metrics = [
  { label: 'Applications', value: '0', icon: KeyRound },
  { label: 'Profiles', value: '0', icon: ShieldCheck },
  { label: 'Trace roots', value: '0', icon: Activity },
  { label: 'Token usage', value: '0', icon: Database },
]

function App() {
  return (
    <main className="min-h-[100dvh] bg-[radial-gradient(circle_at_top_left,rgba(20,184,166,0.12),transparent_34rem),linear-gradient(135deg,#09090b_0%,#111827_45%,#0f172a_100%)] text-zinc-100">
      <div className="mx-auto flex min-h-[100dvh] w-full max-w-7xl flex-col px-6 py-8">
        <header className="flex items-center justify-between border-b border-white/10 pb-6">
          <div>
            <p className="text-xs uppercase tracking-[0.22em] text-cyan-200/70">
              Agent Platform
            </p>
            <h1 className="mt-3 text-3xl font-semibold tracking-tight text-white">
              Console scaffold
            </h1>
          </div>
          <div className="rounded-full border border-emerald-300/20 bg-emerald-300/10 px-4 py-2 text-sm text-emerald-100">
            Frontend ready
          </div>
        </header>

        <section className="grid flex-1 gap-6 py-8 lg:grid-cols-[280px_1fr]">
          <aside className="rounded-2xl border border-white/10 bg-white/[0.035] p-4 shadow-[inset_0_1px_0_rgba(255,255,255,0.08)]">
            <nav className="space-y-2 text-sm text-zinc-300">
              {navigationItems.map((item) => (
                <div
                  className="rounded-xl border border-transparent px-3 py-2 transition hover:border-white/10 hover:bg-white/[0.05]"
                  key={item}
                >
                  {item}
                </div>
              ))}
            </nav>
          </aside>

          <section className="space-y-6">
            <div className="grid gap-4 md:grid-cols-2 xl:grid-cols-4">
              {metrics.map((metric) => {
                const Icon = metric.icon

                return (
                  <article
                    className="rounded-2xl border border-white/10 bg-zinc-950/45 p-5 shadow-[inset_0_1px_0_rgba(255,255,255,0.08)]"
                    key={metric.label}
                  >
                    <div className="flex items-center justify-between">
                      <span className="text-sm text-zinc-400">{metric.label}</span>
                      <Icon className="h-4 w-4 text-cyan-200/80" strokeWidth={1.75} />
                    </div>
                    <p className="mt-5 font-mono text-3xl text-white">{metric.value}</p>
                  </article>
                )
              })}
            </div>

            <div className="rounded-2xl border border-white/10 bg-white/[0.035] p-6 shadow-[inset_0_1px_0_rgba(255,255,255,0.08)]">
              <h2 className="text-xl font-medium tracking-tight text-white">
                Stage 3 frontend foundation
              </h2>
              <p className="mt-3 max-w-2xl text-sm leading-6 text-zinc-400">
                React, Vite, TypeScript, Tailwind CSS, routing dependencies, charting,
                icons, path aliases, and the local API proxy are ready for the console MVP.
              </p>
            </div>
          </section>
        </section>
      </div>
    </main>
  )
}

export default App
