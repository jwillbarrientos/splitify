import { useState, useEffect } from 'react'
import { Routes, Route, Outlet, Navigate, useNavigate } from 'react-router-dom'
import { getUserProfile, logout } from './services/api'
import Header from './components/Header'
import LoginPage from './pages/LoginPage.jsx'
import MainPage from './pages/MainPage.jsx'
import PlaylistDetailPage from './pages/PlaylistDetailPage.jsx'

function AuthLayout() {
  const [user, setUser] = useState(null)
  // 'loading' = todavia verificando, 'authenticated' = ok, 'unauthenticated' = ir a /login
  const [authStatus, setAuthStatus] = useState('loading')
  const navigate = useNavigate()

  useEffect(() => {
    getUserProfile()
      .then((profile) => {
        if (profile) {
          setUser(profile)
          setAuthStatus('authenticated')
        } else {
          setAuthStatus('unauthenticated')
        }
      })
      .catch(() => setAuthStatus('unauthenticated'))
  }, [])

  const handleLogout = async () => {
    await logout()
    navigate('/login')
  }

  // Mientras se verifica el auth, no renderizamos contenido protegido
  // (asi el usuario no ve la playlist/homepage por un instante antes del redirect).
  if (authStatus === 'loading') {
    return <div className="min-h-screen bg-[#0A0A0A]" />
  }

  // No autenticado: redirect SPA-style a /login, sin reload del navegador.
  if (authStatus === 'unauthenticated') {
    return <Navigate to="/login" replace />
  }

  return (
    <div className="min-h-screen bg-[#0A0A0A] text-white">
      <Header user={user} onLogout={handleLogout} />
      <Outlet />
    </div>
  )
}

function App() {
  return (
    <Routes>
      <Route path="/login" element={<LoginPage />} />
      <Route element={<AuthLayout />}>
        <Route path="/" element={<MainPage />} />
        <Route path="/playlist/:id" element={<PlaylistDetailPage />} />
      </Route>
    </Routes>
  )
}

export default App
