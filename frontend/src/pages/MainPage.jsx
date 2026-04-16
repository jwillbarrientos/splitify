import { useState, useEffect } from 'react'
import { useNavigate } from 'react-router-dom'
import { Music, Sparkles, RefreshCw, FolderOpen, Trash2, CheckCheck } from 'lucide-react'
import { getPlaylists, syncPlaylists, deletePlaylists, getSplitifyPlaylists, deleteSplitifyPlaylist, deleteSplitifyPlaylists, previewRefresh, refreshSplitifyPlaylist, refreshSplitifyPlaylists } from '../services/api'
import SyncOverlay from '../components/SyncOverlay'
import PlaylistCard from '../components/PlaylistCard'
import SplitifyCard from '../components/SplitifyCard'
import OrganizeModal from '../components/OrganizeModal'
import CustomOrganizeModal from '../components/CustomOrganizeModal'
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
    setSplitifyList(prev => [...prev, ...newPlaylists])
    setSelectedIds(new Set())
    setShowOrganizeModal(false)
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

  const [refreshConfirm, setRefreshConfirm] = useState(null) // { id, preview } or { ids, mode: 'batch' }

  const handleRefreshSplitify = async (id) => {
    if (refreshingIds.has(id)) return
    addRefreshing(id)
    try {
      const preview = await previewRefresh(id)
      if (preview.removedByUser && preview.removedByUser.length > 0) {
        // Hay canciones eliminadas manualmente → pedir confirmación.
        // Mantenemos refreshing hasta que el usuario confirme/cancele.
        setRefreshConfirm({ id, preview })
      } else {
        // No hay conflictos, actualizar directamente
        const updated = await refreshSplitifyPlaylist(id, false)
        if (updated === null) {
          setSplitifyList(prev => prev.filter(p => p.id !== id))
        } else {
          setSplitifyList(prev => prev.map(p => p.id === id ? updated : p))
        }
        removeRefreshing(id)
      }
    } catch (err) {
      console.error('Error refreshing splitify playlist:', err)
      removeRefreshing(id)
    }
  }

  const handleConfirmRefresh = async (restoreRemoved) => {
    const { id } = refreshConfirm
    setRefreshConfirm(null)
    try {
      const updated = await refreshSplitifyPlaylist(id, restoreRemoved)
      if (updated === null) {
        setSplitifyList(prev => prev.filter(p => p.id !== id))
      } else {
        setSplitifyList(prev => prev.map(p => p.id === id ? updated : p))
      }
    } catch (err) {
      console.error('Error refreshing splitify playlist:', err)
    } finally {
      removeRefreshing(id)
    }
  }

  const handleCancelRefreshConfirm = () => {
    if (refreshConfirm) removeRefreshing(refreshConfirm.id)
    setRefreshConfirm(null)
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
      const updated = await refreshSplitifyPlaylists(ids, false)
      const updatedIds = new Set(updated.map(p => p.id))
      setSplitifyList(prev => {
        const remaining = prev.filter(p => !splitifySelectedIds.has(p.id) || updatedIds.has(p.id))
        return remaining.map(p => {
          const match = updated.find(u => u.id === p.id)
          return match || p
        })
      })
      setSplitifySelectedIds(new Set())
    } catch (err) {
      console.error('Error refreshing selected splitify playlists:', err)
    } finally {
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
        selectedIds={Array.from(selectedIds)}
        onPlaylistsCreated={handlePlaylistsCreated}
        onOpenCustom={handleOpenCustom}
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

      {/* Modal de confirmación de refresh */}
      {refreshConfirm && (
        <div className="fixed inset-0 z-50 flex items-center justify-center bg-black/60 backdrop-blur-sm">
          <div className="flex flex-col gap-5 rounded-2xl border border-zinc-800 bg-[#141414] p-6 shadow-2xl" style={{ maxWidth: '480px', width: '90%' }}>
            <div className="flex flex-col gap-2">
              <h3 className="text-lg font-bold text-white">Canciones eliminadas detectadas</h3>
              <p className="text-sm text-zinc-400">
                Se detectaron {refreshConfirm.preview.removedByUser.length} cancion{refreshConfirm.preview.removedByUser.length === 1 ? '' : 'es'} que eliminaste manualmente desde Spotify:
              </p>
            </div>
            <div className="flex flex-col gap-1.5 max-h-48 overflow-y-auto rounded-lg bg-[#0A0A0A] p-3">
              {refreshConfirm.preview.removedByUser.map(song => (
                <div key={song.spotifyId} className="flex items-center gap-2 text-sm">
                  <span className="text-red-400">-</span>
                  <span className="text-zinc-300 truncate">{song.name}</span>
                  <span className="text-zinc-600">·</span>
                  <span className="text-zinc-500 truncate">{song.artist}</span>
                </div>
              ))}
            </div>
            <p className="text-sm text-zinc-400">¿Quieres volver a agregarlas o solo agregar las canciones nuevas?</p>
            <div className="flex gap-3">
              <button
                onClick={() => handleConfirmRefresh(true)}
                className="flex-1 rounded-lg bg-gradient-to-r from-green-500 to-blue-500 py-2.5 text-sm font-semibold text-black transition hover:opacity-90"
              >
                Restaurar todas
              </button>
              <button
                onClick={() => handleConfirmRefresh(false)}
                className="flex-1 rounded-lg border border-zinc-700 bg-[#0A0A0A] py-2.5 text-sm font-medium text-white transition hover:bg-[#1A1A1A]"
              >
                Solo nuevas
              </button>
              <button
                onClick={handleCancelRefreshConfirm}
                className="rounded-lg border border-zinc-800 bg-[#0A0A0A] px-4 py-2.5 text-sm font-medium text-zinc-500 transition hover:bg-[#1A1A1A] hover:text-zinc-300"
              >
                Cancelar
              </button>
            </div>
          </div>
        </div>
      )}

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
