import { useState } from 'react'
import { api } from './api'

export default function ContentActions({ postId, item, isComment = false, token, userId, onChange }) {
  const [mode, setMode] = useState('')
  const [title, setTitle] = useState('')
  const [category, setCategory] = useState('')
  const [content, setContent] = useState('')
  const [reason, setReason] = useState('')
  const [busy, setBusy] = useState(false)
  const [message, setMessage] = useState('')
  if (!token || item.status !== 'PUBLISHED') return null

  function open(next) {
    setTitle(item.title || '')
    setCategory(item.category || 'General')
    setContent(item.content)
    setReason('')
    setMessage('')
    setMode(next)
  }

  async function submit(event) {
    event.preventDefault()
    if (busy) return
    setBusy(true)
    setMessage('')
    const path = isComment ? `posts/${postId}/comments/${item.id}` : `posts/${postId}`
    try {
      if (mode === 'report') {
        await api('reports', { method: 'POST', token, data: {
          postId, commentId: isComment ? item.id : null, reason: reason.trim(),
        } })
        setMessage('Reporte enviado a moderación.')
      } else {
        if (mode === 'withdraw') await api(path, { method: 'DELETE', token })
        else await api(path, { method: 'PUT', token, data: isComment ? { content } : { title, content, category } })
        onChange()
      }
      setMode('')
    } catch (e) { setMessage(e.message) }
    finally { setBusy(false) }
  }

  return <div className="acciones-aporte">
    {!mode && <>
      {item.authorId === userId && <>
        <button onClick={() => open('edit')}>Editar</button>
        <button onClick={() => open('withdraw')}>Retirar</button>
      </>}
      <button onClick={() => open('report')}>Reportar</button>
    </>}
    {mode && <form className="acciones-form" onSubmit={submit}>
      {mode === 'edit' && <>
        {!isComment && <>
          <label>Título<input required maxLength={180} value={title} onChange={e => setTitle(e.target.value)} /></label>
          <label>Categoría<input required maxLength={80} value={category} onChange={e => setCategory(e.target.value)} /></label>
        </>}
        <label>Contenido<textarea aria-label="Contenido" required minLength={isComment ? 2 : 10} maxLength={isComment ? 5000 : 10000}
          value={content} onChange={e => setContent(e.target.value)} /></label>
      </>}
      {mode === 'withdraw' && <p>{isComment ? '¿Retirar este comentario?' : '¿Retirar esta consulta? Las respuestas de la comunidad se conservarán.'}</p>}
      {mode === 'report' && <label>Motivo del reporte<textarea required maxLength={500} value={reason} onChange={e => setReason(e.target.value)} /></label>}
      <div><button disabled={busy}>{mode === 'report' ? 'Enviar reporte' : mode === 'edit' ? 'Guardar cambios' : 'Confirmar retiro'}</button>
        <button disabled={busy} type="button" onClick={() => setMode('')}>Cancelar</button></div>
    </form>}
    {message && <p role="status">{message}</p>}
  </div>
}
