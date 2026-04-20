import { useState, useRef, useEffect } from 'react'
import { Eye, Music, Clock, Check, RefreshCw, Trash2, Pencil, Camera, Loader2, X } from 'lucide-react'

const COVER_GRADIENTS = [
  'from-blue-500 to-purple-500',
  'from-red-500 to-orange-500',
  'from-pink-500 to-purple-500',
  'from-green-500 to-yellow-400',
  'from-cyan-500 to-blue-500',
  'from-amber-500 to-pink-500',
  'from-emerald-500 to-teal-400',
]

// Convierte un File (imagen) a base64 JPEG redimensionado a máx 640px.
// Esto garantiza que respetamos el límite de 256KB de Spotify para imágenes de playlists.
function fileToResizedJpegBase64(file, maxDim = 640, quality = 0.85) {
  return new Promise((resolve, reject) => {
    const reader = new FileReader()
    reader.onerror = () => reject(new Error('No se pudo leer la imagen.'))
    reader.onload = () => {
      const img = new Image()
      img.onerror = () => reject(new Error('Archivo no es una imagen válida.'))
      img.onload = () => {
        let { width, height } = img
        if (width > maxDim || height > maxDim) {
          const ratio = Math.min(maxDim / width, maxDim / height)
          width = Math.round(width * ratio)
          height = Math.round(height * ratio)
        }
        const canvas = document.createElement('canvas')
        canvas.width = width
        canvas.height = height
        const ctx = canvas.getContext('2d')
        ctx.drawImage(img, 0, 0, width, height)
        const dataUrl = canvas.toDataURL('image/jpeg', quality)
        const base64 = dataUrl.replace(/^data:image\/jpeg;base64,/, '')
        resolve({ base64, dataUrl })
      }
      img.src = reader.result
    }
    reader.readAsDataURL(file)
  })
}

function SplitifyCard({ playlist, selected, onToggleSelect, onViewDetails, onUpdate, onDelete, onRename, onUpdateImage, refreshing = false }) {
  const gradientIndex = playlist.id % COVER_GRADIENTS.length
  const gradient = COVER_GRADIENTS[gradientIndex]

  const [editingName, setEditingName] = useState(false)
  const [nameDraft, setNameDraft] = useState(playlist.name)
  const [savingName, setSavingName] = useState(false)
  const [uploadingImage, setUploadingImage] = useState(false)
  const [imageError, setImageError] = useState(null)
  const inputNameRef = useRef(null)
  const fileInputRef = useRef(null)

  useEffect(() => {
    if (editingName) {
      inputNameRef.current?.focus()
      inputNameRef.current?.select()
    }
  }, [editingName])

  useEffect(() => {
    // Si el playlist cambia externamente (ej: tras resync), actualizar el draft si no estamos editando
    if (!editingName) setNameDraft(playlist.name)
  }, [playlist.name, editingName])

  const startEditName = (e) => {
    e.stopPropagation()
    setNameDraft(playlist.name)
    setEditingName(true)
  }

  const cancelEditName = () => {
    setEditingName(false)
    setNameDraft(playlist.name)
  }

  const commitEditName = async () => {
    const trimmed = nameDraft.trim()
    if (!trimmed || trimmed === playlist.name) {
      cancelEditName()
      return
    }
    setSavingName(true)
    try {
      await onRename(playlist.id, trimmed)
      setEditingName(false)
    } catch (err) {
      console.error('Error renaming playlist:', err)
      cancelEditName()
    } finally {
      setSavingName(false)
    }
  }

  const handleNameKey = (e) => {
    if (e.key === 'Enter') {
      e.preventDefault()
      commitEditName()
    } else if (e.key === 'Escape') {
      e.preventDefault()
      cancelEditName()
    }
  }

  const handlePickImage = (e) => {
    e.stopPropagation()
    if (refreshing || uploadingImage) return
    fileInputRef.current?.click()
  }

  const handleFileChosen = async (e) => {
    const file = e.target.files?.[0]
    e.target.value = '' // permitir reseleccionar el mismo archivo
    if (!file) return
    setImageError(null)
    setUploadingImage(true)
    try {
      const { base64 } = await fileToResizedJpegBase64(file)
      await onUpdateImage(playlist.id, base64)
    } catch (err) {
      console.error('Error updating image:', err)
      setImageError(err.message || 'No se pudo actualizar la imagen.')
    } finally {
      setUploadingImage(false)
    }
  }

  const hasImage = !!playlist.imageUrl

  return (
    <div
      className={`relative flex flex-col gap-3 rounded-xl p-4 transition cursor-pointer ${
        refreshing
          ? 'border-2 border-green-500/70 bg-[#1A1A1A] shadow-[0_0_24px_rgba(34,197,94,0.25)]'
          : selected
            ? 'border-2 border-green-500 bg-[#1A1A1A]'
            : 'border border-[#27272A] bg-[#1A1A1A]'
      }`}
      onClick={refreshing || editingName ? undefined : onToggleSelect}
    >
      {/* Checkbox */}
      <div className="flex justify-end">
        <div
          className={`flex h-5 w-5 items-center justify-center rounded transition ${
            selected
              ? 'bg-green-500'
              : 'border border-[#27272A] bg-[#0A0A0A]'
          }`}
        >
          {selected && <Check size={14} className="text-black" strokeWidth={3} />}
        </div>
      </div>

      {/* Cover con overlay de edición al hacer hover */}
      <div
        className="group relative aspect-square w-full overflow-hidden rounded-lg"
        onClick={handlePickImage}
      >
        {hasImage ? (
          <img
            src={playlist.imageUrl}
            alt={playlist.name}
            className="h-full w-full object-cover"
          />
        ) : (
          <div className={`flex h-full w-full items-center justify-center bg-gradient-to-br ${gradient}`}>
            <Music size={48} className="text-white/80" />
          </div>
        )}

        {/* Overlay: aparece al hover */}
        {!refreshing && (
          <div className="absolute inset-0 flex cursor-pointer flex-col items-center justify-center gap-2 bg-black/70 opacity-0 transition-opacity group-hover:opacity-100">
            {uploadingImage ? (
              <>
                <Loader2 size={28} className="animate-spin text-white" />
                <span className="text-xs text-white">Subiendo...</span>
              </>
            ) : (
              <>
                <Camera size={28} className="text-white" />
                <span className="text-xs font-semibold text-white">Cambiar foto</span>
              </>
            )}
          </div>
        )}

        {/* Indicador persistente mientras sube (independiente del hover) */}
        {uploadingImage && (
          <div className="absolute inset-0 flex items-center justify-center bg-black/70">
            <Loader2 size={28} className="animate-spin text-white" />
          </div>
        )}

        <input
          ref={fileInputRef}
          type="file"
          accept="image/jpeg,image/jpg,image/png,image/webp"
          className="hidden"
          onChange={handleFileChosen}
          onClick={(e) => e.stopPropagation()}
        />
      </div>

      {imageError && (
        <div className="flex items-start gap-1 rounded-md bg-red-500/10 px-2 py-1 text-xs text-red-400">
          <X size={12} className="mt-0.5 shrink-0" />
          <span>{imageError}</span>
        </div>
      )}

      {/* Name (editable inline). Mantiene la misma altura en ambos estados para no
          desplazar los botones debajo. El input y los botones viven dentro del mismo
          borde (flex con min-w-0) para que no desborden el card estrecho. */}
      {editingName ? (
        <div className="flex h-7 items-center gap-1 rounded-md border border-green-500/60 bg-[#0A0A0A] pl-2 pr-1">
          <input
            ref={inputNameRef}
            type="text"
            value={nameDraft}
            onChange={(e) => setNameDraft(e.target.value)}
            onKeyDown={handleNameKey}
            onClick={(e) => e.stopPropagation()}
            disabled={savingName}
            maxLength={100}
            className="min-w-0 flex-1 bg-transparent text-sm font-semibold text-white outline-none disabled:opacity-50"
          />
          {savingName ? (
            <Loader2 size={14} className="shrink-0 animate-spin text-green-500" />
          ) : (
            <>
              <button
                onClick={(e) => { e.stopPropagation(); commitEditName() }}
                className="flex h-5 w-5 shrink-0 items-center justify-center rounded text-green-500 transition hover:bg-green-500/20"
                title="Guardar"
              >
                <Check size={13} />
              </button>
              <button
                onClick={(e) => { e.stopPropagation(); cancelEditName() }}
                className="flex h-5 w-5 shrink-0 items-center justify-center rounded text-red-400 transition hover:bg-red-500/20"
                title="Cancelar"
              >
                <X size={13} />
              </button>
            </>
          )}
        </div>
      ) : (
        <div className="flex h-7 items-center gap-1.5">
          <p className="min-w-0 flex-1 truncate text-sm font-semibold text-white">{playlist.name}</p>
          {/* Lápiz SIEMPRE visible con color sutil — al hover se ilumina */}
          <button
            onClick={startEditName}
            disabled={refreshing}
            className="flex h-5 w-5 shrink-0 items-center justify-center rounded text-[#71717A] transition hover:bg-[#27272A] hover:text-white disabled:opacity-40"
            title="Renombrar"
          >
            <Pencil size={12} />
          </button>
        </div>
      )}

      {/* Meta */}
      <div className="flex items-center gap-1">
        <Clock size={12} className="text-[#71717A]" />
        <span className="text-xs text-[#71717A]">{playlist.totalTracks} canciones</span>
      </div>

      {/* Ver Detalles */}
      <button
        onClick={(e) => { e.stopPropagation(); onViewDetails() }}
        className="flex w-full items-center justify-center gap-2 rounded-lg border border-[#27272A] bg-[#0A0A0A] px-5 py-2.5 text-sm font-medium text-white transition hover:bg-[#27272A]"
      >
        <Eye size={16} />
        Ver Detalles
      </button>

      {/* Action buttons */}
      <div className="flex w-full gap-2">
        <button
          onClick={(e) => { e.stopPropagation(); if (!refreshing) onUpdate() }}
          disabled={refreshing} className={`flex flex-1 items-center justify-center gap-2 rounded-lg px-3 py-2.5 text-sm font-medium text-green-500 transition ${
            refreshing
              ? 'cursor-wait'
              : 'hover:bg-green-500/15 active:scale-95 active:bg-green-500/25'
          }`}
        >
          <RefreshCw size={16} className={refreshing ? 'animate-spin' : ''} />
          Actualizar
        </button>
        <button
          onClick={(e) => { e.stopPropagation(); onDelete() }}
          disabled={refreshing}
          className={`flex flex-1 items-center justify-center gap-2 rounded-lg px-3 py-2.5 text-sm font-medium text-red-500 transition ${
            refreshing ? 'opacity-40 cursor-not-allowed' : 'hover:bg-red-500/15 active:scale-95 active:bg-red-500/25'
          }`}
        >
          <Trash2 size={16} />
          Eliminar
        </button>
      </div>
    </div>
  )
}

export default SplitifyCard
