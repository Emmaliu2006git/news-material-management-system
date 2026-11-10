import { useEffect, useState } from 'react'
import { Link, useParams } from 'react-router-dom'
import { api, ApiError } from '../api/client'
import type { Project, SearchResult } from '../api/types'
import { PageState } from '../components/Layout'
import { MaterialResultList } from '../materials/MaterialResultList'

export function ProjectDetailPage() {
  const { id = '' } = useParams()
  const [project, setProject] = useState<Project | null>(null)
  const [results, setResults] = useState<SearchResult[]>([])
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState('')

  useEffect(() => {
    Promise.all([api.listProjects(), api.projectMaterials(id)])
      .then(([projects, materials]) => {
        setProject(projects.find((item) => item.id === id) ?? null)
        setResults(materials)
      })
      .catch((caught) => setError(caught instanceof ApiError ? caught.message : 'The project could not be loaded.'))
      .finally(() => setLoading(false))
  }, [id])

  if (loading) return <PageState>Loading project…</PageState>
  if (!project) return <PageState kind="alert">{error || 'Project not found.'}</PageState>

  return (
    <div className="page">
      <div className="breadcrumbs"><Link to="/projects">Projects</Link><span>/</span><span>{project.title}</span></div>
      <section className="page-heading page-heading--action">
        <div>
          <p className="eyebrow">Reporting project</p>
          <h1>{project.title}</h1>
          <p>Browse the current version of every material in this project.</p>
        </div>
        <Link className="button button--primary" to={`/projects/${id}/new`}>Add material</Link>
      </section>

      {error && <div role="alert" className="inline-message inline-message--error">{error}</div>}

      <section aria-labelledby="materials-heading">
        <div className="section-heading">
          <h2 id="materials-heading">Current materials</h2>
          <span>{results.length} found</span>
        </div>
        <MaterialResultList results={results} emptyMessage="No materials have been added to this project yet." />
      </section>
    </div>
  )
}
