import { useState } from 'react'
import { api } from './api'

export default function BookPicker({ book, onSelect, browse=false }) {
  const [open, setOpen] = useState(false)
  const [term, setTerm] = useState('')
  const [results, setResults] = useState([])
  const [message, setMessage] = useState('')
  const [busy, setBusy] = useState(false)

  async function search() {
    if (!term.trim() || busy) return
    setBusy(true)
    setResults([])
    setMessage('')
    try {
      const books = await api('books?q=' + encodeURIComponent(term.trim()))
      setResults(books)
      if (!books.length) setMessage('No se encontraron libros.')
    } catch { setMessage('No se pudo consultar Google Books. Inténtalo de nuevo.') }
    finally { setBusy(false) }
  }
  if (book) return <div>{book.title} {browse && (book.previewUrl ? <a href={book.previewUrl} target="_blank" rel="noreferrer">Vista previa del libro</a> : <span>Vista previa no disponible</span>)} <button type="button" onClick={() => onSelect(null)}>Quitar libro</button></div>
  return <div className="referencia">
    <button type="button" onClick={() => setOpen(!open)}>{open ? 'Cerrar búsqueda' : browse ? 'Buscar en Google Books' : 'Adjuntar libro de Google Books'}</button>
    {open && <>
      <label>Título o autor<input maxLength={150} value={term} disabled={busy}
        onKeyDown={e => { if (e.key === 'Enter') { e.preventDefault(); search() } }}
        onChange={e => setTerm(e.target.value)} /></label>
      <button type="button" disabled={busy || !term.trim()} onClick={search}>{busy ? 'Buscando…' : 'Buscar libros'}</button>
      <p role="status">{message}</p>
      <ul>{results.map(b => <li key={b.id}><button type="button" onClick={() => onSelect(b)}>
        {b.title}{b.authors?.length ? ' · ' + b.authors.join(', ') : ''}
      </button></li>)}</ul>
    </>}
  </div>
}
