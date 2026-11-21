import { useEffect, useState } from 'react'
import { api } from './api'

function Report({ report, token, onResolved }) {
  const [reason, setReason] = useState('')
  const [busy, setBusy] = useState(false)
  const [error, setError] = useState('')
  async function resolve(event) {
    event.preventDefault()
    if (busy) return
    const decision = event.nativeEvent.submitter?.value
    if (!decision) return
    setBusy(true)
    try {
      await api('reports/' + report.id, { method: 'PUT', token, data: { decision, reason } })
      onResolved()
    } catch (e) { setError(e.message) }
    finally { setBusy(false) }
  }
  return <article className="tarjeta-pregunta">
    <h3>{report.commentId ? 'Comentario' : 'Publicación'} · Reporte #{report.id}</h3>
    <p>{report.content}</p><p>Motivo del reporte: {report.reason}</p>
    <form className="acciones-form" onSubmit={resolve}>
      <label>Motivo de la decisión<textarea required maxLength={500} value={reason} onChange={e => setReason(e.target.value)} /></label>
      <div><button value="DISMISSED" disabled={busy}>Desestimar reporte</button>
        <button value="REMOVED" disabled={busy}>Retirar por infracción</button></div>
      {error && <p role="alert">{error}</p>}
    </form>
  </article>
}

export default function Moderation({ token, onChanged }) {
  const [reports, setReports] = useState(null)
  const [error, setError] = useState('')
  const [revision, setRevision] = useState(0)
  useEffect(() => {
    const controller = new AbortController()
    api('reports', { token, signal: controller.signal }).then(data => {
      setReports(data); setError('')
    }).catch(e => { if (!controller.signal.aborted) setError(e.message) })
    return () => controller.abort()
  }, [token, revision])
  return <section><h2>Reportes pendientes</h2>
    {error ? <p role="alert">{error} <button onClick={() => setRevision(r => r + 1)}>Reintentar</button></p>
      : !reports ? <p>Cargando reportes…</p> : reports.length === 0 ? <p>No hay reportes pendientes.</p> : reports.map(r =>
        <Report key={r.id} token={token} report={r} onResolved={() => { setRevision(n => n + 1); onChanged() }} />)}
  </section>
}
