import { useState, useEffect } from 'react'
import { X, Sparkles, ArrowLeft, Languages, Music, Mic, Pencil, Check, Loader2 } from 'lucide-react'
import { getAvailableFilters, createCustomPlaylist } from '../services/api'

function Checkbox({ checked }) {
  return (
    <div
      className={`flex h-4 w-4 shrink-0 items-center justify-center rounded transition ${
        checked ? 'bg-green-500' : 'border border-[#27272A] bg-[#0A0A0A]'
      }`}
    >
      {checked && <Check size={11} className="text-black" strokeWidth={3} />}
    </div>
  )
}

function FilterList({ icon: Icon, title, items, selected, onToggle }) {
  return (
    <div className="flex min-w-0 flex-1 flex-col gap-2">
      <div className="flex items-center gap-2">
        <Icon size={16} className="text-white" />
        <span className="text-sm font-bold text-white">{title}</span>
        <span className="text-xs text-[#A1A1AA]">({selected.size}/{items.length})</span>
      </div>
      <div className="flex h-40 flex-col overflow-y-auto overscroll-contain rounded-lg border border-[#27272A] bg-[#0A0A0A]">
        {items.length === 0 ? (
          <div className="flex h-full items-center justify-center px-4 text-center text-xs text-[#71717A]">
            Sin opciones disponibles
          </div>
        ) : (
          items.map(item => (
            <button
              key={item}
              type="button"
              onClick={() => onToggle(item)}
              className="flex items-center gap-3 px-4 py-2.5 text-left transition hover:bg-[#18181B]"
            >
              <Checkbox checked={selected.has(item)} />
              <span className="truncate text-sm text-white">{item}</span>
            </button>
          ))
        )}
      </div>
    </div>
  )
}

function CustomOrganizeModal({ visible, onClose, onBack, selectedIds, onPlaylistCreated, onError }) {
  const [loading, setLoading] = useState(false)
  const [creating, setCreating] = useState(false)
  const [filters, setFilters] = useState({ languages: [], genres: [], artists: [] })
  const [selectedLanguages, setSelectedLanguages] = useState(new Set())
  const [selectedGenres, setSelectedGenres] = useState(new Set())
  const [selectedArtists, setSelectedArtists] = useState(new Set())
  const [playlistName, setPlaylistName] = useState('')

  useEffect(() => {
    if (!visible) return
    setLoading(true)
    setSelectedLanguages(new Set())
    setSelectedGenres(new Set())
    setSelectedArtists(new Set())
    setPlaylistName('')
    getAvailableFilters(selectedIds)
      .then(data => setFilters({
        languages: data.languages || [],
        genres: data.genres || [],
        artists: data.artists || [],
      }))
      .catch(err => {
        console.error('Error loading available filters:', err)
        setFilters({ languages: [], genres: [], artists: [] })
      })
      .finally(() => setLoading(false))
  }, [visible, selectedIds])

  if (!visible) return null

  const toggleIn = (setter) => (value) => {
    setter(prev => {
      const next = new Set(prev)
      next.has(value) ? next.delete(value) : next.add(value)
      return next
    })
  }

  const anyFilterSelected =
    selectedLanguages.size > 0 || selectedGenres.size > 0 || selectedArtists.size > 0
  const canCreate = anyFilterSelected && playlistName.trim().length > 0 && !creating

  const handleCreate = async () => {
    if (!canCreate) return
    setCreating(true)
    try {
      const created = await createCustomPlaylist({
        playlistIds: selectedIds,
        languages: Array.from(selectedLanguages),
        genres: Array.from(selectedGenres),
        artists: Array.from(selectedArtists),
        name: playlistName.trim(),
      })
      onPlaylistCreated(created)
    } catch (err) {
      console.error('Error creating custom playlist:', err)
      onError?.(err.message || 'No se pudo crear la playlist personalizada.')
    } finally {
      setCreating(false)
    }
  }

  return (
    <div className="fixed inset-0 z-50 flex items-center justify-center bg-[#0A0A0A]/80 p-4">
      <div className="relative flex w-[600px] max-w-full flex-col gap-5 rounded-2xl border border-[#27272A] bg-[#1A1A1A] p-6 shadow-[0_8px_32px_rgba(0,0,0,0.4)]">
        {/* Close */}
        <button
          onClick={onClose}
          className="absolute right-6 top-6 text-[#A1A1AA] transition hover:text-white"
        >
          <X size={20} />
        </button>

        {/* Header */}
        <div className="flex flex-col gap-1">
          <h2 className="text-lg font-bold text-white">Organizar Música</h2>
          <p className="text-sm text-[#A1A1AA]">
            Selecciona los idiomas, géneros y artistas para tu playlist personalizada
          </p>
        </div>

        {/* Nombre de playlist */}
        <div className="flex flex-col gap-2">
          <label className="text-sm font-semibold text-white">Nombre de la playlist</label>
          <div className="flex items-center gap-2 rounded-lg border border-[#27272A] bg-[#0A0A0A] px-4 py-3">
            <Pencil size={16} className="text-[#71717A]" />
            <input
              type="text"
              value={playlistName}
              onChange={e => setPlaylistName(e.target.value)}
              placeholder="Mi playlist personalizada..."
              maxLength={100}
              className="flex-1 bg-transparent text-sm text-white placeholder-[#71717A] outline-none"
            />
          </div>
        </div>

        {/* Filtros dinámicos */}
        {loading ? (
          <div className="flex h-40 items-center justify-center rounded-lg border border-[#27272A] bg-[#0A0A0A]">
            <Loader2 size={20} className="animate-spin text-green-500" />
            <span className="ml-2 text-sm text-[#A1A1AA]">Cargando filtros disponibles...</span>
          </div>
        ) : (
          <div className="flex gap-4">
            <FilterList
              icon={Languages}
              title="Idiomas"
              items={filters.languages}
              selected={selectedLanguages}
              onToggle={toggleIn(setSelectedLanguages)}
            />
            <FilterList
              icon={Music}
              title="Géneros"
              items={filters.genres}
              selected={selectedGenres}
              onToggle={toggleIn(setSelectedGenres)}
            />
            <FilterList
              icon={Mic}
              title="Artistas"
              items={filters.artists}
              selected={selectedArtists}
              onToggle={toggleIn(setSelectedArtists)}
            />
          </div>
        )}

        {/* Botones */}
        <div className="flex gap-3">
          <button
            onClick={onBack}
            disabled={creating}
            className="flex flex-1 items-center justify-center gap-2 rounded-lg border border-[#27272A] bg-[#0A0A0A] px-4 py-2.5 text-sm font-medium text-white transition hover:bg-[#27272A] disabled:opacity-50"
          >
            <ArrowLeft size={16} />
            Volver
          </button>
          <button
            onClick={handleCreate}
            disabled={!canCreate}
            className={`flex flex-1 items-center justify-center gap-2 rounded-lg bg-gradient-to-r from-green-500 to-blue-500 px-4 py-2.5 text-sm font-semibold text-black transition ${
              canCreate ? 'hover:opacity-90 cursor-pointer' : 'opacity-40 cursor-not-allowed'
            }`}
          >
            <Sparkles size={16} />
            {creating ? 'Creando playlist...' : 'Crear Playlist'}
          </button>
        </div>
      </div>
    </div>
  )
}

export default CustomOrganizeModal
