import { useActions, useRows } from './useAcademic'
import { Form, Notice } from './academic'

export default function Communities({ auth, onRead }) {
  const { user, token } = auth
  const action = useActions(token)
  const directory = useRows('academic/communities', token, action.revision)
  const reviewer = ['ADMIN', 'MODERATOR'].includes(user?.role)
  const proposals = useRows(reviewer && 'academic/proposals', token, action.revision)
  return (
    <section className="academico">
      <h1>Comunidades</h1>
      <Notice action={action} sources={[directory, proposals]} />
      {directory.rows.map((c) => (
        <article key={c.id}>
          <h3>{c.name}</h3>
          <p>{c.purpose}</p>
          <button onClick={() => onRead(c)}>Ver publicaciones</button>
          {token && (
            <button
              disabled={action.busy}
              onClick={() =>
                action.act('academic/communities/' + c.id + '/follow', c.following ? 'DELETE' : 'PUT')
              }
            >
              {c.following ? 'Dejar de seguir' : 'Seguir comunidad'}
            </button>
          )}
        </article>
      ))}
      {token && (
        <>
          <h2>{user.role === 'ADMIN' ? 'Crear comunidad' : 'Proponer comunidad'}</h2>
          <Form
            submit={user.role === 'ADMIN' ? 'Crear comunidad' : 'Enviar propuesta'}
            disabled={action.busy}
            onSave={(data) =>
              action.act(
                user.role === 'ADMIN' ? 'academic/communities' : 'academic/proposals',
                'POST',
                Object.fromEntries(data),
              )
            }
          >
            <label>
              Nombre
              <input name="name" required maxLength={100} />
            </label>
            <label>
              Propósito
              <textarea name="purpose" required maxLength={500} />
            </label>
          </Form>
        </>
      )}
      {reviewer && (
        <>
          <h2>Propuestas pendientes</h2>
          {proposals.rows.map((p) => (
            <article key={p.id}>
              <strong>{p.name}</strong>
              <p>{p.purpose}</p>
              {[true, false].map((approve) => (
                <button
                  key={String(approve)}
                  disabled={action.busy}
                  onClick={() => action.act('academic/proposals/' + p.id, 'PUT', { approve })}
                >
                  {approve ? 'Aprobar propuesta' : 'Rechazar propuesta'}
                </button>
              ))}
            </article>
          ))}
        </>
      )}
    </section>
  )
}

function Catalog({ kind, title, token }) {
  const action = useActions(token)
  const path = 'academic/catalogs/' + kind
  const source = useRows(path, token, action.revision)
  return (
    <section>
      <h2>{title}</h2>
      <Notice action={action} sources={[source]} />
      <Form
        submit="Crear"
        disabled={action.busy}
        onSave={(data) => action.act(path, 'POST', Object.fromEntries(data))}
      >
        <label>
          Nuevo nombre
          <input name="name" required maxLength={80} />
        </label>
      </Form>
      {source.rows.map((item) => (
        <article key={item.id}>
          <strong>{item.name}</strong>
          {item.active ? (
            <>
              <Form
                submit="Renombrar"
                disabled={action.busy}
                onSave={(data) => action.act(path + '/' + item.id, 'PUT', Object.fromEntries(data))}
              >
                <label>
                  Nuevo nombre de {item.name}
                  <input name="name" required maxLength={80} />
                </label>
              </Form>
              <button disabled={action.busy} onClick={() => action.act(path + '/' + item.id, 'DELETE')}>
                Archivar {item.name}
              </button>
            </>
          ) : (
            ' · Archivada'
          )}
        </article>
      ))}
    </section>
  )
}
export function Administration({ token }) {
  const action = useActions(token)
  return (
    <section className="academico">
      <h1>Administración</h1>
      <Catalog kind="subjects" title="Asignaturas de biblioteca" token={token} />
      <Catalog kind="categories" title="Categorías del foro" token={token} />
      <h2>Permisos de moderación</h2>
      <Notice action={action} />
      <Form
        submit="Aplicar permiso"
        disabled={action.busy}
        onSave={(data) =>
          action.act('auth/moderators', 'PUT', {
            email: data.get('email'),
            granted: data.get('permission') === 'grant',
          })
        }
      >
        <label>
          Correo de la cuenta
          <input type="email" name="email" required />
        </label>
        <label>
          Operación
          <select name="permission">
            <option value="grant">Conceder moderación</option>
            <option value="revoke">Revocar moderación</option>
          </select>
        </label>
      </Form>
    </section>
  )
}
