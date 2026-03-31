import { useNavigate } from 'react-router-dom'

function LoginPage() {
  const navigate = useNavigate()

  return (
    <div className="flex min-h-screen flex-col items-center justify-center bg-gradient-to-br from-green-900 via-gray-900 to-black text-white">
      <h1 className="mb-4 text-6xl font-bold tracking-tight">Splitify</h1>
      <p className="mb-8 max-w-md text-center text-lg text-gray-300">
        Organiza tus playlists de Spotify por idioma, género y década automáticamente.
      </p>
      <button
        onClick={() => navigate('/')}
        className="rounded-full bg-green-500 px-8 py-3 text-lg font-semibold text-black transition hover:bg-green-400 hover:scale-105 cursor-pointer"
      >
        Iniciar sesión con Spotify
      </button>
    </div>
  )
}

export default LoginPage
