import { Link } from 'react-router-dom'
import type { SearchResult } from '../api/types'
import { PageState, TagList } from '../components/Layout'

export function MaterialResultList({
  results,
  emptyMessage,
  showProject = false,
  showMatchCount = false,
}: {
  results: SearchResult[]
  emptyMessage: string
  showProject?: boolean
  showMatchCount?: boolean
}) {
  if (results.length === 0) {
    return <PageState>{emptyMessage}</PageState>
  }

  return (
    <div className="material-list">
      {results.map((result) => (
        <article className="material-card" key={result.id}>
          <div className={`type-badge type-badge--${result.type.toLowerCase()}`}>
            {result.type === 'TEXT' ? 'Text' : 'Files'}
          </div>
          <div className="material-card__body">
            <div className="material-card__title">
              <h3>
                <Link to={`/materials/${result.id}${result.matchedFileId ? `?highlightFile=${result.matchedFileId}` : ''}`}>
                  {result.title}
                </Link>
              </h3>
              <span>v{result.currentVersionNumber}</span>
            </div>
            {showProject && (
              <p>
                Project: <Link to={`/projects/${result.projectId}`}>{result.projectTitle}</Link>
              </p>
            )}
            {result.location && <p>{result.location}</p>}
            <TagList tags={result.manualTags} kind="manual" />
            {showMatchCount && result.matchedTermCount > 0 && (
              <p className="match-note">
                Matched {result.matchedTermCount} {result.matchedTermCount === 1 ? 'keyword' : 'keywords'}
              </p>
            )}
            {result.matchedFileId && <p className="match-note">Matched an AI-analyzed image</p>}
          </div>
        </article>
      ))}
    </div>
  )
}
