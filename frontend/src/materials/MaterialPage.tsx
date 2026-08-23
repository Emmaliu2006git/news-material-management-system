import { useEffect, useState } from 'react'
import { Link, useParams, useSearchParams } from 'react-router-dom'
import { api, ApiError } from '../api/client'
import type { Material, Version } from '../api/types'
import { PageState } from '../components/Layout'
import { MaterialSnapshotView } from './MaterialSnapshotView'

export function MaterialPage() {
  const { id = '', number } = useParams()
  const [params] = useSearchParams()
  const [material, setMaterial] = useState<Material | null>(null)
  const [versions, setVersions] = useState<Version[]>([])
  const [error, setError] = useState('')

  useEffect(() => {
    const requested = number ? api.historicalMaterial(id, Number(number)) : api.material(id)
    Promise.all([requested, api.versions(id)])
      .then(([loaded, history]) => {
        setMaterial(loaded)
        setVersions(history)
      })
      .catch((caught) => setError(caught instanceof ApiError ? caught.message : 'The material could not be loaded.'))
  }, [id, number])

  if (error) return <PageState kind="alert">{error}</PageState>
  if (!material) return <PageState>Loading material…</PageState>

  return (
    <div className="page page--material">
      <div className="breadcrumbs">
        <Link to="/projects">Projects</Link>
        <span>/</span>
        <Link to={`/projects/${material.projectId}`}>Project materials</Link>
        <span>/</span>
        <span>{material.version.snapshot.title}</span>
      </div>
      {material.historical && (
        <div role="status" className="history-banner">
          Historical version {material.version.versionNumber} · Read-only snapshot
          <Link to={`/materials/${material.id}`}>View current version</Link>
        </div>
      )}
      <div className="material-layout">
        <div>
          {!material.historical && material.canEdit && (
            <div className="action-bar">
              <Link className="button button--primary" to={`/materials/${material.id}/edit`}>Create new version</Link>
            </div>
          )}
          <MaterialSnapshotView material={material} highlightedFileId={params.get('highlightFile')} />
        </div>
        <aside className="version-panel" aria-labelledby="version-history-heading">
          <h2 id="version-history-heading">Version history</h2>
          <ol className="version-timeline">
            {[...versions].reverse().map((version) => {
              const current = version.id === material.currentVersionId
              return (
                <li key={version.id} className={current ? 'version-timeline__current' : ''}>
                  <Link to={current ? `/materials/${material.id}` : `/materials/${material.id}/versions/${version.versionNumber}`}>
                    <strong>Version {version.versionNumber}</strong>
                    <span>{version.snapshot.title}</span>
                    <time dateTime={version.versionCreatedTime}>{new Date(version.versionCreatedTime).toLocaleDateString()}</time>
                  </Link>
                </li>
              )
            })}
          </ol>
        </aside>
      </div>
    </div>
  )
}
