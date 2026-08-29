import { useCallback, useEffect, useState } from 'react'
import type { FormEvent } from 'react'
import { Link, useParams } from 'react-router-dom'
import { api, ApiError } from '../api/client'
import type { Project, SearchFilters, SearchResult } from '../api/types'
import { EnglishDateInput } from '../components/EnglishDateInput'
import { PageState, TagList } from '../components/Layout'
import { englishDateBoundary } from '../dateTime'

export function ProjectDetailPage() {
  const { id = '' } = useParams()
  const [project, setProject] = useState<Project | null>(null)
  const [results, setResults] = useState<SearchResult[]>([])
  const [initialResults, setInitialResults] = useState<SearchResult[]>([])
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState('')

  const search = useCallback(async (filters: SearchFilters = {}) => {
    setError('')
    try {
      setResults(await api.search(id, filters))
    } catch (caught) {
      setError(caught instanceof ApiError ? caught.message : 'Materials could not be searched.')
    }
  }, [id])

  useEffect(() => {
    Promise.all([api.listProjects(), api.search(id)])
      .then(([projects, materials]) => {
        setProject(projects.find((item) => item.id === id) ?? null)
        setResults(materials)
        setInitialResults(materials)
      })
      .catch((caught) => setError(caught instanceof ApiError ? caught.message : 'The project could not be loaded.'))
      .finally(() => setLoading(false))
  }, [id])

  async function submit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault()
    setError('')
    try {
      const data = new FormData(event.currentTarget)
      await search({
        q: String(data.get('q') ?? ''),
        materialType: (String(data.get('materialType') ?? '') || undefined) as SearchFilters['materialType'],
        ownerId: String(data.get('ownerId') ?? ''),
        createdFrom: englishDateBoundary(data.get('createdFrom'), 'Material created from'),
        createdTo: englishDateBoundary(data.get('createdTo'), 'Material created to', true),
        eventFrom: englishDateBoundary(data.get('eventFrom'), 'Event time from'),
        eventTo: englishDateBoundary(data.get('eventTo'), 'Event time to', true),
        location: String(data.get('location') ?? ''),
        tag: String(data.get('tag') ?? ''),
      })
    } catch (caught) {
      setError(caught instanceof Error ? caught.message : 'Enter dates as MM/DD/YYYY.')
    }
  }

  const owners = Array.from(new Map(
    initialResults.map((result) => [result.ownerId, result.ownerUsername]),
  )).sort((left, right) => left[1].localeCompare(right[1]))

  if (loading) return <PageState>Loading project…</PageState>
  if (!project) return <PageState kind="alert">{error || 'Project not found.'}</PageState>

  return (
    <div className="page">
      <div className="breadcrumbs"><Link to="/projects">Projects</Link><span>/</span><span>{project.title}</span></div>
      <section className="page-heading page-heading--action">
        <div>
          <p className="eyebrow">Reporting project</p>
          <h1>{project.title}</h1>
          <p>Searches include current material versions only.</p>
        </div>
        <Link className="button button--primary" to={`/projects/${id}/new`}>Add material</Link>
      </section>

      <form className="search-panel" onSubmit={submit} aria-label="Material search">
        <label className="search-panel__query">
          <span>Search current materials</span>
          <input name="q" type="search" placeholder="Title, text, tags, or image description" />
        </label>
        <label>
          <span>Material type</span>
          <select name="materialType" defaultValue="">
            <option value="">All types</option>
            <option value="TEXT">Text</option>
            <option value="FILE">File</option>
          </select>
        </label>
        <label>
          <span>Owner</span>
          <select name="ownerId" defaultValue="">
            <option value="">All owners</option>
            {owners.map(([ownerId, username]) => <option key={ownerId} value={ownerId}>{username}</option>)}
          </select>
        </label>
        <label>
          <span>Material created from</span>
          <EnglishDateInput name="createdFrom" />
        </label>
        <label>
          <span>Material created to</span>
          <EnglishDateInput name="createdTo" />
        </label>
        <label>
          <span>Event time from</span>
          <EnglishDateInput name="eventFrom" />
        </label>
        <label>
          <span>Event time to</span>
          <EnglishDateInput name="eventTo" />
        </label>
        <label>
          <span>Location</span>
          <input name="location" />
        </label>
        <label>
          <span>Tag</span>
          <input name="tag" />
        </label>
        <button className="button button--secondary" type="submit">Search</button>
      </form>
      {error && <div role="alert" className="inline-message inline-message--error">{error}</div>}

      <section aria-labelledby="materials-heading">
        <div className="section-heading">
          <h2 id="materials-heading">Current materials</h2>
          <span>{results.length} found</span>
        </div>
        {results.length === 0 ? <PageState>No materials match these filters.</PageState> : (
          <div className="material-list">
            {results.map((result) => (
              <article className="material-card" key={result.id}>
                <div className={`type-badge type-badge--${result.type.toLowerCase()}`}>{result.type === 'TEXT' ? 'Text' : 'Files'}</div>
                <div className="material-card__body">
                  <div className="material-card__title">
                    <h3>
                      <Link to={`/materials/${result.id}${result.matchedFileId ? `?highlightFile=${result.matchedFileId}` : ''}`}>
                        {result.title}
                      </Link>
                    </h3>
                    <span>v{result.currentVersionNumber}</span>
                  </div>
                  {result.location && <p>{result.location}</p>}
                  <TagList tags={result.manualTags} kind="manual" />
                  {result.matchedFileId && <p className="match-note">Matched an AI-analyzed image</p>}
                </div>
              </article>
            ))}
          </div>
        )}
      </section>
    </div>
  )
}
