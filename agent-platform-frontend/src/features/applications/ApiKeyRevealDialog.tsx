import { useState } from 'react'
import { Check, Copy, KeyRound } from 'lucide-react'
import { Alert, AlertDescription, AlertTitle } from '@/components/ui/alert'
import { Button } from '@/components/ui/button'
import {
  Dialog,
  DialogContent,
  DialogDescription,
  DialogFooter,
  DialogHeader,
  DialogTitle,
} from '@/components/ui/dialog'
import type { CreatedApiKey } from '@/lib/api/types'

type ApiKeyRevealDialogProps = {
  apiKey: CreatedApiKey | null
  onOpenChange: (open: boolean) => void
  open: boolean
}

export function ApiKeyRevealDialog({ apiKey, onOpenChange, open }: ApiKeyRevealDialogProps) {
  const [hasCopied, setHasCopied] = useState(false)

  async function copyKey() {
    if (!apiKey?.key) {
      return
    }

    await navigator.clipboard.writeText(apiKey.key)
    setHasCopied(true)
    window.setTimeout(() => setHasCopied(false), 1600)
  }

  return (
    <Dialog onOpenChange={onOpenChange} open={open}>
      <DialogContent>
        <DialogHeader>
          <div className="mb-3 flex h-10 w-10 items-center justify-center rounded-2xl border border-cyan-200/20 bg-cyan-300/10">
            <KeyRound className="h-5 w-5 text-cyan-100" strokeWidth={1.75} />
          </div>
          <DialogTitle>API key created</DialogTitle>
          <DialogDescription>
            This secret is shown once. Store it now; only the key prefix will be visible later.
          </DialogDescription>
        </DialogHeader>

        <Alert variant="warning">
          <AlertTitle>One-time secret</AlertTitle>
          <AlertDescription>
            Closing this dialog removes the plaintext key from the UI. The backend stores only a
            hash and key prefix.
          </AlertDescription>
        </Alert>

        <div className="mt-4 rounded-2xl border border-white/10 bg-zinc-950/70 p-4">
          <p className="text-xs uppercase tracking-[0.16em] text-zinc-500">Secret key</p>
          <p className="mt-3 break-all font-mono text-sm text-cyan-100">{apiKey?.key ?? '-'}</p>
        </div>

        <DialogFooter>
          <Button onClick={copyKey} type="button" variant="secondary">
            {hasCopied ? (
              <Check className="h-4 w-4" strokeWidth={1.75} />
            ) : (
              <Copy className="h-4 w-4" strokeWidth={1.75} />
            )}
            {hasCopied ? 'Copied' : 'Copy key'}
          </Button>
          <Button onClick={() => onOpenChange(false)} type="button">
            Done
          </Button>
        </DialogFooter>
      </DialogContent>
    </Dialog>
  )
}
