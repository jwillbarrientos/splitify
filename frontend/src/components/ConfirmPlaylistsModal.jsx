import { useState, useEffect } from 'react'
import { X, Sparkles, ArrowLeft, Loader2, Pencil, Music, Calendar, Languages, Check, RotateCcw } from 'lucide-react'
import { previewOrganizedPlaylists, confirmOrganizedPlaylists } from '../services/api'

function iconForFilter(filterType) {
  if (filterType === 'language') return Languages
  if (filterType === 'genre') return Music
  return Calendar
}

function Checkbox({ checked, onClick }) {
  return (
    <button
      type="button"
      onClick={onClick}
      className={`flex h-5 w-5 shrink-0 items-center justify-center rounded-md transition ${
        checked
          ? 'bg-green-500'
          : 'border-2 border-[#27272A] bg-[#0A0A0A] hover:border-[#3f3f46]'
      }`}
    >
      {checked && <Check size={14} className="text-black" strokeWidth={3} />}
    </button>
  )
}

function ConfirmPlaylistsModal({ visible, onClose, onBack, selectedIds, options, onPlaylistsCreated, onEmpty }) {
  const [loading, setLoading] = useState(false)
  const [creating, setCreating] = useState(false)
  const [specs, setSpecs] = useState([])

  useEffect(() => {
    if (!visible) return
    setLoading(true)
    previewOrganizedPlaylists(selectedIds, options)
      .then(data => {
        const withSelection = (data || []).map(s => ({ ...s, defaultName: s.name, selected: true }))
        setSpecs(withSelection)
        if (!data || data.length === 0) {
          onEmpty?.()
        }
      })
      .catch(err => {
        console.error('Error loading preview:', err)
        setSpecs([])
      })
      .finally(() => setLoading(false))
  }, [visible, selectedIds, options, onEmpty])

  if (!visible) return null

  const handleChangeName = (idx, newName) => {
    setSpecs(prev => prev.map((s, i) => i === idx ? { ...s, name: newName } : s))
  }

  const handleToggleSelected = (idx) => {
    setSpecs(prev => prev.map((s, i) => i === idx ? { ...s, selected: !s.selected } : s))
  }

  const handleResetName = (idx) => {
    setSpecs(prev => prev.map((s, i) => i === idx ? { ...s, name: s.defaultName } : s))
  }

  const allSelected = specs.length > 0 && specs.every(s => s.selected)
  const handleToggleAll = () => {
    const next = !allSelected
    setSpecs(prev => prev.map(s => ({ ...s, selected: next })))
  }

  const selectedSpecs = specs.filter(s => s.selected)
  const canCreate =
    !creating &&
    selectedSpecs.length > 0 &&
    selectedSpecs.every(s => s.name && s.name.trim().length > 0)

  const handleConfirm = async () => {
    if (!canCreate) return
    setCreating(true)
    try {
      const cleaned = selectedSpecs.map(s => ({
        filterType: s.filterType,
        filterValue: s.filterValue,
        name: s.name.trim(),
      }))
      const created = await confirmOrganizedPlaylists(selectedIds, cleaned)
      onPlaylistsCreated(created)
    } catch (err) {
      console.error('Error creating playlists:', err)
    } finally {
      setCreating(false)
    }
  }

  return (
    <div className="fixed inset-0 z-50 flex items-center justify-center bg-[#0A0A0A]/80 p-4">
      <div className="relative flex w-[600px] max-w-full flex-col gap-5 rounded-2xl border border-[#27272A] bg-[#1A1A1A] p-6 shadow-[0_8px_32px_rgba(0,0,0,0.4)]">
        <button
          onClick={onClose}
          className="absolute right-6 top-6 text-[#A1A1AA] transition hover:text-white"
        >
          <X size={20} />
        </button>

        <div className="flex flex-col gap-1">
          <h2 className="text-lg font-bold text-white">Confirmar creación</h2>
          <p className="text-sm text-[#A1A1AA]">
            Selecciona las playlists que quieres crear. Puedes editar los nombres si quieres.
          </p>
        </div>

        {loading ? (
          <div className="flex h-40 items-center justify-center rounded-lg border border-[#27272A] bg-[#0A0A0A]">
            <Loader2 size={20} className="animate-spin text-green-500" />
            <span className="ml-2 text-sm text-[#A1A1AA]">Calculando playlists...</span>
          </div>
        ) : specs.length === 0 ? (
          <div className="flex h-40 items-center justify-center rounded-lg border border-[#27272A] bg-[#0A0A0A]">
            <span className="text-sm text-[#A1A1AA]">No hay canciones que coincidan con los criterios.</span>
          </div>
        ) : (
          <div className="flex flex-col gap-2">
            <div className="flex items-center justify-between px-1">
              <button
                type="button"
                onClick={handleToggleAll}
                className="flex items-center gap-2 text-[13px] text-[#A1A1AA] transition hover:text-white"
              >
                <Checkbox checked={allSelected} onClick={handleToggleAll} />
                <span>{allSelected ? 'Deseleccionar todas' : 'Seleccionar todas'}</span>
              </button>
              <span className="text-[13px] text-[#A1A1AA]">
                {selectedSpecs.length} de {specs.length} seleccionadas
              </span>
            </div>
            <div className="flex max-h-80 flex-col gap-2 overflow-y-auto overscroll-contain rounded-lg border border-[#27272A] bg-[#0A0A0A] p-3">
              {specs.map((spec, idx) => {
                const Icon = iconForFilter(spec.filterType)
                const isSelected = spec.selected
                return (
                  <div
                    key={`${spec.filterType}-${spec.filterValue ?? 'all'}-${idx}`}
                    className={`flex items-center gap-3 rounded-lg bg-[#141414] px-3 py-2.5 transition ${
                      isSelected ? '' : 'opacity-50'
                    }`}
                  >
                    <Checkbox checked={isSelected} onClick={() => handleToggleSelected(idx)} />
                    <Icon size={16} className="shrink-0 text-green-500" />
                    <div className="flex flex-1 items-center gap-2 rounded-md border border-[#27272A] bg-[#0A0A0A] px-3 py-2 focus-within:border-green-500/50">
                      <Pencil size={12} className="shrink-0 text-[#71717A]" />
                      <input
                        type="text"
                        value={spec.name}
                        onChange={e => handleChangeName(idx, e.target.value)}
                        disabled={!isSelected}
                        maxLength={100}
                        className="flex-1 bg-transparent text-sm text-white outline-none disabled:cursor-not-allowed"
                      />
                      {isSelected && spec.name !== spec.defaultName && (
                        <button
                          type="button"
                          onClick={() => handleResetName(idx)}
                          title={`Restablecer: "${spec.defaultName}"`}
                          className="shrink-0 rounded p-1 text-[#71717A] transition hover:bg-[#27272A] hover:text-green-500"
                        >
                          <RotateCcw size={12} />
                        </button>
                      )}
                    </div>
                  </div>
                )
              })}
            </div>
          </div>
        )}

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
            onClick={handleConfirm}
            disabled={!canCreate}
            className={`flex flex-1 items-center justify-center gap-2 rounded-lg bg-gradient-to-r from-green-500 to-blue-500 px-4 py-2.5 text-sm font-semibold text-black transition ${
              canCreate ? 'hover:opacity-90 cursor-pointer' : 'opacity-40 cursor-not-allowed'
            }`}
          >
            <Sparkles size={16} />
            {creating ? 'Creando playlists...' : 'Confirmar y Crear'}
          </button>
        </div>
      </div>
    </div>
  )
}

export default ConfirmPlaylistsModal
