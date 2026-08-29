import { useEffect, useRef, useState } from 'react'
import type { ChangeEvent, FormEvent } from 'react'
import { Link, useNavigate, useParams } from 'react-router-dom'
import { api, ApiError } from '../api/client'
import type { FileMaterialInput, MaterialType, Project, TextMaterialInput, UploadPolicy } from '../api/types'
import { EnglishDateInput } from '../components/EnglishDateInput'
import {
  createUploadSession,
  eventTime,
  idempotencyKey,
  isDefinitiveUploadIntentFailure,
  optional,
  tags,
  uploadPendingFiles,
  uploadSessionIsValid,
  validateFilePolicy,
} from './formUtils'
import type { UploadProgress, UploadSession } from './formUtils'

type SubmissionAttempt = { fingerprint: string; key: string; payload: TextMaterialInput | FileMaterialInput }

export function NewMaterialPage() {
  const { id: projectId = '' } = useParams()
  const navigate = useNavigate()
  const [project, setProject] = useState<Project | null>(null)
  const [uploadPolicy, setUploadPolicy] = useState<UploadPolicy | null>(null)
  const [type, setType] = useState<MaterialType>('TEXT')
  const [files, setFiles] = useState<File[]>([])
  const [progress, setProgress] = useState<UploadProgress[]>([])
  const [submitting, setSubmitting] = useState(false)
  const [error, setError] = useState('')
  const uploadSession = useRef<UploadSession | null>(null)
  const submission = useRef<SubmissionAttempt | null>(null)

  useEffect(() => {
    Promise.all([api.listProjects(), api.capabilities()])
      .then(([all, capabilities]) => {
        setProject(all.find((item) => item.id === projectId) ?? null)
        setUploadPolicy(capabilities.upload)
      })
      .catch((caught) => setError(caught instanceof Error ? caught.message : 'Upload limits could not be loaded.'))
  }, [projectId])

  function selectFiles(event: ChangeEvent<HTMLInputElement>) {
    const selected = Array.from(event.currentTarget.files ?? [])
    if (!uploadPolicy) {
      setError('Upload limits are unavailable. Reload the page.')
      return
    }
    const policyError = validateFilePolicy(selected, uploadPolicy)
    if (policyError) {
      setError(policyError)
      return
    }
    setFiles(selected)
    uploadSession.current = null
    submission.current = null
    setProgress(selected.map((file) => ({ name: file.name, percent: 0, status: 'hashing' })))
    setError('')
  }

  function chooseType(next: MaterialType) {
    setType(next)
    uploadSession.current = null
    submission.current = null
  }

  async function submit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault()
    setError('')
    if (type === 'FILE' && files.length === 0) {
      setError('File material requires at least one file.')
      return
    }
    if (type === 'FILE') {
      if (!uploadPolicy) {
        setError('Upload limits are unavailable. Reload the page.')
        return
      }
      const policyError = validateFilePolicy(files, uploadPolicy)
      if (policyError) {
        setError(policyError)
        return
      }
    }
    setSubmitting(true)
    try {
      const data = new FormData(event.currentTarget)
      const common = {
        projectId,
        title: String(data.get('title')).trim(),
        eventTime: eventTime(data.get('eventTime')),
        location: optional(data.get('location')),
        manualTags: tags(data.get('manualTags')),
      }
      let created
      if (type === 'TEXT') {
        const payload: TextMaterialInput = {
          ...common,
          textBody: String(data.get('textBody')).trim(),
        }
        const fingerprint = JSON.stringify(payload)
        if (submission.current?.fingerprint !== fingerprint) {
          submission.current = { fingerprint, key: idempotencyKey(), payload }
        }
        const attempt = submission.current
        created = await api.createText(attempt.payload as TextMaterialInput, attempt.key)
      } else {
        const textContent = optional(data.get('textContent'))
        const fingerprint = JSON.stringify({
          ...common,
          textContent,
          files: files.map((file) => ({
            name: file.name,
            type: file.type,
            size: file.size,
            lastModified: file.lastModified,
          })),
        })
        let attempt = submission.current
        if (attempt?.fingerprint !== fingerprint) {
          let session = uploadSession.current
          if (!session || !uploadSessionIsValid(session)) {
            session = await createUploadSession(files, (index, next) => {
              setProgress((current) => current.map((item, itemIndex) => itemIndex === index ? next : item))
            })
            uploadSession.current = session
          }
          await uploadPendingFiles(files, session, (index, next) => {
            setProgress((current) => current.map((item, itemIndex) => itemIndex === index ? next : item))
          })
          const payload: FileMaterialInput = {
            ...common,
            textContent,
            uploadIntentIds: session.intents.map((intent) => intent.intentId),
          }
          attempt = { fingerprint, key: idempotencyKey(), payload }
          submission.current = attempt
        }
        created = await api.createFile(attempt.payload as FileMaterialInput, attempt.key)
      }
      navigate(`/materials/${created.id}`, { replace: true })
    } catch (caught) {
      if (type === 'FILE' && isDefinitiveUploadIntentFailure(caught)) {
        uploadSession.current = null
        submission.current = null
        setProgress(files.map((file) => ({ name: file.name, percent: 0, status: 'hashing' })))
        setError(`${caught instanceof Error ? caught.message : 'The upload authorization is no longer valid.'} Retry to upload the files again safely.`)
      } else {
        setError(caught instanceof ApiError || caught instanceof Error
          ? caught.message
          : 'The material could not be created. You can retry safely.')
      }
    } finally {
      setSubmitting(false)
    }
  }

  return (
    <div className="page page--narrow">
      <div className="breadcrumbs">
        <Link to="/projects">Projects</Link><span>/</span>
        <Link to={`/projects/${projectId}`}>{project?.title ?? 'Project'}</Link><span>/</span>
        <span>New material</span>
      </div>
      <section className="page-heading">
        <div><p className="eyebrow">Add evidence</p><h1>Create material</h1><p>Choose one type. File materials may also include written notes.</p></div>
      </section>
      {error && <div role="alert" className="inline-message inline-message--error">{error}</div>}
      <form className="panel material-form" onSubmit={submit}>
        <fieldset className="type-picker">
          <legend>Material type</legend>
          <label className={type === 'TEXT' ? 'type-option type-option--selected' : 'type-option'}>
            <input type="radio" name="type" value="TEXT" checked={type === 'TEXT'} onChange={() => chooseType('TEXT')} />
            <span><strong>Text material</strong><small>A written field note or report.</small></span>
          </label>
          <label className={type === 'FILE' ? 'type-option type-option--selected' : 'type-option'}>
            <input type="radio" name="type" value="FILE" checked={type === 'FILE'} onChange={() => chooseType('FILE')} />
            <span><strong>File material</strong><small>Up to {uploadPolicy?.maxFiles ?? '…'} files, within the server limits.</small></span>
          </label>
        </fieldset>
        <div className="form-grid">
          <label className="form-grid__wide"><span>Title <b aria-label="required">*</b></span><input name="title" required maxLength={240} /></label>
          <label><span>Event time <small>optional</small></span><EnglishDateInput name="eventTime" withTime /></label>
          <label><span>Location <small>optional</small></span><input name="location" /></label>
          <label className="form-grid__wide"><span>Manual tags <small>optional, comma-separated</small></span><input name="manualTags" /></label>
          {type === 'TEXT' ? (
            <label className="form-grid__wide"><span>Text body <b aria-label="required">*</b></span><textarea name="textBody" required rows={10} /></label>
          ) : (
            <>
              <label className="form-grid__wide"><span>Files <b aria-label="required">*</b></span><input type="file" multiple onChange={selectFiles} accept="image/*,video/*,audio/*,.pdf,.txt" /></label>
              {progress.length > 0 && (
                <ul className="upload-list" aria-label="Upload progress">
                  {progress.map((item, index) => (
                    <li key={`${item.name}-${index}`}>
                      <span>{item.name}</span><progress max="100" value={item.percent}>{item.percent}%</progress><span>{item.status} · {item.percent}%</span>
                    </li>
                  ))}
                </ul>
              )}
              <label className="form-grid__wide"><span>Material notes <small>optional</small></span><textarea name="textContent" rows={5} /></label>
            </>
          )}
        </div>
        <div className="form-actions">
          <Link className="button button--quiet" to={`/projects/${projectId}`}>Cancel</Link>
          <button className="button button--primary" disabled={submitting} type="submit">
            {submitting ? (type === 'FILE' ? 'Uploading and creating…' : 'Creating…') : 'Create material'}
          </button>
        </div>
      </form>
    </div>
  )
}
