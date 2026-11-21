import { useState } from 'react'
import { api } from './api'

export default function Profile({ user, token, onSave, onClose }) {
  const [name, setName] = useState(user.fullName)
  const [program, setProgram] = useState(user.academicProgram || '')
  const [bio, setBio] = useState(user.biography || '')
  const [busy, setBusy] = useState(false)
  const [message, setMessage] = useState('')
  async function save(event) {
    event.preventDefault()
    if (busy) return
    setBusy(true)
    try {
      const updated = await api('auth/me', { method: 'PUT', token,
        data: { fullName: name.trim(), academicProgram: program.trim(), biography: bio.trim() } })
      onSave(updated)
      setMessage('Perfil actualizado.')
    } catch (e) { setMessage(e.message) }
    finally { setBusy(false) }
  }
  return <section className="tarjeta-pregunta">
    <h2>Mi perfil</h2>
    <p>{user.email}</p>
    <form className="acciones-form" onSubmit={save}>
      <label>Nombre completo<input required maxLength={120} value={name} onChange={e => setName(e.target.value)} /></label>
      <label>Carrera o especialidad<input maxLength={120} value={program} onChange={e => setProgram(e.target.value)} /></label>
      <label>Presentación académica<textarea maxLength={500} value={bio} onChange={e => setBio(e.target.value)} /></label>
      <div><button disabled={busy}>Guardar perfil</button> <button type="button" onClick={onClose}>Volver al foro</button></div>
      <p role="status">{message}</p>
    </form>
  </section>
}
