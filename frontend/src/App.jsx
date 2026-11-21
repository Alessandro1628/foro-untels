
import { useEffect, useState } from 'react'
import AuthPanel from './AuthPanel'
import CreatePostForm from './CreatePostForm'
import PostComments from './PostComments'
import Profile from './Profile'
import Moderation from './Moderation'
import ContentActions from './ContentActions'
import { api } from './api'
import Library from './Library'
import Tutoring from './Tutoring'
import Communities, { Administration } from './Communities'
import { useRows } from './useAcademic'
import './academic.css'
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
    setScope('ALL'); setCommunity(null); setFeedError('')
  }
  function refresh() { setReloadKey(n => n + 1) }

  const [scope,setScope] = useState('ALL')
  const [sort,setSort] = useState('RECENT')
  const [community,setCommunity] = useState(null)
  const [feedError,setFeedError] = useState('')
  async function mark(id, kind, remove=false) {
    try {
      await api('academic/feed/'+id+'/'+kind,{token:auth.token,method:remove?'DELETE':'PUT'})
      setFeedError('')
      refresh()
    } catch (err) { setFeedError(err.message) }
  }
  const [reloadKey, setReloadKey] = useState(0)
  const [busqueda, setBusqueda] = useState('')
  const [categoria, setCategoria] = useState('Todas')
  const params = new URLSearchParams({scope,sort,...(community && {community:community.id})})
  const feed = useRows('academic/feed?'+params,auth.token,reloadKey)
  const {rows:preguntas,loading:cargando,error} = feed

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

          <nav className="navegacion" aria-label="Navegacion principal">
            <button type="button" className={view === 'foro' ? 'navegacion-activa' : ''}
              onClick={() => setView('foro')}>Inicio</button>
            <button type="button" className={['comunidad','comunidades'].includes(view) ? 'navegacion-activa' : ''}
              onClick={() => setView(view === 'comunidad' ? 'comunidades' : 'comunidad')}>
              Comunidad universitaria
            </button>
            <button type="button" className={view === 'biblioteca' ? 'navegacion-activa' : ''}
              onClick={() => setView('biblioteca')}>Biblioteca</button>
            <button type="button" className={view === 'tutorias' ? 'navegacion-activa' : ''}
              onClick={() => setView('tutorias')}>Tutorías</button>
          </nav>


          <AuthPanel user={auth.user} onLogin={login} onLogout={logout}
            onProfile={() => setView(view === 'perfil' ? 'foro' : 'perfil')} />

        </div>
      </header>

      <main className="contenedor">
        <aside className="panel-categorias">
          <h3>Explorar</h3>
          {Object.entries({foro:'Foro',biblioteca:'Biblioteca',tutorias:'Tutorías',comunidades:'Comunidades',
            ...(auth.user?.role==='ADMIN' && {admin:'Administración'})}).map(([key,label])=>
            <button key={key} className={`categoria ${view===key?'categoria-activa':''}`}
              onClick={()=>{setView(key);setCommunity(null)}}>{label}</button>)}
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
          : view === 'biblioteca' ? <Library key={auth.user?.id ?? 'guest'} auth={auth}/>
          : view === 'tutorias' ? <Tutoring key={auth.user?.id ?? 'guest'} auth={auth}/>
          : view === 'admin' && auth.user?.role==='ADMIN' ? <Administration token={auth.token}/>
          : view === 'comunidad' ? (
            <section className="vista-comunidad" aria-labelledby="titulo-comunidad">
              <div className="bienvenida comunidad-hero">
                <p className="subtitulo">COMUNIDAD UNIVERSITARIA UNTELS</p>
                <h1 id="titulo-comunidad">Conecta, comparte y aprende.</h1>
                <p>Encuentra espacios académicos y participa en conversaciones con la comunidad.</p>
              </div>
              <div className="comunidad-grid">
                <button type="button" className="comunidad-card" onClick={() => setView('comunidades')}>
                  <i className="bi bi-people-fill" aria-hidden="true"></i>
                  <h2>Comunidades académicas</h2>
                  <p>Explora comunidades por carrera, área de conocimiento y proyecto.</p>
                  <span>Explorar comunidades <i className="bi bi-arrow-right" aria-hidden="true"></i></span>
                </button>
                <button type="button" className="comunidad-card" onClick={() => setView('foro')}>
                  <i className="bi bi-chat-square-text-fill" aria-hidden="true"></i>
                  <h2>Conversaciones abiertas</h2>
                  <p>Comparte dudas, propuestas y respuestas entre estudiantes.</p>
                  <span>Ir a conversaciones <i className="bi bi-arrow-right" aria-hidden="true"></i></span>
                </button>
                <button type="button" className="comunidad-card" onClick={() => setView('tutorias')}>
                  <i className="bi bi-mortarboard-fill" aria-hidden="true"></i>
                  <h2>Aprendizaje colaborativo</h2>
                  <p>Encuentra tutorías y construye conocimiento con apoyo de la comunidad.</p>
                  <span>Ver tutorías <i className="bi bi-arrow-right" aria-hidden="true"></i></span>
                </button>
              </div>
            </section>
          )
          : view === 'comunidades' ? <Communities auth={auth} onRead={c=>{setCommunity(c);setScope('ALL');setCategoria('Todas');setBusqueda('');setView('foro')}}/>
          : view === 'reportes' && auth.user ? <Moderation token={auth.token} onChanged={refresh} />
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


          <div className="feed-controls">
            <label>Orden<select value={sort} onChange={e=>setSort(e.target.value)}><option value="RECENT">Recientes</option><option value="FEATURED">Destacados</option></select></label>
            {auth.token && <label>Ver publicaciones<select value={scope} onChange={e=>{setScope(e.target.value);setCommunity(null)}}>
              <option value="ALL">Todas</option><option value="FOLLOWING">Mis comunidades</option><option value="FAVORITES">Mis favoritos</option>
            </select></label>}
            {community && <button onClick={()=>setCommunity(null)}>{community.name} · Quitar filtro</button>}
          </div>{feedError && <p role="alert">{feedError}</p>}
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
              <h2>{sort==='FEATURED'?'Preguntas destacadas':'Preguntas recientes'}</h2>
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

                  {pregunta.status==='PUBLISHED' && <div><span>{pregunta.votes} votos útiles</span>
                    {auth.token && <><button disabled={pregunta.voted} onClick={()=>mark(pregunta.id,'vote')}>{pregunta.voted?'Voto registrado':'Votar como útil'}</button>
                    <button onClick={()=>mark(pregunta.id,'favorite',pregunta.favorite)}>{pregunta.favorite?'Quitar de favoritos':'Guardar en favoritos'}</button></>}</div>}
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
