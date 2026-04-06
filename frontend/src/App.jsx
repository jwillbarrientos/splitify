import { Routes, Route } from 'react-router-dom'
import LoginPage from './pages/LoginPage.jsx'
import MainPage from './pages/MainPage.jsx'
import PlaylistDetailPage from './pages/PlaylistDetailPage.jsx'

function App() {
  return (
    <Routes>
      <Route path="/login" element={<LoginPage />} />
      <Route path="/" element={<MainPage />} />
      <Route path="/playlist/:id" element={<PlaylistDetailPage />} />
    </Routes>
  )
}

export default App
