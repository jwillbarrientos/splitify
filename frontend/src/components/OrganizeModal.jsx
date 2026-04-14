import { useState } from 'react'
import { X, Sparkles, Languages, Music, Calendar, SlidersHorizontal, Check } from 'lucide-react'
import { createOrganizedPlaylists } from '../services/api'

function Checkbox({ checked }) {
  return (
    <div
      className={`flex h-5 w-5 shrink-0 items-center justify-center rounded-md transition ${
        checked
          ? 'bg-green-500'
          : 'border-2 border-[#27272A] bg-[#0A0A0A]'
      }`}
    >
      {checked && <Check size={14} className="text-black" strokeWidth={3} />}
    </div>
  )
}

function OptionCard({ icon: Icon, title, description, checked, onToggle }) {
  return (
    <button
      onClick={onToggle}
      className="flex w-full flex-col gap-2 rounded-lg border border-[#27272A] p-4 text-left transition hover:border-[#3f3f46]"
    >
      <div className="flex items-center gap-2">
        <Checkbox checked={checked} />
        <Icon size={16} className="text-white" />
        <span className="text-sm font-semibold text-white">{title}</span>
      </div>
      <p className="text-[13px] text-[#A1A1AA]">{description}</p>
    </button>
  )
}

function OrganizeModal({ visible, onClose, selectedIds, onPlaylistsCreated, onOpenCustom }) {
  const [options, setOptions] = useState({
    idioma: false,
    genero: false,
    fecha: false,
  })
  const [creating, setCreating] = useState(false)

  if (!visible) return null

  const toggleOption = (key) => {
    setOptions(prev => ({ ...prev, [key]: !prev[key] }))
  }

  const anySelected = options.idioma || options.genero || options.fecha

  const handleCreate = async () => {
    if (!anySelected || creating) return
    setCreating(true)
    try {
      const newPlaylists = await createOrganizedPlaylists(selectedIds, options)
      onPlaylistsCreated(newPlaylists)
      setOptions({ idioma: false, genero: false, fecha: false })
    } catch (err) {
      console.error('Error creating playlists:', err)
    } finally {
      setCreating(false)
    }
  }

  return (
    <div className="fixed inset-0 z-50 flex items-center justify-center bg-[#0A0A0A]/80">
      <div className="relative flex w-[600px] flex-col gap-5 rounded-2xl border border-[#27272A] bg-[#1A1A1A] p-6 shadow-[0_8px_32px_rgba(0,0,0,0.4)]">
        {/* Close button */}
        <button
          onClick={onClose}
          className="absolute right-6 top-6 text-[#A1A1AA] transition hover:text-white"
        >
          <X size={20} />
        </button>

        {/* Header */}
        <div className="flex flex-col gap-1">
          <h2 className="text-lg font-bold text-white">Organizar Música</h2>
          <p className="text-sm text-[#A1A1AA]">
            Selecciona cómo quieres organizar tus playlists
          </p>
        </div>

        {/* Options */}
        <div className="flex flex-col gap-3">
          <OptionCard
            icon={Languages}
            title="Por Idioma"
            description="Crea una playlist separada por cada idioma encontrado en tus selecciones"
            checked={options.idioma}
            onToggle={() => toggleOption('idioma')}
          />
          <OptionCard
            icon={Music}
            title="Por Género"
            description="Crea una playlist separada por cada género definido en tus selecciones"
            checked={options.genero}
            onToggle={() => toggleOption('genero')}
          />
          <OptionCard
            icon={Calendar}
            title="Por Fecha de Lanzamiento"
            description="Crea una playlist con todas las canciones ordenadas por fecha de lanzamiento"
            checked={options.fecha}
            onToggle={() => toggleOption('fecha')}
          />
          {/* Crear Playlists button */}
          <button
            onClick={handleCreate}
            disabled={!anySelected || creating}
            className={`flex w-full items-center justify-center gap-2 rounded-lg bg-gradient-to-r from-green-500 to-blue-500 px-5 py-2.5 text-sm font-semibold text-black transition ${
              anySelected && !creating ? 'hover:opacity-90' : 'opacity-40 cursor-not-allowed'
            }`}
          >
            <Sparkles size={16} />
            {creating ? 'Creando playlists...' : 'Crear Playlists'}
          </button>

        </div>

        {/* Separator */}
        <div className="flex items-center gap-4">
          <div className="h-px flex-1 bg-[#27272A]" />
          <span className="text-[13px] text-[#A1A1AA]">o</span>
          <div className="h-px flex-1 bg-[#27272A]" />
        </div>

        {/* Custom option - gradient border via wrapper */}
        <button
          type="button"
          onClick={onOpenCustom}
          className="group rounded-lg bg-gradient-to-r from-green-500 to-blue-500 p-[1.5px] text-left transition duration-200 hover:scale-[1.015] hover:shadow-[0_0_24px_rgba(34,197,94,0.35)] active:scale-[0.99]"
        >
          <div className="flex flex-col gap-2 rounded-[7px] bg-[#1A1A1A] p-4 transition duration-200 group-hover:bg-[#202024]">
            <div className="flex items-center gap-2">
              <SlidersHorizontal
                size={16}
                className="text-green-500 transition duration-200 group-hover:scale-110 group-hover:text-green-400"
              />
              <span className="text-sm font-semibold text-white transition group-hover:text-green-50">
                Personalizado
              </span>
            </div>
            <p className="text-[13px] text-[#A1A1AA] transition group-hover:text-[#D4D4D8]">
              Crea una playlist con criterios personalizados (Idioma, género, artista)
            </p>
          </div>
        </button>
      </div>
    </div>
  )
}

export default OrganizeModal
