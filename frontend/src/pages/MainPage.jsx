import { useNavigate } from 'react-router-dom'

function MainPage() {
  const navigate = useNavigate()

  return (
    <div className="min-h-screen bg-gray-900 text-white">
      <header className="flex items-center justify-between border-b border-gray-700 px-6 py-4">
        <h1 className="text-2xl font-bold text-green-400">Splitify</h1>
        <button
          onClick={() => navigate('/login')}
          className="rounded bg-gray-700 px-4 py-2 text-sm transition hover:bg-gray-600 cursor-pointer"
        >
          Cerrar sesión
        </button>
      </header>

      <main className="mx-auto max-w-5xl px-6 py-10">
        <section className="mb-10">
          <h2 className="mb-4 text-xl font-semibold">Tus Playlists</h2>
          <div className="rounded-lg border border-gray-700 bg-gray-800 p-8 text-center text-gray-400">
            Aquí aparecerán tus playlists de Spotify leka.
          </div>
        </section>

        <section>
          <h2 className="mb-4 text-xl font-semibold">Playlists de Splitify</h2>
          <div className="rounded-lg border border-gray-700 bg-gray-800 p-8 text-center text-gray-400">
            Aquí aparecerán las playlists generadas por Splitify. Supppper.
          </div>
        </section>
      </main>
    </div>
  )
}

export default MainPage
