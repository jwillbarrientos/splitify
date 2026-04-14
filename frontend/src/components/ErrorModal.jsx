import { X, CircleX, Lightbulb, ArrowLeft } from 'lucide-react'

function ErrorModal({ visible, title, message, tip, onClose }) {
  if (!visible) return null

  return (
    <div className="fixed inset-0 z-[60] flex items-center justify-center bg-[#0A0A0A]/80 p-4">
      <div className="relative flex w-[480px] max-w-full flex-col items-center gap-6 rounded-2xl border border-[#27272A] bg-[#1A1A1A] p-8 shadow-[0_8px_32px_rgba(0,0,0,0.4)]">
        {/* Close */}
        <button
          onClick={onClose}
          className="absolute right-6 top-6 text-[#A1A1AA] transition hover:text-white"
        >
          <X size={20} />
        </button>

        {/* Icon */}
        <div className="flex h-16 w-16 items-center justify-center rounded-full bg-red-500/10">
          <CircleX size={32} className="text-red-500" />
        </div>

        {/* Header */}
        <div className="flex w-full flex-col items-center gap-2">
          <h2 className="text-center text-xl font-bold text-white">
            {title || 'Error al crear playlist'}
          </h2>
          <p className="text-center text-sm text-[#A1A1AA]">
            {message || 'Ocurrió un error inesperado.'}
          </p>
        </div>

        {/* Tip box */}
        {tip && (
          <div className="flex w-full items-start gap-2.5 rounded-lg bg-[#0A0A0A] p-4">
            <Lightbulb size={16} className="mt-0.5 shrink-0 text-[#71717A]" />
            <p className="text-[13px] text-[#A1A1AA]">{tip}</p>
          </div>
        )}

        {/* Back button */}
        <button
          onClick={onClose}
          className="flex w-full items-center justify-center gap-2 rounded-lg border border-[#27272A] bg-[#0A0A0A] px-4 py-2.5 text-sm font-medium text-white transition hover:bg-[#27272A]"
        >
          <ArrowLeft size={16} />
          Volver
        </button>
      </div>
    </div>
  )
}

export default ErrorModal
