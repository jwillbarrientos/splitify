import { useState, useEffect } from 'react'
import { Routes, Route, Outlet, useNavigate } from 'react-router-dom'
import { getUserProfile, logout } from './services/api'
import Header from './components/Header'
import LoginPage from './pages/LoginPage.jsx'
import MainPage from './pages/MainPage.jsx'
import PlaylistDetailPage from './pages/PlaylistDetailPage.jsx'

function AuthLayout() {
  const [user, setUser] = useState(null)
  const navigate = useNavigate()

  useEffect(() => {
    getUserProfile().then(setUser).catch(() => {})
  }, [])

  const handleLogout = async () => {
    await logout()
    navigate('/login')
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
