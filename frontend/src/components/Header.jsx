import { Music2, LogOut } from 'lucide-react'
import { Link } from 'react-router-dom'

function Header({ user, onLogout }) {
  return (
    <header className="flex h-16 items-center justify-between border-b border-zinc-800 px-6">
      <Link to="/" className="flex items-center gap-2">
        <div className="flex h-9 w-9 items-center justify-center rounded-lg bg-gradient-to-b from-green-500 to-blue-500">
          <Music2 size={20} className="text-white" />
        </div>
        <span className="bg-gradient-to-r from-green-500 to-blue-500 bg-clip-text text-[22px] font-bold text-transparent">
          Splitify
        </span>
      </Link>

      <div className="flex items-center gap-4">
        {user && (
          <>
            <div className="flex items-center gap-2.5">
              {user.imageUrl ? (
                <img
                  src={user.imageUrl}
                  alt={user.displayName}
                  className="h-8 w-8 rounded-full object-cover"
                />
              ) : (
                <div className="flex h-8 w-8 items-center justify-center rounded-full bg-zinc-700 text-xs font-bold text-white">
                  {user.displayName?.charAt(0)?.toUpperCase() || '?'}
                </div>
              )}
              <span className="text-sm font-medium text-white">
                {user.displayName}
              </span>
            </div>
            <div className="h-6 w-px bg-zinc-700" />
          </>
        )}
        <button
          onClick={onLogout}
          className="flex items-center gap-2 rounded-lg px-4 py-2 text-sm text-zinc-400 transition hover:bg-zinc-800 hover:text-white"
        >
          <LogOut size={16} />
          Cerrar sesion
        </button>
      </div>
    </header>
  )
}

export default Header
