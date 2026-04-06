import { RefreshCw } from 'lucide-react'

function SyncOverlay({ visible }) {
  if (!visible) return null

  return (
    <div className="fixed inset-0 z-50 flex items-center justify-center bg-[#0A0A0A]">
      <div className="flex w-[480px] flex-col items-center gap-6 rounded-2xl border border-[#27272A] bg-[#1A1A1A] p-10 shadow-[0_8px_32px_rgba(0,0,0,0.4)]">
        {/* Spinner */}
        <div className="flex h-[72px] w-[72px] animate-spin items-center justify-center rounded-full bg-gradient-to-br from-green-500 to-blue-500">
          <RefreshCw size={32} className="text-white" />
        </div>

        {/* Textos */}
        <div className="flex w-full flex-col items-center gap-2">
          <h2 className="text-center text-2xl font-bold text-white">
            Sincronizando...
          </h2>
          <p className="text-center text-sm text-[#A1A1AA]">
            Trayendo tus playlists de Spotify.
            <br />
            Esto puede tardar unos minutos.
          </p>
        </div>

        <p className="text-xs text-[#71717A]">
          Por favor no cierres esta ventana
        </p>
      </div>
    </div>
  )
}

export default SyncOverlay
