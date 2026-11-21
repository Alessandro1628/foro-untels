
import { useEffect, useState } from 'react'
import AuthPanel from './AuthPanel'
import CreatePostForm from './CreatePostForm'
import PostComments from './PostComments'
import Profile from './Profile'
import Moderation from './Moderation'
import ContentActions from './ContentActions'
import { api } from './api'
import './App.css'


function App() {
  const [auth, setAuth] = useState({ user: null, token: null })
  const [view, setView] = useState('foro')
  const [restoreAttempt, setRestoreAttempt] = useState(0)
  const [restoreError, setRestoreError] = useState('')
  useEffect(() => {
    const token = sessionStorage.getItem('foro.session')
    if (!token) return
    const controller = new AbortController()
    api('auth/me', { token, signal: controller.signal }).then(user => {
      if (sessionStorage.getItem('foro.session') === token) {
        setAuth({ user, token }); setRestoreError('')
      }
    }).catch(e => {
      if (controller.signal.aborted || sessionStorage.getItem('foro.session') !== token) return
      if (e.status === 401) sessionStorage.removeItem('foro.session')
      else setRestoreError('No se pudo recuperar el perfil.')
    })
    return () => controller.abort()
  }, [restoreAttempt])

  function login(session) {
    sessionStorage.setItem('foro.session', session.token)
    setRestoreError('')
    setAuth(session)
  }
  function logout() {
    if (!window.confirm('¿Cerrar sesión? Tu sesión actual se eliminará de este navegador.')) return
    sessionStorage.removeItem('foro.session')
    setAuth({ user: null, token: null })
    setView('foro')
  }
  function refresh() { setReloadKey(n => n + 1) }

  const [preguntas, setPreguntas] = useState([])
  const [reloadKey, setReloadKey] = useState(0)
  const [busqueda, setBusqueda] = useState('')
  const [categoria, setCategoria] = useState('Todas')
  const [cargando, setCargando] = useState(true)
  const [error, setError] = useState('')

  // Obtener las publicaciones reales de Forum Service
  useEffect(() => {
    const controller = new AbortController()

    async function cargarPreguntas() {
      try {
        const respuesta = await fetch('/api/v1/posts', {
          signal: controller.signal,
        })

        if (!respuesta.ok) {
          throw new Error(
            `Error al consultar publicaciones: ${respuesta.status}`
          )
        }

        const datos = await respuesta.json()

        if (!Array.isArray(datos)) {
          throw new Error('La API no devolvio una lista de publicaciones')
        }

        setPreguntas(datos)
        setError('')
      } catch (err) {
        if (err.name !== 'AbortError') {
          setError('No se pudieron cargar las publicaciones.')
          console.error(err)
        }
      } finally {
        if (!controller.signal.aborted) {
          setCargando(false)
        }
      }
    }

    cargarPreguntas()

    return () => controller.abort()
  }, [reloadKey])

  // Crear categorias con los datos reales
  const categorias = [
    'Todas',
    ...new Set(
      preguntas.filter(pregunta => pregunta.status === 'PUBLISHED')
        .map(pregunta => pregunta.category || 'General')
    ),
  ]

  // Filtrar publicaciones por categoria y busqueda
  const preguntasFiltradas = preguntas.filter((pregunta) => {
    const coincideCategoria =
      categoria === 'Todas' ||
      (pregunta.status === 'PUBLISHED' && (pregunta.category || 'General') === categoria)

    const texto = `
      ${pregunta.title || ''}
      ${pregunta.content || ''}
    `.toLowerCase()

    const coincideBusqueda = texto.includes(
      busqueda.trim().toLowerCase()
    )

    return coincideCategoria && coincideBusqueda
  })

  function formatearFecha(fecha) {
    if (!fecha) return 'Sin fecha'

    return new Date(fecha).toLocaleDateString('es-PE', {
      day: '2-digit',
      month: 'short',
      year: 'numeric',
    })
  }

  return (
    <div className="app">
      <header className="encabezado">
        <div className="encabezado-contenido">
          <div className="marca">
            <div className="marca-icono">U</div>
            <div>
              <strong>Foro Académico</strong>
              <span>UNTELS</span>
            </div>
          </div>

          <nav className="navegacion" aria-label="Navegación principal">
            <button
              type="button"
              className={view === 'foro' ? 'navegacion-activa' : ''}
              onClick={() => setView('foro')}
            >
              Inicio
            </button>
            <button
              type="button"
              className={view === 'comunidad' ? 'navegacion-activa' : ''}
              onClick={() => setView(view === 'comunidad' ? 'foro' : 'comunidad')}
            >
              Comunidad universitaria
            </button>
          </nav>


          <AuthPanel user={auth.user} onLogin={login} onLogout={logout}
            onProfile={() => setView(view === 'perfil' ? 'foro' : 'perfil')} />

        </div>
      </header>

      <main className="contenedor">
        <aside className="panel-categorias">
          <h3>Explorar</h3>
          <button className={`categoria ${view === 'comunidad' ? 'categoria-activa' : ''}`}
            onClick={() => setView(view === 'comunidad' ? 'foro' : 'comunidad')}>
            <i className="bi bi-people-fill" aria-hidden="true"></i> Comunidad universitaria
          </button>
          {['MODERATOR', 'ADMIN'].includes(auth.user?.role) && <button className="categoria"
            onClick={() => setView(view === 'reportes' ? 'foro' : 'reportes')}>
            {view === 'reportes' ? 'Volver al foro' : 'Reportes pendientes'}
          </button>}
          <p className="etiqueta-lateral">CATEGORÍAS</p>

          {categorias.map((item) => (
            <button
              key={item}
              className={`categoria ${
                categoria === item ? 'categoria-activa' : ''
              }`}
              onClick={() => { setCategoria(item); setView('foro') }}
            >
              {item}
            </button>
          ))}

          <div className="nota-lateral">
            <strong>Aprende en comunidad</strong>
            <p>
              Comparte tus dudas, intercambia conocimientos
              y colabora con otros estudiantes.
            </p>
          </div>
        </aside>

        <section className="contenido-principal">
          {restoreError && <p role="alert">{restoreError} <button onClick={() => setRestoreAttempt(n => n + 1)}>Reintentar sesión</button></p>}
          {view === 'perfil' && auth.user ? <Profile user={auth.user} token={auth.token}
            onSave={user => setAuth(current => ({ ...current, user }))} onClose={() => setView('foro')} />
          : view === 'reportes' && auth.user ? <Moderation token={auth.token} onChanged={refresh} />
          : view === 'comunidad' ? (
            <section className="vista-comunidad" aria-labelledby="titulo-comunidad">
              <div className="bienvenida comunidad-hero">
                <p className="subtitulo">COMUNIDAD UNIVERSITARIA UNTELS</p>
                <h1 id="titulo-comunidad">Conecta, comparte y aprende.</h1>
                <p>Encuentra espacios académicos y participa en conversaciones con la comunidad.</p>
              </div>
              <div className="comunidad-grid">
                {[
                  ['bi-people-fill', 'Comunidades académicas', 'Explora temas por carrera, área de conocimiento y proyecto.'],
                  ['bi-chat-square-text-fill', 'Conversaciones abiertas', 'Comparte dudas, propuestas y respuestas entre estudiantes.'],
                  ['bi-mortarboard-fill', 'Aprendizaje colaborativo', 'Construye conocimiento con apoyo de la comunidad.'],
                ].map(([icon, title, text]) => (
                  <article className="comunidad-card" key={title}>
                    <i className={`bi ${icon}`} aria-hidden="true"></i>
                    <h2>{title}</h2>
                    <p>{text}</p>
                  </article>
                ))}
              </div>
              <button type="button" className="btn-universitario" onClick={() => setView('foro')}>
                <i className="bi bi-arrow-left" aria-hidden="true"></i>
                Volver al foro
              </button>
            </section>
          )
          : <>
          <div className="bienvenida">
            <p className="subtitulo">
              COMUNIDAD ACADÉMICA UNTELS
            </p>
            <h1>Aprendemos mejor juntos.</h1>
            <p>
              Un espacio para preguntar, compartir conocimientos
              y resolver dudas académicas.
            </p>
          </div>


          <CreatePostForm key={auth.user?.id ?? 'visitante'}
            token={auth.token}
            onCreated={refresh}
          />


          <div className="buscador">
            <input
              type="search"
              aria-label="Buscar publicaciones"
              placeholder="Buscar preguntas o temas académicos..."
              value={busqueda}
              onChange={(e) => setBusqueda(e.target.value)}
            />
          </div>

          <div className="titulo-seccion">
            <div>
              <h2>Preguntas recientes</h2>
              <p>
                Explora las conversaciones de nuestra comunidad.
              </p>
            </div>
            <span className="contador">
              {preguntasFiltradas.length} preguntas
            </span>
          </div>

          <div className="lista-preguntas">
            {cargando ? (
              <div className="sin-resultados">
                Cargando publicaciones...
              </div>
            ) : error ? (
              <div className="sin-resultados">
                {error}
                <button onClick={refresh}>Reintentar carga</button>
              </div>
            ) : preguntasFiltradas.length === 0 ? (
              <div className="sin-resultados">
                {preguntas.length === 0
                  ? 'Todavía no hay publicaciones en el foro.'
                  : 'No encontramos preguntas con esos filtros.'}
              </div>
            ) : (
              preguntasFiltradas.map((pregunta) => (
                <article
                  className="tarjeta-pregunta"
                  key={pregunta.id}
                >
                  {pregunta.category && <span className="etiqueta-categoria">
                    {pregunta.category || 'General'}
                  </span>}

                  {pregunta.communityName && <p>Comunidad: {pregunta.communityName}</p>}
                  <h3>{pregunta.title}</h3>

                  <p>{pregunta.content}</p>

                  <div className="pie-pregunta">
                    <span>
                      {pregunta.authorId ? 'Usuario #' + pregunta.authorId : 'Autoría oculta'}
                    </span>

                    <span>
                      📅 {formatearFecha(pregunta.createdAt)}
                    </span>
                  </div>

                  <ContentActions postId={pregunta.id} item={pregunta} userId={auth.user?.id} token={auth.token} onChange={refresh} />
                  <PostComments key={auth.user?.id ?? 'visitante'} userId={auth.user?.id} withdrawn={pregunta.status === 'WITHDRAWN'} onThreadChange={refresh}
                    postId={pregunta.id}
                    token={auth.token}
                  />

                </article>
              ))
            )}
          </div>

          <p className="aviso-demo">
            Publicaciones obtenidas desde Forum Service
            y PostgreSQL.
          </p>
          </>}
        </section>

        <aside className="panel-derecho">
          <div className="tarjeta-informativa">
            <h3>Bienvenido al foro</h3>
            <p>
              Conecta con estudiantes, comparte recursos
              y encuentra respuestas a tus preguntas académicas.
            </p>
          </div>

          <div className="tarjeta-informativa">
            <h3>¿Qué podrás hacer?</h3>
            <p>✓ Publicar preguntas</p>
            <p>✓ Responder comentarios</p>
            <p>✓ Compartir conocimientos</p>
            <p>✓ Aprender colaborativamente</p>
          </div>
        </aside>
      </main>
    </div>
  )
}

export default App
