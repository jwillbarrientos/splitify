import { useState, useMemo } from 'react'
import { X, RotateCcw, Trash2, AlertTriangle, ChevronDown, ChevronRight, Check, CheckCheck, ListMusic } from 'lucide-react'

// Estructura esperada de `groups`: array de
//   { playlistId, playlistName, songs: [{ spotifyId, name, artist, ... }] }
//
// mode: 'restore' (default) o 'remove'.
//   - restore: canciones que el usuario quitó de Spotify pero siguen en el origen;
//     pregunta si restaurarlas. Por defecto TODAS marcadas.
//   - remove: canciones que siguen en el hijo pero su origen desapareció;
//     pregunta si quitarlas del hijo también. Por defecto NINGUNA marcada.
//
// onConfirm(selectionByPlaylist) donde selectionByPlaylist es Map playlistId -> Array<spotifyId>
// onCancel: cierra el modal sin hacer nada.

function Checkbox({ checked, indeterminate, onClick, size = 16 }) {
  return (
    <button
      type="button"
      onClick={(e) => { e.stopPropagation(); onClick?.(e) }}
      className={`flex shrink-0 items-center justify-center rounded transition ${
        checked || indeterminate
          ? 'bg-green-500'
          : 'border border-[#3f3f46] bg-[#0A0A0A] hover:border-[#52525b]'
      }`}
      style={{ width: size, height: size }}
    >
      {checked && <Check size={size - 4} className="text-black" strokeWidth={3} />}
      {!checked && indeterminate && <div className="h-[2px] w-[8px] rounded bg-black" />}
    </button>
  )
}

function RefreshConfirmModal({ visible, groups, mode = 'restore', onConfirm, onCancel }) {
  const isRemoveMode = mode === 'remove'

  // selected: Map playlistId -> Set de spotifyIds seleccionados
  const [selected, setSelected] = useState({})
  const [expanded, setExpanded] = useState({})
  // syncedKey captura qué conjunto de grupos ya inicializamos. Cuando cambia (abrir/cambiar modal),
  // reinicializamos selected/expanded durante el render. Evita setState-in-effect.
  const [syncedKey, setSyncedKey] = useState(null)

  const totalSongs = useMemo(
    () => groups.reduce((acc, g) => acc + g.songs.length, 0),
    [groups]
  )
  const totalSelected = useMemo(
    () => Object.values(selected).reduce((acc, s) => acc + (s ? s.size : 0), 0),
    [selected]
  )

  // Key derivada del contenido de los grupos + mode. Cuando cambia, reseteamos el state.
  const currentKey = visible ? `${mode}:${groups.map(g => g.playlistId).join('-')}` : null
  if (currentKey !== syncedKey) {
    setSyncedKey(currentKey)
    if (currentKey) {
      // restore: TODAS marcadas por defecto (restaurar todo es lo más común).
      // remove: NINGUNA marcada por defecto (conservar todo, quitar solo lo que el usuario marque).
      const initial = {}
      const initialExpanded = {}
      groups.forEach(g => {
        initial[g.playlistId] = isRemoveMode ? new Set() : new Set(g.songs.map(s => s.spotifyId))
        initialExpanded[g.playlistId] = groups.length === 1
      })
      setSelected(initial)
      setExpanded(initialExpanded)
    } else {
      setSelected({})
      setExpanded({})
    }
  }

  if (!visible) return null

  const toggleSong = (playlistId, spotifyId) => {
    setSelected(prev => {
      const next = { ...prev }
      const set = new Set(next[playlistId] || [])
      if (set.has(spotifyId)) set.delete(spotifyId)
      else set.add(spotifyId)
      next[playlistId] = set
      return next
    })
  }

  const togglePlaylistAll = (playlistId, songs) => {
    setSelected(prev => {
      const next = { ...prev }
      const current = next[playlistId] || new Set()
      if (current.size === songs.length) {
        next[playlistId] = new Set() // deseleccionar todo
      } else {
        next[playlistId] = new Set(songs.map(s => s.spotifyId)) // seleccionar todo
      }
      return next
    })
  }

  const toggleGlobalAll = () => {
    const allSelected = totalSelected === totalSongs
    const next = {}
    groups.forEach(g => {
      next[g.playlistId] = allSelected ? new Set() : new Set(g.songs.map(s => s.spotifyId))
    })
    setSelected(next)
  }

  const toggleExpanded = (playlistId) => {
    setExpanded(prev => ({ ...prev, [playlistId]: !prev[playlistId] }))
  }

  const handleConfirm = () => {
    // Convertir Map de Sets a Map de Arrays para la API
    const payload = {}
    Object.entries(selected).forEach(([pid, set]) => {
      payload[pid] = Array.from(set)
    })
    onConfirm(payload)
  }

  const handleSelectNone = () => {
    // restore: "No restaurar ninguna" (equivalente a "Solo nuevas")
    // remove: "No quitar ninguna" (conservar todas)
    const payload = {}
    groups.forEach(g => { payload[g.playlistId] = [] })
    onConfirm(payload)
  }

  const isBatch = groups.length > 1
  const allChecked = totalSelected === totalSongs
  const someChecked = totalSelected > 0 && !allChecked

  return (
    <div className="fixed inset-0 z-50 flex items-center justify-center bg-[#0A0A0A]/80 p-4">
      <div className="relative flex w-[640px] max-w-full flex-col rounded-2xl border border-[#27272A] bg-[#1A1A1A] shadow-[0_8px_32px_rgba(0,0,0,0.4)]" style={{ maxHeight: '85vh' }}>
        {/* Close button */}
        <button
          onClick={onCancel}
          className="absolute right-5 top-5 text-[#A1A1AA] transition hover:text-white"
        >
          <X size={20} />
        </button>

        {/* Header (fijo) */}
        <div className="flex items-start gap-3 border-b border-[#27272A] p-4 pr-12">
          <div className="flex h-10 w-10 shrink-0 items-center justify-center rounded-full bg-amber-500/10">
            <AlertTriangle size={20} className="text-amber-400" />
          </div>
          <div className="flex min-w-0 flex-1 flex-col gap-1">
            <h2 className="text-lg font-bold text-white">
              {isRemoveMode ? 'Canciones sin origen detectadas' : 'Canciones eliminadas detectadas'}
            </h2>
            <p className="text-sm text-[#A1A1AA]">
              {isRemoveMode ? (
                isBatch ? (
                  <>Detectamos <span className="font-semibold text-white">{totalSongs}</span> canciones en <span className="font-semibold text-white">{groups.length}</span> playlists cuyo origen las quitó. Por defecto se conservan — marca las que quieras quitar del hijo también.</>
                ) : (
                  <>Detectamos <span className="font-semibold text-white">{totalSongs}</span> canciones en esta playlist cuyo origen las quitó. Por defecto se conservan — marca las que quieras quitar del hijo también.</>
                )
              ) : (
                isBatch ? (
                  <>Detectamos <span className="font-semibold text-white">{totalSongs}</span> canciones eliminadas manualmente en <span className="font-semibold text-white">{groups.length}</span> playlists que siguen en las playlists origen. Elige cuáles quieres restaurar.</>
                ) : (
                  <>Detectamos <span className="font-semibold text-white">{totalSongs}</span> canciones eliminadas manualmente que siguen en las playlists origen. Elige cuáles quieres restaurar.</>
                )
              )}
            </p>
          </div>
        </div>

        {/* Barra contador + "Seleccionar todo" (fija, arriba de la lista) */}
        <div className="flex items-center justify-between gap-3 border-b border-[#27272A] px-4 py-3">
          <span className="text-xs text-[#A1A1AA]">
            {totalSelected} de {totalSongs} seleccionadas
          </span>
          <button
            onClick={toggleGlobalAll}
            className="flex shrink-0 items-center gap-2 rounded-lg border border-[#27272A] bg-[#0A0A0A] px-3 py-1.5 text-xs font-medium text-white transition hover:bg-[#27272A]"
          >
            <CheckCheck size={14} />
            {allChecked ? 'Deseleccionar todo' : 'Seleccionar todo'}
          </button>
        </div>

        {/* Lista (scrollable, overscroll-contain para no propagar) */}
        <div
          className="flex-1 overflow-y-auto overscroll-contain p-4"
          onWheel={(e) => e.stopPropagation()}
        >
          <div className="flex flex-col gap-2">
            {groups.map(group => {
              const selectedSet = selected[group.playlistId] || new Set()
              const allOfPlaylist = selectedSet.size === group.songs.length
              const someOfPlaylist = selectedSet.size > 0 && !allOfPlaylist
              const isExpanded = expanded[group.playlistId]

              return (
                <div
                  key={group.playlistId}
                  className="overflow-hidden rounded-lg border border-[#27272A] bg-[#0A0A0A]"
                >
                  {/* Header de la playlist */}
                  <div className="flex items-center gap-2 px-3 py-2.5">
                    <Checkbox
                      checked={allOfPlaylist}
                      indeterminate={someOfPlaylist}
                      onClick={() => togglePlaylistAll(group.playlistId, group.songs)}
                    />
                    <button
                      onClick={() => toggleExpanded(group.playlistId)}
                      className="flex flex-1 items-center gap-2 text-left transition hover:text-green-400"
                    >
                      {isExpanded ? (
                        <ChevronDown size={14} className="text-[#71717A]" />
                      ) : (
                        <ChevronRight size={14} className="text-[#71717A]" />
                      )}
                      <ListMusic size={14} className="text-green-500" />
                      <span className="flex-1 truncate text-sm font-semibold text-white">
                        {group.playlistName}
                      </span>
                      <span className="shrink-0 rounded-full bg-[#18181B] px-2 py-0.5 text-[11px] text-[#A1A1AA]">
                        {selectedSet.size} / {group.songs.length}
                      </span>
                    </button>
                  </div>

                  {/* Canciones (colapsable) */}
                  {isExpanded && (
                    <div className="flex flex-col border-t border-[#18181B]">
                      {group.songs.map(song => {
                        const checked = selectedSet.has(song.spotifyId)
                        const toggle = () => toggleSong(group.playlistId, song.spotifyId)
                        return (
                          <div
                            key={song.spotifyId}
                            role="button"
                            tabIndex={0}
                            onClick={toggle}
                            onKeyDown={(e) => { if (e.key === 'Enter' || e.key === ' ') { e.preventDefault(); toggle() } }}
                            className="flex cursor-pointer items-center gap-3 border-b border-[#18181B] px-4 py-2.5 text-left transition last:border-b-0 hover:bg-[#141414]"
                          >
                            <Checkbox checked={checked} onClick={toggle} />
                            <div className="flex min-w-0 flex-1 flex-col">
                              <span className="truncate text-sm text-white">{song.name}</span>
                              <span className="truncate text-xs text-[#71717A]">{song.artist}</span>
                            </div>
                          </div>
                        )
                      })}
                    </div>
                  )}
                </div>
              )
            })}
          </div>
        </div>

        {/* Footer (fijo) */}
        <div className="flex items-center justify-between gap-2 border-t border-[#27272A] p-4">
          <button
            onClick={onCancel}
            className="rounded-lg border border-[#27272A] bg-[#0A0A0A] px-4 py-2.5 text-sm font-medium text-[#A1A1AA] transition hover:border-[#3f3f46] hover:bg-[#18181B] hover:text-white"
          >
            Cancelar
          </button>
          <div className="flex items-center gap-2">
            <button
              onClick={handleSelectNone}
              className={`rounded-lg border px-4 py-2.5 text-sm font-medium transition ${
                isRemoveMode
                  ? 'border-green-900/50 bg-[#0A0A0A] text-green-400 hover:border-green-500/70 hover:bg-green-500/20 hover:text-green-300'
                  : 'border-red-900/50 bg-[#0A0A0A] text-red-400 hover:border-red-500/70 hover:bg-red-500/20 hover:text-red-300'
              }`}
            >
              {isRemoveMode ? 'No quitar ninguna' : 'No restaurar ninguna'}
            </button>
            {isRemoveMode ? (
              <button
                onClick={handleConfirm}
                disabled={totalSelected === 0}
                className={`flex items-center gap-2 rounded-lg bg-red-500 px-5 py-2.5 text-sm font-semibold text-white transition ${
                  totalSelected > 0 ? 'cursor-pointer hover:bg-red-600' : 'cursor-not-allowed opacity-40'
                }`}
              >
                <Trash2 size={16} />
                Quitar seleccionadas del hijo ({totalSelected})
              </button>
            ) : (
              <button
                onClick={handleConfirm}
                disabled={totalSelected === 0}
                className={`flex items-center gap-2 rounded-lg bg-gradient-to-r from-green-500 to-blue-500 px-5 py-2.5 text-sm font-semibold text-black transition ${
                  totalSelected > 0 ? 'cursor-pointer hover:opacity-90' : 'cursor-not-allowed opacity-40'
                }`}
              >
                <RotateCcw size={16} />
                Restaurar seleccionadas ({totalSelected})
              </button>
            )}
          </div>
        </div>
      </div>
    </div>
  )
}

export default RefreshConfirmModal
