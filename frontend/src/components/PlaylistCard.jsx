import { Eye, Music, Heart, Clock, Check } from 'lucide-react'

const COVER_GRADIENTS = [
  'from-red-500 to-orange-500',
  'from-green-500 to-yellow-400',
  'from-cyan-500 to-purple-500',
  'from-amber-500 to-pink-500',
  'from-blue-500 to-indigo-600',
  'from-emerald-500 to-teal-400',
  'from-rose-500 to-fuchsia-500',
]

function PlaylistCard({ playlist, selected, onToggleSelect, onViewDetails }) {
  const isLikedSongs = playlist.spotifyId === 'liked_songs'

  // Deterministic gradient based on playlist id
  const gradientIndex = playlist.id % COVER_GRADIENTS.length
  const gradient = COVER_GRADIENTS[gradientIndex]

  return (
    <div
      className={`flex flex-col gap-3 rounded-xl p-4 transition cursor-pointer ${
        selected
          ? 'border-2 border-green-500 bg-[#1A1A1A]'
          : 'border border-[#27272A] bg-[#1A1A1A]'
      }`}
      onClick={onToggleSelect}
    >
      {/* Checkbox row */}
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
      {playlist.imageUrl ? (
        <img
          src={playlist.imageUrl}
          alt={playlist.name}
          className="aspect-square w-full rounded-lg object-cover"
        />
      ) : isLikedSongs ? (
        <div className="flex aspect-square w-full items-center justify-center rounded-lg bg-gradient-to-br from-[#4838C7] via-[#8B6CCF] to-[#7EC8CA]">
          <Heart size={48} fill="rgba(255,255,255,0.8)" stroke="none" />
        </div>
      ) : (
        <div className={`flex aspect-square w-full items-center justify-center rounded-lg bg-gradient-to-br ${gradient}`}>
          <Music size={48} className="text-white/80" />
        </div>
      )}

      {/* Name */}
      <p className="truncate text-sm font-semibold text-white">{playlist.name}</p>

      {/* Meta */}
      <div className="flex items-center gap-1">
        <Clock size={12} className="text-[#71717A]" />
        <span className="text-xs text-[#71717A]">{playlist.totalTracks} canciones</span>
      </div>

      {/* Button */}
      <button
        onClick={(e) => {
          e.stopPropagation()
          onViewDetails()
        }}
        className="flex w-full items-center justify-center gap-2 rounded-lg border border-[#27272A] bg-[#0A0A0A] px-5 py-2.5 text-sm font-medium text-white transition hover:bg-[#27272A]"
      >
        <Eye size={16} />
        Ver Detalles
      </button>
    </div>
  )
}

export default PlaylistCard
