import { Eye, Music } from 'lucide-react'

function PlaylistCard({ playlist, onViewDetails }) {
  return (
    <div className="flex flex-col gap-3 rounded-xl border border-[#27272A] bg-[#1A1A1A] p-4">
      {/* Cover */}
      {playlist.imageUrl ? (
        <img
          src={playlist.imageUrl}
          alt={playlist.name}
          className="h-[168px] w-full rounded-lg object-cover"
        />
      ) : (
        <div className="flex h-[168px] w-full items-center justify-center rounded-lg bg-gradient-to-br from-green-500 to-blue-500">
          <Music size={48} className="text-white" />
        </div>
      )}

      {/* Name */}
      <p className="truncate text-sm font-semibold text-white">{playlist.name}</p>

      {/* Meta */}
      <div className="flex items-center gap-1">
        <Music size={12} className="text-[#71717A]" />
        <span className="text-xs text-[#71717A]">{playlist.totalTracks} canciones</span>
      </div>

      {/* Button */}
      <button
        onClick={onViewDetails}
        className="flex w-full items-center justify-center gap-2 rounded-lg border border-[#27272A] bg-[#0A0A0A] px-5 py-2.5 text-sm font-medium text-white transition hover:bg-[#27272A]"
      >
        <Eye size={16} />
        Ver Detalles
      </button>
    </div>
  )
}

export default PlaylistCard
