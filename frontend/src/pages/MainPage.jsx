import { useState, useEffect } from 'react'
import { useNavigate } from 'react-router-dom'
import { Music, Sparkles, RefreshCw, FolderOpen, Trash2, CheckCheck } from 'lucide-react'
import { getPlaylists, syncPlaylists, deletePlaylists, getSplitifyPlaylists, deleteSplitifyPlaylist, deleteSplitifyPlaylists, previewRefresh, refreshSplitifyPlaylist, refreshSplitifyPlaylists, previewBatchRefresh, renameSplitifyPlaylist, updateSplitifyPlaylistImage } from '../services/api'
import SyncOverlay from '../components/SyncOverlay'
import PlaylistCard from '../components/PlaylistCard'
import SplitifyCard from '../components/SplitifyCard'
import OrganizeModal from '../components/OrganizeModal'
import ConfirmPlaylistsModal from '../components/ConfirmPlaylistsModal'
import CustomOrganizeModal from '../components/CustomOrganizeModal'
import RefreshConfirmModal from '../components/RefreshConfirmModal'
import ErrorModal from '../components/ErrorModal'

function EmptyState({ icon: Icon, title, description, buttonLabel, buttonIcon: BtnIcon, onButtonClick }) {
  return (
    <div className="flex flex-col items-center justify-center gap-4 rounded-xl border border-zinc-800 bg-[#1A1A1A] px-6 py-12">
      <Icon size={48} className="text-zinc-600" />
      <div className="flex flex-col items-center gap-1">
        <p className="text-sm font-semibold text-white">{title}</p>
        <p className="text-center text-xs text-zinc-500">{description}</p>
      </div>
      {buttonLabel && (
        <button
          onClick={onButtonClick}
          className="flex items-center gap-2 rounded-lg bg-gradient-to-r from-green-500 to-blue-500 px-4 py-2.5 text-sm font-semibold text-black transition hover:opacity-90"
        >
          {BtnIcon && <BtnIcon size={16} />}
          {buttonLabel}
        </button>
      )}
    </div>
  )
}

function MainPage() {
  const navigate = useNavigate()
  const [playlists, setPlaylists] = useState([])
  const [splitifyList, setSplitifyList] = useState([])
  const [loading, setLoading] = useState(true)
  const [syncing, setSyncing] = useState(false)
  const [selectedIds, setSelectedIds] = useState(new Set())
  const [splitifySelectedIds, setSplitifySelectedIds] = useState(new Set())
  const [showOrganizeModal, setShowOrganizeModal] = useState(false)
  const [confirmOptions, setConfirmOptions] = useState(null) // options {idioma,genero,fecha} cuando pasas al confirm modal
  const [showCustomModal, setShowCustomModal] = useState(false)
  const [errorModal, setErrorModal] = useState(null) // { title, message, tip }
  const [refreshingIds, setRefreshingIds] = useState(new Set())
  const [refreshingBatch, setRefreshingBatch] = useState(false)

  const addRefreshing = (id) => setRefreshingIds(prev => {
    const next = new Set(prev)
    next.add(id)
    return next
  })
  const removeRefreshing = (id) => setRefreshingIds(prev => {
    const next = new Set(prev)
    next.delete(id)
    return next
  })

  useEffect(() => {
    Promise.all([getPlaylists(), getSplitifyPlaylists()])
      .then(([data, splitifyData]) => {
        setPlaylists(data)
        setSplitifyList(splitifyData)
      })
      .catch(() => {
        setPlaylists([])
        setSplitifyList([])
      })
      .finally(() => setLoading(false))
  }, [])

  const handleSync = async () => {
    setSyncing(true)
    try {
      const data = await syncPlaylists()
      const splitifyData = await getSplitifyPlaylists()
      setPlaylists(data)
      setSplitifyList(splitifyData)
      setSelectedIds(new Set())
      setSplitifySelectedIds(new Set())
    } catch (err) {
      console.error('Error syncing playlists:', err)
    } finally {
      setSyncing(false)
    }
  }

  const handleDelete = async () => {
    try {
      await deletePlaylists()
      setPlaylists([])
      setSelectedIds(new Set())
    } catch (err) {
      console.error('Error deleting playlists:', err)
    }
  }

  const toggleSelect = (id) => {
    setSelectedIds(prev => {
      const next = new Set(prev)
      next.has(id) ? next.delete(id) : next.add(id)
      return next
    })
  }

  const toggleSplitifySelect = (id) => {
    setSplitifySelectedIds(prev => {
      const next = new Set(prev)
      next.has(id) ? next.delete(id) : next.add(id)
      return next
    })
  }

  const allSpotifySelected = playlists.length > 0 && selectedIds.size === playlists.length
  const allSplitifySelected = splitifyList.length > 0 && splitifySelectedIds.size === splitifyList.length

  const handleSelectAll = () => {
    setSelectedIds(allSpotifySelected ? new Set() : new Set(playlists.map(p => p.id)))
  }

  const handleSplitifySelectAll = () => {
    setSplitifySelectedIds(allSplitifySelected ? new Set() : new Set(splitifyList.map(p => p.id)))
  }

  const handlePlaylistsCreated = (newPlaylists) => {
    if (newPlaylists && newPlaylists.length > 0) {
      setSplitifyList(prev => [...prev, ...newPlaylists])
    }
    setSelectedIds(new Set())
    setConfirmOptions(null)
  }

  const handleContinueToConfirm = (options) => {
    setShowOrganizeModal(false)
    setConfirmOptions(options)
  }

  const handleBackToOrganizeFromConfirm = () => {
    setConfirmOptions(null)
    setShowOrganizeModal(true)
  }

  const handleCloseConfirm = () => {
    setConfirmOptions(null)
  }

  const handleConfirmEmpty = () => {
    setConfirmOptions(null)
    setErrorModal({
      title: 'Sin resultados',
      message: 'No hay canciones que coincidan con los criterios seleccionados.',
      tip: 'Prueba seleccionar otras opciones o más playlists origen.',
    })
  }

  const handleOpenCustom = () => {
    setShowOrganizeModal(false)
    setShowCustomModal(true)
  }

  const handleBackToOrganize = () => {
    setShowCustomModal(false)
    setShowOrganizeModal(true)
  }

  const handleCustomPlaylistCreated = (newPlaylist) => {
    if (newPlaylist) {
      setSplitifyList(prev => [...prev, newPlaylist])
    }
    setSelectedIds(new Set())
    setShowCustomModal(false)
  }

  const handleCustomError = (message) => {
    setShowCustomModal(false)
    setErrorModal({
      title: 'Error al crear playlist',
      message: message || 'No se pudieron encontrar canciones que coincidan con los criterios seleccionados para crear la playlist solicitada.',
      tip: 'Intenta ampliar tus criterios de búsqueda o seleccionar diferentes opciones de idioma, género o artista.',
    })
  }

  const handleDeleteSplitify = async (id) => {
    try {
      await deleteSplitifyPlaylist(id)
      setSplitifyList(prev => prev.filter(p => p.id !== id))
      setSplitifySelectedIds(prev => {
        const next = new Set(prev)
        next.delete(id)
        return next
      })
    } catch (err) {
      console.error('Error deleting splitify playlist:', err)
    }
  }

  const handleDeleteSplitifySelected = async () => {
    const ids = Array.from(splitifySelectedIds)
    try {
      await deleteSplitifyPlaylists(ids)
      setSplitifyList(prev => prev.filter(p => !splitifySelectedIds.has(p.id)))
      setSplitifySelectedIds(new Set())
    } catch (err) {
      console.error('Error deleting selected splitify playlists:', err)
    }
  }

  // refreshConfirm: { mode: 'single'|'batch', groups: [{playlistId, playlistName, songs}], pendingIds: [...] }
  const [refreshConfirm, setRefreshConfirm] = useState(null)

  const applyRefreshResult = (id, updated) => {
    if (updated === null) {
      setSplitifyList(prev => prev.filter(p => p.id !== id))
    } else {
      setSplitifyList(prev => prev.map(p => p.id === id ? updated : p))
    }
  }

  const handleRefreshSplitify = async (id) => {
    if (refreshingIds.has(id)) return
    addRefreshing(id)
    try {
      const preview = await previewRefresh(id)
      if (preview.removedByUser && preview.removedByUser.length > 0) {
        const playlist = splitifyList.find(p => p.id === id)
        setRefreshConfirm({
          mode: 'single',
          groups: [{
            playlistId: id,
            playlistName: playlist?.name || 'Playlist',
            songs: preview.removedByUser,
          }],
          pendingIds: [id],
        })
      } else {
        const updated = await refreshSplitifyPlaylist(id, [])
        applyRefreshResult(id, updated)
        removeRefreshing(id)
      }
    } catch (err) {
      console.error('Error refreshing splitify playlist:', err)
      removeRefreshing(id)
    }
  }

  const handleConfirmRefresh = async (selectionByPlaylist) => {
    if (!refreshConfirm) return
    const { mode, pendingIds } = refreshConfirm
    setRefreshConfirm(null)

    try {
      if (mode === 'single') {
        const id = pendingIds[0]
        const restored = selectionByPlaylist[id] || []
        try {
          const updated = await refreshSplitifyPlaylist(id, restored)
          applyRefreshResult(id, updated)
        } finally {
          removeRefreshing(id)
        }
      } else {
        // Batch: las playlists sin conflictos mandan [] (no restaurar nada).
        const items = pendingIds.map(pid => ({
          playlistId: pid,
          restoredSongIds: selectionByPlaylist[pid] || [],
        }))
        try {
          const updated = await refreshSplitifyPlaylists(items)
          const updatedIds = new Set(updated.map(p => p.id))
          setSplitifyList(prev => {
            const stillThere = prev.filter(p => !pendingIds.includes(p.id) || updatedIds.has(p.id))
            return stillThere.map(p => {
              const match = updated.find(u => u.id === p.id)
              return match || p
            })
          })
          setSplitifySelectedIds(new Set())
        } finally {
          setRefreshingIds(prev => {
            const next = new Set(prev)
            pendingIds.forEach(i => next.delete(i))
            return next
          })
          setRefreshingBatch(false)
        }
      }
    } catch (err) {
      console.error('Error refreshing splitify playlist(s):', err)
    }
  }

  const handleCancelRefreshConfirm = () => {
    if (refreshConfirm) {
      const { mode, pendingIds } = refreshConfirm
      setRefreshingIds(prev => {
        const next = new Set(prev)
        pendingIds.forEach(i => next.delete(i))
        return next
      })
      setRefreshingBatch(false)
      if (mode === 'batch') {
        setSplitifySelectedIds(new Set())
      }
    }
    setRefreshConfirm(null)
  }

  const handleRenameSplitify = async (id, newName) => {
    const updated = await renameSplitifyPlaylist(id, newName)
    setSplitifyList(prev => prev.map(p => p.id === id ? updated : p))
  }

  const handleUpdateSplitifyImage = async (id, base64) => {
    const updated = await updateSplitifyPlaylistImage(id, base64)
    setSplitifyList(prev => prev.map(p => p.id === id ? updated : p))
  }

  const handleRefreshSplitifySelected = async () => {
    if (refreshingBatch || splitifySelectedCount === 0) return
    const ids = Array.from(splitifySelectedIds)
    setRefreshingBatch(true)
    setRefreshingIds(prev => {
      const next = new Set(prev)
      ids.forEach(i => next.add(i))
      return next
    })
    try {
      const previews = await previewBatchRefresh(ids)
      // Construir grupos solo para playlists con conflictos (canciones eliminadas)
      const groups = previews
        .filter(bp => bp.preview.removedByUser && bp.preview.removedByUser.length > 0)
        .map(bp => ({
          playlistId: bp.playlistId,
          playlistName: bp.playlistName,
          songs: bp.preview.removedByUser,
        }))

      if (groups.length > 0) {
        // Hay conflictos en al menos una playlist → mostrar modal.
        // pendingIds incluye TODAS las del batch (las sin conflictos igual se refrescan sin restaurar)
        setRefreshConfirm({ mode: 'batch', groups, pendingIds: ids })
      } else {
        // Sin conflictos → refresh directo del batch
        const items = ids.map(pid => ({ playlistId: pid, restoredSongIds: [] }))
        const updated = await refreshSplitifyPlaylists(items)
        const updatedIds = new Set(updated.map(p => p.id))
        setSplitifyList(prev => {
          const remaining = prev.filter(p => !splitifySelectedIds.has(p.id) || updatedIds.has(p.id))
          return remaining.map(p => {
            const match = updated.find(u => u.id === p.id)
            return match || p
          })
        })
        setSplitifySelectedIds(new Set())
        setRefreshingBatch(false)
        setRefreshingIds(prev => {
          const next = new Set(prev)
          ids.forEach(i => next.delete(i))
          return next
        })
      }
    } catch (err) {
      console.error('Error refreshing selected splitify playlists:', err)
      setRefreshingBatch(false)
      setRefreshingIds(prev => {
        const next = new Set(prev)
        ids.forEach(i => next.delete(i))
        return next
      })
    }
  }

  const selectedCount = selectedIds.size
  const splitifySelectedCount = splitifySelectedIds.size

  return (
    <>
      <SyncOverlay visible={syncing} />
      <OrganizeModal
        visible={showOrganizeModal}
        onClose={() => setShowOrganizeModal(false)}
        onContinueToConfirm={handleContinueToConfirm}
        onOpenCustom={handleOpenCustom}
      />
      <ConfirmPlaylistsModal
        visible={confirmOptions !== null}
        onClose={handleCloseConfirm}
        onBack={handleBackToOrganizeFromConfirm}
        selectedIds={Array.from(selectedIds)}
        options={confirmOptions || { idioma: false, genero: false, fecha: false }}
        onPlaylistsCreated={handlePlaylistsCreated}
        onEmpty={handleConfirmEmpty}
      />
      <CustomOrganizeModal
        visible={showCustomModal}
        onClose={() => setShowCustomModal(false)}
        onBack={handleBackToOrganize}
        selectedIds={Array.from(selectedIds)}
        onPlaylistCreated={handleCustomPlaylistCreated}
        onError={handleCustomError}
      />
      <ErrorModal
        visible={errorModal !== null}
        title={errorModal?.title}
        message={errorModal?.message}
        tip={errorModal?.tip}
        onClose={() => setErrorModal(null)}
      />

      <RefreshConfirmModal
        visible={refreshConfirm !== null}
        groups={refreshConfirm?.groups || []}
        onConfirm={handleConfirmRefresh}
        onCancel={handleCancelRefreshConfirm}
      />

      <main className="flex flex-col gap-6 p-8">
        {/* Sección: Tus Playlists de Spotify */}
        <section className="flex flex-col gap-5">
          <div className="flex items-start justify-between">
            <div className="flex flex-col gap-1">
              <h2 className="text-2xl font-bold">Tus Playlists de Spotify</h2>
              <p className="text-sm text-zinc-400">
                {playlists.length > 0
                  ? 'Selecciona las playlists que quieres organizar con Splitify'
                  : 'Sincroniza tus playlists de Spotify para empezar a organizarlas'}
              </p>
            </div>
            <div className="flex items-center gap-3">
              {playlists.length > 0 && (
                <>
                  <button
                    onClick={handleDelete}
                    className="flex items-center gap-2 rounded-lg border border-red-900/50 bg-[#0A0A0A] px-4 py-2.5 text-sm font-medium text-red-400 transition hover:bg-red-500/20 hover:border-red-500/70 hover:text-red-300"
                  >
                    <Trash2 size={16} />
                    Borrar datos
                  </button>
                  <button
                    onClick={handleSync}
                    className="flex items-center gap-2 rounded-lg border border-green-900/50 bg-[#0A0A0A] px-4 py-2.5 text-sm font-medium text-green-400 transition hover:bg-green-500/20 hover:border-green-500/70 hover:text-green-300"
                  >
                    <RefreshCw size={16} />
                    Re-sincronizar
                  </button>
                  <button
                    onClick={handleSelectAll}
                    className="flex items-center gap-2 rounded-lg border border-[#27272A] bg-[#0A0A0A] px-4 py-2.5 text-sm font-medium text-white transition hover:bg-[#27272A]"
                  >
                    <CheckCheck size={16} />
                    {allSpotifySelected ? 'Deseleccionar todo' : 'Seleccionar todo'}
                  </button>
                </>
              )}
              <button
                onClick={() => selectedCount > 0 && setShowOrganizeModal(true)}
                className={`flex items-center gap-2 rounded-lg px-4 py-2.5 text-sm font-semibold transition ${
                  selectedCount > 0
                    ? 'bg-gradient-to-r from-green-500 to-blue-500 text-black hover:opacity-90 cursor-pointer'
                    : 'bg-gradient-to-r from-green-500 to-blue-500 text-black opacity-40 cursor-not-allowed'
                }`}
              >
                <Sparkles size={16} />
                Organizar ({selectedCount})
              </button>
            </div>
          </div>

          {loading ? null : playlists.length === 0 ? (
            <EmptyState
              icon={Music}
              title="Aún no hay playlists sincronizadas"
              description='Haz clic en "Sincronizar Playlists" para traer tus playlists de Spotify'
              buttonLabel="Sincronizar Playlists"
              buttonIcon={RefreshCw}
              onButtonClick={handleSync}
            />
          ) : (
            <div className="grid gap-5" style={{ gridTemplateColumns: 'repeat(auto-fill, minmax(240px, 1fr))' }}>
              {playlists.map(playlist => (
                <PlaylistCard
                  key={playlist.id}
                  playlist={playlist}
                  selected={selectedIds.has(playlist.id)}
                  onToggleSelect={() => toggleSelect(playlist.id)}
                  onViewDetails={() => navigate(`/playlist/${playlist.id}`, { state: { playlist } })}
                />
              ))}
            </div>
          )}
        </section>

        {/* Divider */}
        <hr className="border-zinc-800" />

        {/* Sección: Playlists de Splitify */}
        <section className="flex flex-col gap-5">
          <div className="flex items-start justify-between">
            <div className="flex flex-col gap-1">
              <h2 className="text-xl font-bold">Playlists de Splitify</h2>
              <p className="text-[13px] text-zinc-400">
                Creadas automáticamente al organizar tus canciones
              </p>
            </div>
            {splitifyList.length > 0 && (
              <div className="flex items-center gap-3">
                <button
                  onClick={handleSplitifySelectAll}
                  className="flex items-center gap-2 rounded-lg border border-[#27272A] bg-[#0A0A0A] px-4 py-2.5 text-sm font-medium text-white transition hover:bg-[#27272A]"
                >
                  <CheckCheck size={16} />
                  {allSplitifySelected ? 'Deseleccionar todo' : 'Seleccionar todo'}
                </button>
                <button
                  onClick={splitifySelectedCount > 0 ? handleDeleteSplitifySelected : undefined}
                  className={`flex items-center gap-2 rounded-lg border border-red-900/50 bg-[#0A0A0A] px-4 py-2.5 text-sm font-medium text-red-400 transition ${
                    splitifySelectedCount > 0 ? 'hover:bg-red-500/20 hover:border-red-500/70 hover:text-red-300 cursor-pointer' : 'opacity-40 cursor-not-allowed'
                  }`}
                >
                  <Trash2 size={16} />
                  Eliminar Seleccionados
                </button>
                <button
                  onClick={splitifySelectedCount > 0 && !refreshingBatch ? handleRefreshSplitifySelected : undefined}
                  disabled={refreshingBatch}
                  className={`flex items-center gap-2 rounded-lg border border-green-900/50 bg-[#0A0A0A] px-4 py-2.5 text-sm font-medium text-green-400 transition ${
                    refreshingBatch
                      ? 'cursor-wait'
                      : splitifySelectedCount > 0
                        ? 'hover:bg-green-500/20 hover:border-green-500/70 hover:text-green-300 active:scale-95 cursor-pointer'
                        : 'opacity-40 cursor-not-allowed'
                  }`}
                >
                  <RefreshCw size={16} className={refreshingBatch ? 'animate-spin' : ''} />
                  Actualizar Seleccionados
                </button>
              </div>
            )}
          </div>

          {loading ? null : splitifyList.length === 0 ? (
            <EmptyState
              icon={FolderOpen}
              title="No hay playlists aún"
              description="Las playlists organizadas aparecerán aquí después de sincronizar y organizar"
            />
          ) : (
            <div className="grid gap-5" style={{ gridTemplateColumns: 'repeat(auto-fill, minmax(240px, 1fr))' }}>
              {splitifyList.map(playlist => (
                <SplitifyCard
                  key={playlist.id}
                  playlist={playlist}
                  selected={splitifySelectedIds.has(playlist.id)}
                  onToggleSelect={() => toggleSplitifySelect(playlist.id)}
                  onViewDetails={() => navigate(`/playlist/${playlist.id}`, { state: { playlist } })}
                  onUpdate={() => handleRefreshSplitify(playlist.id)}
                  onDelete={() => handleDeleteSplitify(playlist.id)}
                  onRename={handleRenameSplitify}
                  onUpdateImage={handleUpdateSplitifyImage}
                  refreshing={refreshingIds.has(playlist.id)}
                />
              ))}
            </div>
          )}
        </section>
      </main>
    </>
  )
}

export default MainPage
