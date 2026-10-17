
import { useState } from 'react'
import './AuthPanel.css'

function AuthPanel({ user, onLogin, onLogout }) {
  const [modo, setModo] = useState(null)
  const [nombre, setNombre] = useState('')
  const [email, setEmail] = useState('')
  const [password, setPassword] = useState('')
  const [cargando, setCargando] = useState(false)
  const [error, setError] = useState('')
  const [mensaje, setMensaje] = useState('')

  function abrirModal(nuevoModo) {
    setModo(nuevoModo)
    setNombre('')
    setEmail('')
    setPassword('')
    setError('')
    setMensaje('')
  }

  function cerrarModal() {
    if (!cargando) {
      setModo(null)
      setError('')
      setMensaje('')
      setPassword('')
    }
  }

  function cambiarModo(nuevoModo) {
    setModo(nuevoModo)
    setError('')
    setMensaje('')
    setPassword('')
  }

  async function enviarFormulario(event) {
    event.preventDefault()

    if (cargando) return

    setCargando(true)
    setError('')
    setMensaje('')

    const esRegistro = modo === 'registro'

    const datos = esRegistro
      ? {
          fullName: nombre.trim(),
          email: email.trim(),
          password,
        }
      : {
          email: email.trim(),
          password,
        }

    const ruta = esRegistro
      ? '/api/v1/auth/register'
      : '/api/v1/auth/login'

    try {
      const respuesta = await fetch(ruta, {
        method: 'POST',
        headers: {
          'Content-Type': 'application/json',
        },
        body: JSON.stringify(datos),
      })

      if (!respuesta.ok) {
        if (respuesta.status === 409) {
          throw new Error('Este correo ya está registrado.')
        }

        if (respuesta.status === 401) {
          throw new Error('Correo o contraseña incorrectos.')
        }

        if (respuesta.status === 400) {
          throw new Error(
            'Revisa los datos ingresados e inténtalo nuevamente.'
          )
        }

        throw new Error(
          `No se pudo completar la solicitud. Código: ${respuesta.status}`
        )
      }

      const resultado = await respuesta.json()

      if (esRegistro) {
        setModo('login')
        setPassword('')
        setNombre('')
        setMensaje(
          'Cuenta registrada correctamente. Ahora inicia sesión.'
        )
        return
      }

      const token = resultado.accessToken

      if (!token) {
        throw new Error('El servidor no devolvió un token JWT.')
      }

      // Obtener los datos reales del estudiante
      const perfilRespuesta = await fetch('/api/v1/auth/me', {
        headers: {
          Authorization: `Bearer ${token}`,
        },
      })

      if (!perfilRespuesta.ok) {
        throw new Error(
          'No se pudo obtener el perfil del estudiante.'
        )
      }

      const perfil = await perfilRespuesta.json()

      onLogin({
        user: perfil,
        token,
      })

      cerrarModal()
      setModo(null)
      setPassword('')
    } catch (err) {
      setError(
        err instanceof TypeError
          ? 'No se pudo conectar con Auth Service.'
          : err.message
      )
    } finally {
      setCargando(false)
    }
  }

  return (
    <>
      {user ? (
        <div className="acciones-acceso">
          <span className="auth-usuario">
            Hola, {user.fullName}
          </span>

          <button type="button" onClick={onLogout}>
            Cerrar sesión
          </button>
        </div>
      ) : (
        <div className="acciones-acceso">
          <button
            type="button"
            onClick={() => abrirModal('login')}
          >
            Iniciar sesión
          </button>

          <button
            type="button"
            className="boton-registro"
            onClick={() => abrirModal('registro')}
          >
            Registrarse
          </button>
        </div>
      )}

      {modo && (
        <div className="auth-fondo">
          <div
            className="auth-ventana"
            role="dialog"
            aria-modal="true"
            aria-labelledby="auth-titulo"
          >
            <div className="auth-encabezado">
              <div>
                <p className="auth-subtitulo">
                  FORO ACADÉMICO UNTELS
                </p>

                <h2 id="auth-titulo">
                  {modo === 'login'
                    ? 'Iniciar sesión'
                    : 'Crear una cuenta'}
                </h2>
              </div>

              <button
                type="button"
                className="auth-cerrar"
                onClick={cerrarModal}
                disabled={cargando}
                aria-label="Cerrar ventana"
              >
                ×
              </button>
            </div>

            <p className="auth-descripcion">
              {modo === 'login'
                ? 'Ingresa para participar en la comunidad académica.'
                : 'Regístrate para compartir preguntas y conocimientos.'}
            </p>

            {error && (
              <div className="auth-error" role="alert">
                {error}
              </div>
            )}

            {mensaje && (
              <div className="auth-exito" role="status">
                {mensaje}
              </div>
            )}

            <form
              className="auth-formulario"
              onSubmit={enviarFormulario}
            >
              {modo === 'registro' && (
                <>
                  <label htmlFor="auth-nombre">
                    Nombre completo
                  </label>

                  <input
                    id="auth-nombre"
                    type="text"
                    placeholder="Ej. Juan Pérez"
                    value={nombre}
                    onChange={(e) => setNombre(e.target.value)}
                    minLength={2}
                    maxLength={120}
                    autoComplete="name"
                    required
                  />
                </>
              )}

              <label htmlFor="auth-email">
                Correo electrónico
              </label>

              <input
                id="auth-email"
                type="email"
                placeholder="correo@ejemplo.com"
                value={email}
                onChange={(e) => setEmail(e.target.value)}
                autoComplete="email"
                required
              />

              <label htmlFor="auth-password">
                Contraseña
              </label>

              <input
                id="auth-password"
                type="password"
                placeholder={
                  modo === 'registro'
                    ? 'Mínimo 8 caracteres'
                    : 'Ingresa tu contraseña'
                }
                value={password}
                onChange={(e) => setPassword(e.target.value)}
                minLength={modo === 'registro' ? 8 : undefined}
                maxLength={modo === 'registro' ? 64 : undefined}
                autoComplete={
                  modo === 'registro'
                    ? 'new-password'
                    : 'current-password'
                }
                required
              />

              <button
                className="auth-enviar"
                type="submit"
                disabled={cargando}
              >
                {cargando
                  ? 'Procesando...'
                  : modo === 'login'
                    ? 'Iniciar sesión'
                    : 'Crear cuenta'}
              </button>
            </form>

            <div className="auth-cambiar">
              {modo === 'login' ? (
                <p>
                  ¿Todavía no tienes cuenta?{' '}
                  <button
                    type="button"
                    onClick={() => cambiarModo('registro')}
                    disabled={cargando}
                  >
                    Regístrate
                  </button>
                </p>
              ) : (
                <p>
                  ¿Ya tienes una cuenta?{' '}
                  <button
                    type="button"
                    onClick={() => cambiarModo('login')}
                    disabled={cargando}
                  >
                    Inicia sesión
                  </button>
                </p>
              )}
            </div>
          </div>
        </div>
      )}
    </>
  )
}

export default AuthPanel
