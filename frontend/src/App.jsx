
import { useEffect, useState } from 'react'
import AuthPanel from './AuthPanel'
import CreatePostForm from './CreatePostForm'
import PostComments from './PostComments'
import './App.css'


function App() {
  const [auth, setAuth] = useState({
    user: null,
    token: null,
  })

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
      preguntas.map((pregunta) => pregunta.category || 'General')
    ),
  ]

  // Filtrar publicaciones por categoria y busqueda
  const preguntasFiltradas = preguntas.filter((pregunta) => {
    const coincideCategoria =
      categoria === 'Todas' ||
      (pregunta.category || 'General') === categoria

    const texto = `
      ${pregunta.title || ''}
      ${pregunta.content || ''}
      ${pregunta.category || ''}
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

          <nav className="navegacion">
            <span className="navegacion-activa">Inicio</span>
            <span>Comunidad universitaria</span>
          </nav>


<AuthPanel
  user={auth.user}
  onLogin={(sesion) => setAuth(sesion)}
  onLogout={() =>
    setAuth({
      user: null,
      token: null,
    })
  }
/>

        </div>
      </header>

      <main className="contenedor">
        <aside className="panel-categorias">
          <h3>Explorar</h3>
          <p className="etiqueta-lateral">CATEGORÍAS</p>

          {categorias.map((item) => (
            <button
              key={item}
              className={`categoria ${
                categoria === item ? 'categoria-activa' : ''
              }`}
              onClick={() => setCategoria(item)}
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


          <CreatePostForm
            token={auth.token}
            onCreated={() => {
              setReloadKey((anterior) => anterior + 1)
              }}
          />


          <div className="buscador">
            <input
              type="search"
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
                <p>Comprueba que Forum Service esté funcionando.</p>
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
                  <span className="etiqueta-categoria">
                    {pregunta.category || 'General'}
                  </span>

                  <h3>{pregunta.title}</h3>

                  <p>{pregunta.content}</p>

                  <div className="pie-pregunta">
                    <span>
                      👤 Usuario #{pregunta.authorId}
                    </span>

                    <span>
                      📅 {formatearFecha(pregunta.createdAt)}
                    </span>
                  </div>

                  <PostComments
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
