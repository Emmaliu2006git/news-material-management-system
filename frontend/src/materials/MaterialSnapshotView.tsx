import { useState } from 'react'
import { api, ApiError } from '../api/client'
import type { FileAsset, Material } from '../api/types'
import { TagList } from '../components/Layout'
import { formatEnglishDateTime } from '../dateTime'

function bytes(value: number) {
  if (value < 1024) return `${value} B`
  if (value < 1024 * 1024) return `${(value / 1024).toFixed(1)} KB`
  return `${(value / 1024 / 1024).toFixed(1)} MB`
}

function FileCard({
  file,
  matched,
  canEdit,
}: {
  file: FileAsset
  matched: boolean
  canEdit: boolean
}) {
  const [analysisStatus, setAnalysisStatus] = useState(file.imageAnalysis?.status)
  const [message, setMessage] = useState('')
  const [downloading, setDownloading] = useState(false)

  async function download() {
    setDownloading(true)
    setMessage('')
    try {
      const { downloadUrl } = await api.downloadUrl(file.id)
      const anchor = document.createElement('a')
      anchor.href = downloadUrl
      anchor.download = file.originalFilename
      anchor.rel = 'noopener'
      anchor.click()
    } catch (caught) {
      setMessage(caught instanceof ApiError ? caught.message : 'The download could not be prepared.')
    } finally {
      setDownloading(false)
    }
  }

  async function retry() {
    setMessage('')
    try {
      const result = await api.retryAnalysis(file.id)
      setAnalysisStatus(result.status as typeof analysisStatus)
    } catch (caught) {
      setMessage(caught instanceof ApiError ? caught.message : 'AI analysis could not be retried.')
    }
  }

  return (
    <article
      aria-label={file.originalFilename}
      className={`file-card${matched ? ' file-card--matched' : ''}`}
    >
      <div className="file-card__top">
        <div className="file-icon" aria-hidden="true">{file.mimeType.startsWith('image/') ? 'IMG' : 'FILE'}</div>
        <div className="grow">
          <h3>{file.originalFilename}</h3>
          <p>{file.mimeType} · {bytes(file.sizeBytes)}</p>
        </div>
        {matched && <span className="match-pill">Search match</span>}
      </div>
      <dl className="compact-meta">
        <div><dt>Uploader</dt><dd>{file.uploaderUsername}</dd></div>
        <div><dt>MIME type</dt><dd>{file.mimeType}</dd></div>
        <div><dt>File size</dt><dd>{bytes(file.sizeBytes)}</dd></div>
        <div><dt>Uploaded</dt><dd>{formatEnglishDateTime(file.fileCreatedTime)}</dd></div>
      </dl>
      <section className="file-system-details" aria-labelledby={`file-system-details-${file.id}`}>
        <h4 id={`file-system-details-${file.id}`}>File system details</h4>
        <dl className="compact-meta compact-meta--system">
          <div><dt>File ID</dt><dd>{file.id}</dd></div>
          <div><dt>Uploader ID</dt><dd>{file.uploaderId}</dd></div>
          <div><dt>SHA-256 checksum</dt><dd>{file.checksumSha256}</dd></div>
        </dl>
      </section>
      {file.imageAnalysis && (
        <section className="analysis-panel" aria-label="AI analysis">
          <div className="analysis-panel__heading">
            <h4>AI image analysis</h4>
            <span className={`status-pill status-pill--${analysisStatus?.toLowerCase()}`}>{analysisStatus}</span>
          </div>
          {file.imageAnalysis.aiDescription && <p>{file.imageAnalysis.aiDescription}</p>}
          <TagList tags={file.imageAnalysis.aiTags} kind="ai" />
          {file.imageAnalysis.generatedTime && (
            <p className="analysis-generated">
              Generated {formatEnglishDateTime(file.imageAnalysis.generatedTime)}
            </p>
          )}
          {canEdit && analysisStatus === 'FAILED' && (
            <button className="button button--small" type="button" onClick={retry}>Retry AI analysis</button>
          )}
        </section>
      )}
      <button className="button button--secondary button--small" type="button" disabled={downloading} onClick={download}>
        {downloading ? 'Preparing…' : `Download ${file.originalFilename}`}
      </button>
      {message && <div role="alert" className="inline-message inline-message--error">{message}</div>}
    </article>
  )
}

export function MaterialSnapshotView({
  material,
  highlightedFileId,
}: {
  material: Material
  highlightedFileId?: string | null
}) {
  const { snapshot } = material.version
  const content = snapshot.content

  return (
    <article className="snapshot">
      <header className="snapshot__header">
        <div>
          <div className={`type-badge type-badge--${material.type.toLowerCase()}`}>{content.kind === 'TEXT' ? 'Text material' : 'File material'}</div>
          <h1>{snapshot.title}</h1>
        </div>
        <div className="version-chip">Version {material.version.versionNumber}</div>
      </header>
      <dl className="metadata-grid">
        <div><dt>Reporting project</dt><dd>{material.projectTitle}</dd></div>
        <div><dt>Owner</dt><dd>{material.ownerUsername}</dd></div>
        <div><dt>Material created</dt><dd>{formatEnglishDateTime(material.materialCreatedTime)}</dd></div>
        <div><dt>Material type</dt><dd>{content.kind === 'TEXT' ? 'Text' : 'File'}</dd></div>
        <div><dt>Version number</dt><dd>{material.version.versionNumber}</dd></div>
        <div><dt>Version creator</dt><dd>{material.version.creatorUsername}</dd></div>
        <div><dt>Version created</dt><dd>{formatEnglishDateTime(material.version.versionCreatedTime)}</dd></div>
        {snapshot.eventTime && <div><dt>Event time</dt><dd>{formatEnglishDateTime(snapshot.eventTime)}</dd></div>}
        {snapshot.location && <div><dt>Location</dt><dd>{snapshot.location}</dd></div>}
      </dl>
      <section className="snapshot__section system-details" aria-labelledby="system-details-heading">
        <h2 id="system-details-heading">System details</h2>
        <dl className="compact-meta compact-meta--system">
          <div><dt>Material ID</dt><dd>{material.id}</dd></div>
          <div><dt>Project ID</dt><dd>{material.projectId}</dd></div>
          <div><dt>Owner ID</dt><dd>{material.ownerId}</dd></div>
          <div><dt>Current version ID</dt><dd>{material.currentVersionId}</dd></div>
          <div><dt>Version ID</dt><dd>{material.version.id}</dd></div>
          <div><dt>Version creator ID</dt><dd>{material.version.createdBy}</dd></div>
        </dl>
      </section>
      <section className="snapshot__section" aria-labelledby="manual-tags-heading">
        <h2 id="manual-tags-heading">Manual tags</h2>
        {snapshot.manualTags.length ? <TagList tags={snapshot.manualTags} kind="manual" /> : <p className="muted">No manual tags</p>}
      </section>
      {content.kind === 'TEXT' ? (
        <section className="snapshot__section prose" aria-labelledby="text-content-heading">
          <h2 id="text-content-heading">Text content</h2>
          <p>{content.textBody}</p>
        </section>
      ) : (
        <>
          {content.textContent && (
            <section className="snapshot__section prose" aria-labelledby="file-text-heading">
              <h2 id="file-text-heading">Material notes</h2>
              <p>{content.textContent}</p>
            </section>
          )}
          <section className="snapshot__section" aria-labelledby="files-heading">
            <div className="section-heading"><h2 id="files-heading">Files</h2><span>{content.files.length}</span></div>
            <div className="file-list">
              {content.files.map((file) => (
                <FileCard
                  key={file.id}
                  file={file}
                  matched={file.id === highlightedFileId}
                  canEdit={material.canEdit && !material.historical}
                />
              ))}
            </div>
          </section>
        </>
      )}
    </article>
  )
}
