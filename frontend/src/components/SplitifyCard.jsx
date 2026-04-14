import { Eye, Music, Clock, Check, RefreshCw, Trash2 } from 'lucide-react'

const COVER_GRADIENTS = [
  'from-blue-500 to-purple-500',
  'from-red-500 to-orange-500',
  'from-pink-500 to-purple-500',
  'from-green-500 to-yellow-400',
  'from-cyan-500 to-blue-500',
  'from-amber-500 to-pink-500',
  'from-emerald-500 to-teal-400',
]

function SplitifyCard({ playlist, selected, onToggleSelect, onViewDetails, onUpdate, onDelete, refreshing = false }) {
  const gradientIndex = playlist.id % COVER_GRADIENTS.length
  const gradient = COVER_GRADIENTS[gradientIndex]

  return (
    <div
      className={`relative flex flex-col gap-3 rounded-xl p-4 transition cursor-pointer ${
        refreshing
          ? 'border-2 border-green-500/70 bg-[#1A1A1A] shadow-[0_0_24px_rgba(34,197,94,0.25)]'
          : selected
            ? 'border-2 border-green-500 bg-[#1A1A1A]'
            : 'border border-[#27272A] bg-[#1A1A1A]'
      }`}
      onClick={refreshing ? undefined : onToggleSelect}
    >
      {/* Checkbox */}
      <div className="flex justify-end">
        <div
          className={`flex h-5 w-5 items-center justify-center rounded transition ${
            selected
              ? 'bg-green-500'
              : 'border border-[#27272A] bg-[#0A0A0A]'
          }`}
        >
          {selected && <Check size={14} className="text-black" strokeWidth={3} />}
        </div>
      </div>

      {/* Cover */}
      <div className={`flex aspect-square w-full items-center justify-center rounded-lg bg-gradient-to-br ${gradient}`}>
        <Music size={48} className="text-white/80" />
      </div>

      {/* Name */}
      <p className="truncate text-sm font-semibold text-white">{playlist.name}</p>

      {/* Meta */}
      <div className="flex items-center gap-1">
        <Clock size={12} className="text-[#71717A]" />
        <span className="text-xs text-[#71717A]">{playlist.totalTracks} canciones</span>
      </div>

      {/* Ver Detalles */}
      <button
        onClick={(e) => { e.stopPropagation(); onViewDetails() }}
        className="flex w-full items-center justify-center gap-2 rounded-lg border border-[#27272A] bg-[#0A0A0A] px-5 py-2.5 text-sm font-medium text-white transition hover:bg-[#27272A]"
      >
        <Eye size={16} />
        Ver Detalles
      </button>

      {/* Action buttons */}
      <div className="flex w-full gap-2">
        <button
          onClick={(e) => { e.stopPropagation(); if (!refreshing) onUpdate() }}
          disabled={refreshing}
          className={`flex flex-1 items-center justify-center gap-2 rounded-lg px-3 py-2.5 text-sm font-medium transition ${
            refreshing
              ? 'bg-gradient-to-r from-green-500 to-blue-500 text-black cursor-wait'
              : 'text-green-500 hover:bg-green-500/15 active:scale-95 active:bg-green-500/25'
          }`}
        >
          <RefreshCw size={16} className={refreshing ? 'animate-spin' : ''} />
          {refreshing ? 'Actualizando...' : 'Actualizar'}
        </button>
        <button
          onClick={(e) => { e.stopPropagation(); onDelete() }}
          disabled={refreshing}
          className={`flex flex-1 items-center justify-center gap-2 rounded-lg px-3 py-2.5 text-sm font-medium text-red-500 transition ${
            refreshing ? 'opacity-40 cursor-not-allowed' : 'hover:bg-red-500/15 active:scale-95 active:bg-red-500/25'
          }`}
        >
          <Trash2 size={16} />
          Eliminar
        </button>
      </div>
    </div>
  )
}

export default SplitifyCard
