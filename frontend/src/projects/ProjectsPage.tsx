import { useEffect, useState } from 'react'
import type { FormEvent } from 'react'
import { Link } from 'react-router-dom'
import { api, ApiError } from '../api/client'
import type { Project } from '../api/types'
import { PageState } from '../components/Layout'

export function ProjectsPage() {
  const [projects, setProjects] = useState<Project[]>([])
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState('')
  const [creating, setCreating] = useState(false)

  useEffect(() => {
    api.listProjects()
      .then(setProjects)
      .catch((caught) => setError(caught instanceof ApiError ? caught.message : 'Projects could not be loaded.'))
      .finally(() => setLoading(false))
  }, [])

  async function create(event: FormEvent<HTMLFormElement>) {
    event.preventDefault()
    setError('')
    setCreating(true)
    const form = event.currentTarget
    const title = String(new FormData(form).get('title')).trim()
    try {
      const created = await api.createProject(title)
      setProjects((current) => [created, ...current])
      form.reset()
    } catch (caught) {
      setError(caught instanceof ApiError ? caught.message : 'The project could not be created.')
    } finally {
      setCreating(false)
    }
  }

  return (
    <div className="page">
      <section className="page-heading">
        <div>
          <p className="eyebrow">Shared reporting workspace</p>
          <h1>Reporting projects</h1>
          <p>Each project groups independent text and file materials for one reporting event.</p>
        </div>
      </section>

      <section className="panel panel--compact" aria-labelledby="create-project-heading">
        <h2 id="create-project-heading">Start a project</h2>
        <form className="inline-form" onSubmit={create}>
          <label className="grow">
            <span>New project title</span>
            <input name="title" maxLength={160} required placeholder="e.g. Downtown flood" />
          </label>
          <button className="button button--primary" disabled={creating} type="submit">
            {creating ? 'Creating…' : 'Create project'}
          </button>
        </form>
        {error && <div role="alert" className="inline-message inline-message--error">{error}</div>}
      </section>

      {loading ? <PageState>Loading projects…</PageState> : (
        <section aria-labelledby="project-list-heading">
          <div className="section-heading">
            <h2 id="project-list-heading">All projects</h2>
            <span>{projects.length} total</span>
          </div>
          {projects.length === 0 ? <PageState>No projects yet. Create the first one above.</PageState> : (
            <div className="card-grid">
              {projects.map((project) => (
                <Link
                  aria-label={`Open project ${project.title}`}
                  className="project-card"
                  key={project.id}
                  to={`/projects/${project.id}`}
                >
                  <div className="project-card__icon" aria-hidden="true">PR</div>
                  <div>
                    <h3>{project.title}</h3>
                    <p>Created by {project.creatorUsername}</p>
                  </div>
                  <span className="project-card__action" aria-hidden="true">
                    Open project <span className="card-arrow">→</span>
                  </span>
                </Link>
              ))}
            </div>
          )}
        </section>
      )}
    </div>
  )
}
