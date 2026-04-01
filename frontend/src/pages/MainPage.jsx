import { useNavigate } from 'react-router-dom'
import { Music, Music2, LogOut, Sparkles, RefreshCw, FolderOpen } from 'lucide-react'

function EmptyState({ icon: Icon, title, description, buttonLabel, buttonIcon: BtnIcon }) {
  return (
    <div className="flex flex-col items-center justify-center gap-4 rounded-xl border border-zinc-800 bg-[#1A1A1A] px-6 py-12">
      <Icon size={48} className="text-zinc-600" />
      <div className="flex flex-col items-center gap-1">
        <p className="text-sm font-semibold text-white">{title}</p>
        <p className="text-center text-xs text-zinc-500">{description}</p>
      </div>
      {buttonLabel && (
        <button className="flex items-center gap-2 rounded-lg bg-gradient-to-r from-green-500 to-blue-500 px-4 py-2.5 text-sm font-semibold text-black transition hover:opacity-90">
          {BtnIcon && <BtnIcon size={16} />}
          {buttonLabel}
        </button>
      )}
    </div>
  )
}

function MainPage() {
  const navigate = useNavigate()

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
          onClick={() => navigate('/login')}
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
                Sincroniza tus playlists de Spotify para empezar a organizarlas
              </p>
            </div>
            <button className="flex items-center gap-2 rounded-lg bg-gradient-to-r from-green-500 to-blue-500 px-4 py-2.5 text-sm font-semibold text-black opacity-40">
              <Sparkles size={16} />
              Organizar (0)
            </button>
          </div>
          <EmptyState
            icon={Music}
            title="Aún no hay playlists sincronizadas"
            description='Haz clic en "Sincronizar Playlists" para traer tus playlists de Spotify'
            buttonLabel="Sincronizar Playlists"
            buttonIcon={RefreshCw}
          />
        </section>

        {/* Divider */}
        <hr className="border-zinc-800" />

        {/* Sección: Playlists de Splitify */}
        <section className="flex flex-col gap-5">
          <div className="flex flex-col gap-1">
            <h2 className="text-xl font-bold">Playlists de Splitify</h2>
            <p className="text-[13px] text-zinc-400">
              Creadas automáticamente al organizar tus canciones
            </p>
          </div>
          <EmptyState
            icon={FolderOpen}
            title="No hay playlists aún"
            description="Las playlists organizadas aparecerán aquí después de sincronizar y organizar"
          />
        </section>
      </main>
    </div>
  )
}

export default MainPage
