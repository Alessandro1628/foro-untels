import { useState } from 'react'
import { api } from './api'

export default function PasswordRecovery({ token, onClose }) {
  const [message, setMessage] = useState('')
  const [busy, setBusy] = useState(false)
  const [done, setDone] = useState(false)
  async function submit(e) {
    e.preventDefault()
    setBusy(true)
    const data = new FormData(e.currentTarget)
    try {
      if (token)
        await api('auth/password/reset', {
          method: 'POST',
          data: { token, password: data.get('password') },
        })
      else await api('auth/password/request', { method: 'POST', data: { email: data.get('email') } })
      setMessage(
        token
          ? 'Contraseña actualizada. Ya puedes iniciar sesión.'
          : 'Si el correo corresponde a una cuenta activa, recibirás un enlace.',
      )
      setDone(true)
    } catch (err) {
      setMessage(
        token && err.status === 400
          ? 'El enlace venció o ya fue utilizado. Solicita otro.'
          : err.message,
      )
    } finally {
      setBusy(false)
    }
  }
  return (
    <div className="auth-fondo">
      <section className="auth-ventana" role="dialog" aria-modal="true" aria-label="Recuperar acceso">
        <h2>{token ? 'Establecer nueva contraseña' : 'Recuperar acceso'}</h2>
        {!done && (
          <form className="auth-formulario" onSubmit={submit}>
            {token ? (
              <label>
                Nueva contraseña
                <input
                  name="password"
                  type="password"
                  autoComplete="new-password"
                  minLength={8}
                  maxLength={64}
                  required
                />
              </label>
            ) : (
              <label>
                Correo de tu cuenta
                <input name="email" type="email" autoComplete="email" required />
              </label>
            )}
            <button disabled={busy}>{token ? 'Guardar contraseña' : 'Enviar enlace'}</button>
          </form>
        )}
        {message && <p role="status">{message}</p>}
        <button onClick={onClose}>Volver al inicio de sesión</button>
      </section>
    </div>
  )
}
