import { Music2 } from 'lucide-react'

function LoginPage() {
  return (
    <div className="flex min-h-screen items-center justify-center bg-gradient-to-br from-green-500 via-blue-500 to-purple-500">
      <div className="flex w-[420px] flex-col gap-6 rounded-2xl bg-[#1A1A1A] p-8 shadow-[0_4px_24px_rgba(0,0,0,0.25)]">
        {/* Logo + título + descripción */}
        <div className="flex flex-col items-center gap-3">
          <div className="flex h-[72px] w-[72px] items-center justify-center rounded-full bg-gradient-to-b from-green-500 to-blue-500">
            <Music2 size={36} className="text-white" />
          </div>
          <h1 className="bg-gradient-to-r from-green-500 to-blue-500 bg-clip-text pb-1 text-4xl font-bold text-transparent">
            Splitify
          </h1>
          <p className="text-center text-sm text-zinc-400">
            Organiza tu música de Spotify por idioma, género o artista
          </p>
        </div>

        {/* Características */}
        <div className="flex flex-col gap-2">
          <p className="text-sm font-semibold text-white">Características:</p>
          <ul className="flex flex-col gap-1 pl-4 text-[13px] text-zinc-400">
            <li>• Sincroniza tus playlists de Spotify</li>
            <li>• Clasificación de género e idioma con IA</li>
            <li>• Ordena tus canciones de la más antigua a la más nueva</li>
            <li>• Organiza por idioma, género, o artista</li>
            <li>• Crea playlists en tu cuenta automáticamente</li>
          </ul>
        </div>

        {/* Botón Spotify */}
        <a
          href="/oauth2/authorization/spotify"
          className="flex h-12 items-center justify-center gap-2 rounded-lg bg-green-500 text-base font-semibold text-[#0A0A0A] transition hover:bg-green-400"
        >
          <Music2 size={20} />
          Conectar con Spotify
        </a>
      </div>
    </div>
  )
}

export default LoginPage
