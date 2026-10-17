
import { useEffect, useState } from 'react'
import './PostComments.css'

function PostComments({ postId, token }) {
  const [abierto, setAbierto] = useState(false)
  const [comentarios, setComentarios] = useState([])
  const [contenido, setContenido] = useState('')

  const [cargando, setCargando] = useState(false)
  const [enviando, setEnviando] = useState(false)

  const [errorCarga, setErrorCarga] = useState('')
  const [errorEnvio, setErrorEnvio] = useState('')
  const [mensaje, setMensaje] = useState('')

  const [recargar, setRecargar] = useState(0)

  // Consultar comentarios cuando se abre la seccion
  useEffect(() => {
    if (!abierto) return

    const controller = new AbortController()

    async function cargarComentarios() {
      setCargando(true)
      setErrorCarga('')

      try {
        const respuesta = await fetch(
          `/api/v1/posts/${postId}/comments`,
          { signal: controller.signal }
        )

        if (!respuesta.ok) {
          throw new Error(
            `No se pudieron consultar los comentarios. HTTP ${respuesta.status}`
          )
        }

        const datos = await respuesta.json()

        if (!Array.isArray(datos)) {
          throw new Error('La API no devolvió una lista válida.')
        }

        setComentarios(datos)
      } catch (err) {
        if (err.name !== 'AbortError') {
          setErrorCarga(err.message)
        }
      } finally {
        if (!controller.signal.aborted) {
          setCargando(false)
        }
      }
    }

    cargarComentarios()

    return () => controller.abort()
  }, [abierto, postId, recargar])

  // Publicar comentario utilizando JWT
  async function publicarComentario(event) {
    event.preventDefault()

    if (enviando) return

    setErrorEnvio('')
    setMensaje('')

    if (!token) {
      setErrorEnvio('Debes iniciar sesión para comentar.')
      return
    }

    const texto = contenido.trim()

    if (texto.length < 2 || texto.length > 5000) {
      setErrorEnvio(
        'El comentario debe tener entre 2 y 5000 caracteres.'
      )
      return
    }

    setEnviando(true)

    try {
      const respuesta = await fetch(
        `/api/v1/posts/${postId}/comments`,
        {
          method: 'POST',
          headers: {
            'Content-Type': 'application/json',
            Authorization: `Bearer ${token}`,
          },
          body: JSON.stringify({
            content: texto,
          }),
        }
      )

      if (!respuesta.ok) {
        if (respuesta.status === 401) {
          throw new Error(
            'Tu sesión expiró o no es válida. Inicia sesión nuevamente.'
          )
        }

        if (respuesta.status === 400) {
          throw new Error(
            'Revisa el contenido de tu comentario.'
          )
        }

        if (respuesta.status === 404) {
          throw new Error(
            'La publicación ya no está disponible.'
          )
        }

        if (respuesta.status === 503) {
          throw new Error(
            'Auth Service no está disponible.'
          )
        }

        throw new Error(
          `No se pudo publicar el comentario. HTTP ${respuesta.status}`
        )
      }

      setContenido('')
      setMensaje('Comentario publicado correctamente.')

      // Actualizar la lista sin recargar toda la pagina
      setRecargar((anterior) => anterior + 1)
    } catch (err) {
      setErrorEnvio(
        err instanceof TypeError
          ? 'No se pudo conectar con Forum Service.'
          : err.message
      )
    } finally {
      setEnviando(false)
    }
  }

  function formatearFecha(fecha) {
    if (!fecha) return 'Sin fecha'

    const valor = new Date(fecha)

    if (Number.isNaN(valor.getTime())) {
      return 'Sin fecha'
    }

    return valor.toLocaleString('es-PE', {
      day: '2-digit',
      month: 'short',
      year: 'numeric',
      hour: '2-digit',
      minute: '2-digit',
    })
  }

  return (
    <div className="comentarios-bloque">
      <button
        type="button"
        className="comentarios-toggle"
        onClick={() => setAbierto(!abierto)}
        aria-expanded={abierto}
      >
        {abierto
          ? '− Ocultar respuestas'
          : '💬 Ver respuestas y comentar'}
      </button>

      {abierto && (
        <div className="comentarios-contenido">
          <h4>Respuestas de la comunidad</h4>

          {cargando ? (
            <p className="comentarios-info">
              Cargando comentarios...
            </p>
          ) : errorCarga ? (
            <p className="comentarios-error" role="alert">
              {errorCarga}
            </p>
          ) : comentarios.length === 0 ? (
            <p className="comentarios-info">
              Todavía no hay respuestas. ¡Sé el primero en comentar!
            </p>
          ) : (
            <div className="comentarios-lista">
              {comentarios.map((comentario) => (
                <div
                  className="comentario-tarjeta"
                  key={comentario.id}
                >
                  <div className="comentario-cabecera">
                    <strong>
                      👤 Usuario #{comentario.authorId}
                    </strong>

                    <span>
                      {formatearFecha(comentario.createdAt)}
                    </span>
                  </div>

                  <p>{comentario.content}</p>
                </div>
              ))}
            </div>
          )}

          {token ? (
            <form
              className="comentarios-formulario"
              onSubmit={publicarComentario}
            >
              <label htmlFor={`comentario-${postId}`}>
                Escribir una respuesta
              </label>

              <textarea
                id={`comentario-${postId}`}
                placeholder="Comparte tus conocimientos y ayuda a resolver esta pregunta..."
                value={contenido}
                onChange={(e) => setContenido(e.target.value)}
                minLength={2}
                maxLength={5000}
                rows={4}
                required
              />

              <span className="comentarios-contador">
                {contenido.length}/5000 caracteres
              </span>

              {errorEnvio && (
                <p className="comentarios-error" role="alert">
                  {errorEnvio}
                </p>
              )}

              {mensaje && (
                <p className="comentarios-exito" role="status">
                  {mensaje}
                </p>
              )}

              <button
                type="submit"
                className="comentarios-publicar"
                disabled={enviando}
              >
                {enviando
                  ? 'Publicando...'
                  : 'Publicar comentario'}
              </button>
            </form>
          ) : (
            <div className="comentarios-invitado">
              Inicia sesión para responder esta publicación.
            </div>
          )}
        </div>
      )}
    </div>
  )
}

export default PostComments
