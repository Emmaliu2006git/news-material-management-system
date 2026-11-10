import { useCallback, useEffect, useRef, useState } from 'react'
import type { FormEvent } from 'react'
import { api, ApiError } from '../api/client'
import type { GlobalSearchFilters, SearchResult } from '../api/types'
import { EnglishDateInput } from '../components/EnglishDateInput'
import { PageState } from '../components/Layout'
import { englishDateBoundary } from '../dateTime'
import { MaterialResultList } from './MaterialResultList'

export function MaterialSearchPage() {
  const [results, setResults] = useState<SearchResult[]>([])
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState('')
  const requestSequence = useRef(0)

  const runSearch = useCallback(async (filters: GlobalSearchFilters = {}) => {
    const requestId = ++requestSequence.current
    setLoading(true)
    setError('')
    try {
      const nextResults = await api.globalSearch(filters)
      if (requestId === requestSequence.current) {
        setResults(nextResults)
      }
    } catch (caught) {
      if (requestId === requestSequence.current) {
        setResults([])
        setError(caught instanceof ApiError ? caught.message : 'Materials could not be searched.')
      }
    } finally {
      if (requestId === requestSequence.current) {
        setLoading(false)
      }
    }
  }, [])

  useEffect(() => {
    void runSearch()
  }, [runSearch])

  async function submit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault()
    if (loading) return
    try {
      const data = new FormData(event.currentTarget)
      await runSearch({
        q: String(data.get('q') ?? ''),
        fileType: (String(data.get('fileType') ?? 'ALL') || 'ALL') as GlobalSearchFilters['fileType'],
        eventFrom: englishDateBoundary(data.get('eventFrom'), 'Event time from'),
        eventTo: englishDateBoundary(data.get('eventTo'), 'Event time to', true),
        location: String(data.get('location') ?? ''),
      })
    } catch (caught) {
      setError(caught instanceof Error ? caught.message : 'Enter dates as MM/DD/YYYY.')
    }
  }

  return (
    <div className="page">
      <section className="page-heading">
        <div>
          <p className="eyebrow">All reporting projects</p>
          <h1>Search materials</h1>
          <p>Searches include current material versions only. Separate keywords with commas; matching any keyword is enough.</p>
        </div>
      </section>

      <form className="search-panel" onSubmit={submit} aria-label="Global material search">
        <label className="search-panel__query">
          <span>Keywords</span>
          <input name="q" type="search" placeholder="Title, text, manual or AI tags, or AI description" />
        </label>
        <label>
          <span>File type</span>
          <select name="fileType" defaultValue="ALL">
            <option value="ALL">All</option>
            <option value="TEXT">Text</option>
            <option value="IMAGE">Image</option>
            <option value="VIDEO">Video</option>
            <option value="AUDIO">Audio</option>
            <option value="OTHER">Other file</option>
          </select>
        </label>
        <label>
          <span>Location</span>
          <input name="location" />
        </label>
        <label>
          <span>Event time from</span>
          <EnglishDateInput name="eventFrom" />
        </label>
        <label>
          <span>Event time to</span>
          <EnglishDateInput name="eventTo" />
        </label>
        <button className="button button--secondary" type="submit" disabled={loading}>
          {loading ? 'Searching…' : 'Search'}
        </button>
      </form>
      {error && <div role="alert" className="inline-message inline-message--error">{error}</div>}

      <section aria-labelledby="search-results-heading">
        <div className="section-heading">
          <h2 id="search-results-heading">Current materials</h2>
          <span>{results.length} found</span>
        </div>
        {loading
          ? <PageState>Loading materials…</PageState>
          : <MaterialResultList
              results={results}
              emptyMessage="No current materials match these filters."
              showProject
              showMatchCount
            />}
      </section>
    </div>
  )
}
