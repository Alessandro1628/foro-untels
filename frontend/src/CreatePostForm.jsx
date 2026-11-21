
import { useEffect, useState } from 'react'
import { api } from './api'
import './CreatePostForm.css'

function CreatePostForm({ token, onCreated }) {
  const [mostrarFormulario, setMostrarFormulario] = useState(false)
  const [titulo, setTitulo] = useState('')
  const [contenido, setContenido] = useState('')
  const [categoria, setCategoria] = useState('General')
  const [cargando, setCargando] = useState(false)
  const [error, setError] = useState('')
  const [exito, setExito] = useState('')

  const [categorias,setCategorias] = useState([])
  const [comunidades, setComunidades] = useState([])
  const [comunidadId, setComunidadId] = useState('')

  useEffect(() => {
    if (!mostrarFormulario) return
    const controller = new AbortController()
    Promise.all([api('posts/communities', { signal: controller.signal }), api('academic/catalogs/categories',{signal:controller.signal})])
      .then(([lista, options]) => {
        if (controller.signal.aborted) return
        const names=options.filter(c=>c.active).map(c=>c.name)
        setCategorias(names)
        setCategoria(names.includes('General')?'General':(names[0]??''))
        setComunidades(lista)
        if (!lista.length) setError('Todavía no hay comunidades disponibles.')
      })
      .catch(() => {
        if (!controller.signal.aborted) setError('No se pudieron cargar las comunidades. Cierra y vuelve a abrir el formulario.')
      })
    return () => controller.abort()
  }, [mostrarFormulario])

  async function publicarPregunta(event) {
    event.preventDefault()

    if (!token || cargando) return

    const tituloLimpio = titulo.trim()
    const contenidoLimpio = contenido.trim()

    if (!tituloLimpio || contenidoLimpio.length < 10) {
      setError(
        'Ingresa un título y una descripción de al menos 10 caracteres.'
      )
      return
    }

    setCargando(true)
    setError('')
    setExito('')

    try {
      const respuesta = await fetch('/api/v1/posts', {
        method: 'POST',
        headers: {
          'Content-Type': 'application/json',
          Authorization: `Bearer ${token}`,
        },
        body: JSON.stringify({
          title: tituloLimpio,
          content: contenidoLimpio,
          category: categoria,
          communityId: Number(comunidadId),
        }),
      })

      if (!respuesta.ok) {
        if (respuesta.status === 401) {
          throw new Error(
            'Tu sesión no es válida o ha expirado. Inicia sesión nuevamente.'
          )
        }

        if (respuesta.status === 400) {
          throw new Error(
            'Revisa los datos de la pregunta e inténtalo nuevamente.'
          )
        }

        if (respuesta.status === 503) {
          throw new Error(
            'Auth Service no está disponible en este momento.'
          )
        }

        throw new Error(
          `No se pudo publicar la pregunta. Código: ${respuesta.status}`
        )
      }

      const resultado = await respuesta.json()

      setTitulo('')
      setContenido('')
      setCategoria('General')
      setMostrarFormulario(false)

      setExito(
        `Pregunta publicada correctamente. ID: ${resultado.postId}`
      )

      // Solicitar a App.jsx que actualice las publicaciones
      onCreated()
    } catch (err) {
      setError(
        err instanceof TypeError
          ? 'No se pudo conectar con Forum Service.'
          : err.message
      )
    } finally {
      setCargando(false)
    }
  }

  if (!token) {
    return (
      <div className="publicacion-invitado">
        <strong>¿Tienes una duda académica?</strong>
        <p>
          Inicia sesión para publicar tus preguntas y participar
          en la comunidad.
        </p>
      </div>
    )
  }

  return (
    <section className="publicacion-contenedor">
      <div className="publicacion-encabezado">
        <div>
          <h3>¿Qué quieres preguntar?</h3>
          <p>
            Comparte tus dudas con la comunidad universitaria.
          </p>
        </div>

        <button
          type="button"
          className="publicacion-boton"
          onClick={() => {
            setComunidades([])
            setComunidadId('')
            setMostrarFormulario(!mostrarFormulario)
            setError('')
            setExito('')
          }}
          disabled={cargando}
        >
          {mostrarFormulario ? 'Cancelar' : '+ Nueva pregunta'}
        </button>
      </div>

      {exito && (
        <div className="publicacion-exito" role="status">
          {exito}
        </div>
      )}

      {mostrarFormulario && (
        <form
          className="publicacion-formulario"
          onSubmit={publicarPregunta}
        >
          <label htmlFor="post-titulo">
            Título de la pregunta
          </label>

          <input
            id="post-titulo"
            type="text"
            placeholder="Ej. ¿Cómo implementar JWT en Spring Boot?"
            value={titulo}
            onChange={(e) => setTitulo(e.target.value)}
            maxLength={180}
            required
          />

          <label htmlFor="post-categoria">
            Categoría académica
          </label>

          <select
            id="post-categoria"
            value={categoria}
            onChange={(e) => setCategoria(e.target.value)}
            required
          >
            {categorias.map((item) => (
              <option key={item} value={item}>
                {item}
              </option>
            ))}
          </select>

          <label htmlFor="post-comunidad">Comunidad</label>
          <select
            id="post-comunidad"
            value={comunidadId}
            onChange={e => setComunidadId(e.target.value)}
            required
          >
            <option value="">Selecciona una comunidad</option>
            {comunidades.map(comunidad => (
              <option key={comunidad.id} value={comunidad.id}>{comunidad.name}</option>
            ))}
          </select>

          <label htmlFor="post-contenido">
            Describe tu pregunta
          </label>

          <textarea
            id="post-contenido"
            placeholder="Explica tu duda con suficiente detalle para que otros estudiantes puedan ayudarte..."
            rows={6}
            minLength={10}
            maxLength={10000}
            value={contenido}
            onChange={(e) => setContenido(e.target.value)}
            required
          />

          {error && (
            <div className="publicacion-error" role="alert">
              {error}
            </div>
          )}

          <button
            className="publicacion-enviar"
            type="submit"
            disabled={cargando}
          >
            {cargando ? 'Publicando...' : 'Publicar pregunta'}
          </button>
        </form>
      )}
    </section>
  )
}

export default CreatePostForm
