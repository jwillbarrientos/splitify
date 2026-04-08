import { useState, useEffect } from 'react'
import { useNavigate } from 'react-router-dom'
import { Music, Music2, LogOut, Sparkles, RefreshCw, FolderOpen, Trash2, CheckCheck } from 'lucide-react'
import { getPlaylists, syncPlaylists, deletePlaylists, logout, getSplitifyPlaylists, deleteSplitifyPlaylist, deleteSplitifyPlaylists } from '../services/api'
import SyncOverlay from '../components/SyncOverlay'
import PlaylistCard from '../components/PlaylistCard'
import SplitifyCard from '../components/SplitifyCard'
import OrganizeModal from '../components/OrganizeModal'

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
      setPlaylists(data)
      setSelectedIds(new Set())
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

  const handleLogout = async () => {
    await logout()
    navigate('/login')
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

  const handleTestCreate = (newPlaylist) => {
    setSplitifyList(prev => [...prev, newPlaylist])
    setShowOrganizeModal(false)
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

  const selectedCount = selectedIds.size
  const splitifySelectedCount = splitifySelectedIds.size

  return (
    <div className="min-h-screen bg-[#0A0A0A] text-white">
      <SyncOverlay visible={syncing} />
      <OrganizeModal
        visible={showOrganizeModal}
        onClose={() => setShowOrganizeModal(false)}
        selectedIds={Array.from(selectedIds)}
        onTestCreate={handleTestCreate}
      />

      {/* Header */}
      <header className="flex h-16 items-center justify-between border-b border-zinc-800 px-6">
        <div className="flex items-center gap-2">
          <div className="flex h-9 w-9 items-center justify-center rounded-lg bg-gradient-to-b from-green-500 to-blue-500">
            <Music2 size={20} className="text-white" />
          </div>
          <span className="bg-gradient-to-r from-green-500 to-blue-500 bg-clip-text text-[22px] font-bold text-transparent">
            Splitify
          </span>
        </div>
        <button
          onClick={handleLogout}
          className="flex items-center gap-2 rounded-lg px-4 py-2 text-sm text-zinc-400 transition hover:bg-zinc-800 hover:text-white"
        >
          <LogOut size={16} />
          Cerrar sesión
        </button>
      </header>

      {/* Main */}
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
                    className="flex items-center gap-2 rounded-lg border border-red-900/50 bg-[#0A0A0A] px-4 py-2.5 text-sm font-medium text-red-400 transition hover:bg-red-950/30"
                  >
                    <Trash2 size={16} />
                    Borrar datos
                  </button>
                  <button
                    onClick={handleSync}
                    className="flex items-center gap-2 rounded-lg bg-green-500 px-4 py-2.5 text-sm font-semibold text-black transition hover:bg-green-600"
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
                  className={`flex items-center gap-2 rounded-lg px-4 py-2.5 text-sm font-medium text-red-500 transition ${
                    splitifySelectedCount > 0 ? 'hover:bg-red-500/10 cursor-pointer' : 'opacity-40 cursor-not-allowed'
                  }`}
                >
                  <Trash2 size={16} />
                  Eliminar Seleccionados
                </button>
                <button
                  className="flex items-center gap-2 rounded-lg bg-green-500 px-4 py-2.5 text-sm font-semibold text-black opacity-40 cursor-not-allowed"
                >
                  <RefreshCw size={16} />
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
                  onUpdate={() => {}}
                  onDelete={() => handleDeleteSplitify(playlist.id)}
                />
              ))}
            </div>
          )}
        </section>
      </main>
    </div>
  )
}

export default MainPage
