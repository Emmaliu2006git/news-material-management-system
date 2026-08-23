import { useEffect, useRef, useState } from 'react'
import type { ChangeEvent, FormEvent } from 'react'
import { Link, useNavigate, useParams } from 'react-router-dom'
import { api, ApiError } from '../api/client'
import type { FileAsset, FileSelection, Material, VersionInput } from '../api/types'
import { PageState } from '../components/Layout'
import {
  createUploadSession,
  eventTime,
  idempotencyKey,
  optional,
  tags,
  uploadPendingFiles,
  uploadSessionIsValid,
} from './formUtils'
import type { UploadProgress, UploadSession } from './formUtils'

type SubmissionAttempt = { fingerprint: string; key: string; payload: VersionInput }

type OrderedFile =
  | { key: string; kind: 'EXISTING'; asset: FileAsset }
  | { key: string; kind: 'UPLOAD'; file: File; progress: UploadProgress }

function localDateTime(value: string | null) {
  if (!value) return ''
  const date = new Date(value)
  return new Date(date.getTime() - date.getTimezoneOffset() * 60_000).toISOString().slice(0, 16)
}

export function VersionEditorPage() {
  const { id = '' } = useParams()
  const navigate = useNavigate()
  const [material, setMaterial] = useState<Material | null>(null)
  const [orderedFiles, setOrderedFiles] = useState<OrderedFile[]>([])
  const [loading, setLoading] = useState(true)
  const [submitting, setSubmitting] = useState(false)
  const [error, setError] = useState('')
  const uploadSession = useRef<UploadSession | null>(null)
  const submission = useRef<SubmissionAttempt | null>(null)

  useEffect(() => {
    api.material(id)
      .then((loaded) => {
        setMaterial(loaded)
        if (loaded.version.snapshot.content.kind === 'FILE') {
          setOrderedFiles(loaded.version.snapshot.content.files.map((asset) => ({
            key: asset.id,
            kind: 'EXISTING' as const,
            asset,
          })))
        }
      })
      .catch((caught) => setError(caught instanceof ApiError ? caught.message : 'The material could not be loaded.'))
      .finally(() => setLoading(false))
  }, [id])

  function move(index: number, direction: -1 | 1) {
    uploadSession.current = null
    submission.current = null
    setOrderedFiles((current) => {
      const nextIndex = index + direction
      if (nextIndex < 0 || nextIndex >= current.length) return current
      const next = [...current]
      ;[next[index], next[nextIndex]] = [next[nextIndex], next[index]]
      return next
    })
  }

  function remove(index: number) {
    uploadSession.current = null
    submission.current = null
    setOrderedFiles((current) => current.filter((_, position) => position !== index))
  }

  function addFiles(event: ChangeEvent<HTMLInputElement>) {
    const selected = Array.from(event.currentTarget.files ?? [])
    if (orderedFiles.length + selected.length > 10) {
      setError('A version can contain no more than 10 files.')
      return
    }
    uploadSession.current = null
    submission.current = null
    setOrderedFiles((current) => [
      ...current,
      ...selected.map((file, index) => ({
        key: `new-${Date.now()}-${index}`,
        kind: 'UPLOAD' as const,
        file,
        progress: { name: file.name, percent: 0, status: 'hashing' as const },
      })),
    ])
  }

  async function submit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault()
    if (!material) return
    setError('')
    if (material.type === 'FILE' && orderedFiles.length === 0) {
      setError('A file version must keep or add at least one file.')
      return
    }
    setSubmitting(true)
    const data = new FormData(event.currentTarget)
    const common = {
      expectedCurrentVersion: material.version.versionNumber,
      title: String(data.get('title')).trim(),
      eventTime: eventTime(data.get('eventTime')),
      location: optional(data.get('location')),
      manualTags: tags(data.get('manualTags')),
    }
    try {
      let payload: VersionInput
      if (material.type === 'TEXT') {
        payload = { ...common, textBody: String(data.get('textBody')).trim() }
      } else {
        const fresh = orderedFiles.filter((item): item is Extract<OrderedFile, { kind: 'UPLOAD' }> => item.kind === 'UPLOAD')
        let session = uploadSession.current
        if (!session || !uploadSessionIsValid(session)) {
          session = fresh.length
            ? await createUploadSession(fresh.map((item) => item.file), (index, progress) => {
              const key = fresh[index].key
              setOrderedFiles((current) => current.map((item) =>
                item.key === key && item.kind === 'UPLOAD' ? { ...item, progress } : item,
              ))
            })
            : { intents: [], uploaded: new Set() }
          uploadSession.current = session
        }
        await uploadPendingFiles(fresh.map((item) => item.file), session, (index, progress) => {
          const key = fresh[index].key
          setOrderedFiles((current) => current.map((item) =>
            item.key === key && item.kind === 'UPLOAD' ? { ...item, progress } : item,
          ))
        })
        const intentIds = new Map(fresh.map((item, index) => [item.key, session.intents[index].intentId]))
        const files: FileSelection[] = orderedFiles.map((item) => item.kind === 'EXISTING'
          ? { kind: 'EXISTING', fileId: item.asset.id, uploadIntentId: null }
          : { kind: 'UPLOAD', fileId: null, uploadIntentId: intentIds.get(item.key)! })
        payload = { ...common, textContent: optional(data.get('textContent')), files }
      }
      const fingerprint = JSON.stringify(payload)
      if (submission.current?.fingerprint !== fingerprint) {
        submission.current = { fingerprint, key: idempotencyKey(), payload }
      }
      const attempt = submission.current
      const created = await api.createVersion(id, attempt.payload, attempt.key)
      navigate(`/materials/${created.id}`, { replace: true })
    } catch (caught) {
      if (caught instanceof ApiError && caught.code === 'VERSION_CONFLICT') {
        setError('A newer version exists. Reload this page before creating another version.')
      } else {
        setError(caught instanceof Error ? caught.message : 'The version could not be created.')
      }
    } finally {
      setSubmitting(false)
    }
  }

  if (loading) return <PageState>Loading the current snapshot…</PageState>
  if (!material) return <PageState kind="alert">{error || 'Material not found.'}</PageState>
  if (!material.canEdit) return <PageState kind="alert">Only the material owner can create a new version.</PageState>

  const snapshot = material.version.snapshot
  const content = snapshot.content
  return (
    <div className="page page--narrow">
      <div className="breadcrumbs"><Link to={`/materials/${id}`}>{snapshot.title}</Link><span>/</span><span>New version</span></div>
      <section className="page-heading">
        <div><p className="eyebrow">Owner workflow</p><h1>Create new version</h1><p>This saves a complete immutable snapshot. Version {material.version.versionNumber} remains unchanged.</p></div>
      </section>
      {error && <div role="alert" className="inline-message inline-message--error">{error}</div>}
      <form className="panel material-form" onSubmit={submit}>
        <div className="form-grid">
          <label className="form-grid__wide"><span>Title <b aria-label="required">*</b></span><input name="title" required defaultValue={snapshot.title} /></label>
          <label><span>Event time <small>optional</small></span><input name="eventTime" type="datetime-local" defaultValue={localDateTime(snapshot.eventTime)} /></label>
          <label><span>Location <small>optional</small></span><input name="location" defaultValue={snapshot.location ?? ''} /></label>
          <label className="form-grid__wide"><span>Manual tags <small>optional, comma-separated</small></span><input name="manualTags" defaultValue={snapshot.manualTags.join(', ')} /></label>
          {content.kind === 'TEXT' ? (
            <label className="form-grid__wide"><span>Text body <b aria-label="required">*</b></span><textarea name="textBody" required rows={10} defaultValue={content.textBody} /></label>
          ) : (
            <>
              <label className="form-grid__wide"><span>Material notes <small>optional</small></span><textarea name="textContent" rows={5} defaultValue={content.textContent ?? ''} /></label>
              <section className="form-grid__wide ordered-files" aria-labelledby="ordered-files-heading">
                <div className="section-heading"><h2 id="ordered-files-heading">Files in this version</h2><span>{orderedFiles.length}/10</span></div>
                <ol>
                  {orderedFiles.map((item, index) => {
                    const name = item.kind === 'EXISTING' ? item.asset.originalFilename : item.file.name
                    return (
                      <li key={item.key}>
                        <span className="order-number">{index + 1}</span>
                        <div className="grow"><strong>{name}</strong><small>{item.kind === 'EXISTING' ? 'Existing immutable file' : `New file · ${item.progress.status} ${item.progress.percent}%`}</small></div>
                        <div className="row-actions">
                          <button type="button" className="icon-button" disabled={index === 0} aria-label={`Move ${name} up`} onClick={() => move(index, -1)}>↑</button>
                          <button type="button" className="icon-button" disabled={index === orderedFiles.length - 1} aria-label={`Move ${name} down`} onClick={() => move(index, 1)}>↓</button>
                          <button type="button" className="icon-button icon-button--danger" aria-label={`Remove ${name}`} onClick={() => remove(index)}>×</button>
                        </div>
                      </li>
                    )
                  })}
                </ol>
                <label className="file-adder"><span>Add files <small>optional</small></span><input type="file" multiple onChange={addFiles} accept="image/*,video/*,audio/*,.pdf,.txt" /></label>
              </section>
            </>
          )}
        </div>
        <div className="form-actions">
          <Link className="button button--quiet" to={`/materials/${id}`}>Cancel</Link>
          <button className="button button--primary" disabled={submitting} type="submit">{submitting ? 'Saving…' : 'Save new version'}</button>
        </div>
      </form>
    </div>
  )
}
