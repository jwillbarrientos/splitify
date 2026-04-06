import { useState, useEffect } from 'react'
import { useParams, useLocation, useNavigate } from 'react-router-dom'
import { Music2, LogOut, ArrowLeft, Music } from 'lucide-react'
import { getPlaylist, getPlaylistSongs, logout } from '../services/api'

function PlaylistDetailPage() {
  const { id } = useParams()
  const location = useLocation()
  const navigate = useNavigate()

  const [playlist, setPlaylist] = useState(location.state?.playlist || null)
  const [songs, setSongs] = useState([])
  const [loading, setLoading] = useState(true)

  useEffect(() => {
    const fetchData = async () => {
      try {
        if (!playlist) {
          const pl = await getPlaylist(id)
          setPlaylist(pl)
        }
        const songsData = await getPlaylistSongs(id)
        setSongs(songsData)
      } catch (err) {
        console.error('Error fetching playlist details:', err)
      } finally {
        setLoading(false)
      }
    }
    fetchData()
  }, [id])

  const handleLogout = async () => {
    await logout()
    navigate('/login')
  }

  if (loading && !playlist) {
    return <div className="min-h-screen bg-[#0A0A0A]" />
  }

  return (
    <div className="min-h-screen bg-[#0A0A0A] text-white">
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

      {/* Content */}
      <main className="flex flex-col gap-6 p-8">
        {/* Back button */}
        <button
          onClick={() => navigate('/')}
          className="flex w-fit items-center gap-2 rounded-lg border border-[#27272A] bg-[#0A0A0A] px-5 py-2.5 text-sm font-medium text-white transition hover:bg-[#27272A]"
        >
          <ArrowLeft size={16} />
          Volver
        </button>

        {/* Playlist info */}
        {playlist && (
          <div className="flex items-center gap-4">
            {playlist.imageUrl ? (
              <img
                src={playlist.imageUrl}
                alt={playlist.name}
                className="h-[72px] w-[72px] rounded-xl object-cover"
              />
            ) : (
              <div className="flex h-[72px] w-[72px] items-center justify-center rounded-xl bg-gradient-to-br from-purple-500 to-blue-500">
                <Music size={32} className="text-white" />
              </div>
            )}
            <div className="flex flex-col gap-1">
              <h1 className="text-[28px] font-bold">{playlist.name}</h1>
              <p className="text-sm text-[#A1A1AA]">{playlist.totalTracks} canciones</p>
            </div>
          </div>
        )}

        {/* Songs table */}
        <div className="overflow-hidden rounded-xl border border-[#27272A]">
          <table className="w-full">
            <thead>
              <tr className="h-11 bg-[#1A1A1A] text-left text-[13px] font-semibold text-[#A1A1AA]">
                <th className="w-[60px] px-4">ID</th>
                <th className="px-4">Nombre</th>
                <th className="w-[200px] px-4">Artista</th>
                <th className="w-[100px] px-4">Idioma</th>
                <th className="w-[80px] px-4">Fecha</th>
                <th className="w-[180px] px-4">Género</th>
              </tr>
            </thead>
            <tbody>
              {songs.map((song, i) => (
                <tr key={song.id} className="h-11 border-t border-[#27272A]">
                  <td className="w-[60px] px-4 font-mono text-[13px] text-[#71717A]">{i + 1}</td>
                  <td className="px-4 text-[13px] font-medium text-white">{song.name}</td>
                  <td className="w-[200px] px-4 text-[13px] text-[#A1A1AA]">{song.artist}</td>
                  <td className="w-[100px] px-4 text-[13px] text-[#A1A1AA]">{song.language || '—'}</td>
                  <td className="w-[80px] px-4 font-mono text-[13px] text-[#A1A1AA]">{song.releaseYear || '—'}</td>
                  <td className="w-[180px] px-4 text-[13px] text-[#A1A1AA]">{song.genre || '—'}</td>
                </tr>
              ))}
              {songs.length === 0 && !loading && (
                <tr className="h-11 border-t border-[#27272A]">
                  <td colSpan={6} className="px-4 text-center text-[13px] text-[#71717A]">
                    No hay canciones
                  </td>
                </tr>
              )}
            </tbody>
          </table>
        </div>
      </main>
    </div>
  )
}

export default PlaylistDetailPage
