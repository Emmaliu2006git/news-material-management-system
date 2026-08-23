import { render, screen, waitFor } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { MemoryRouter } from 'react-router-dom'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import { App } from '../App'
import { fileMaterial, json, project, textMaterial, user } from '../test/fixtures'

function renderRoute(route: string) {
  return render(<MemoryRouter initialEntries={[route]}><App /></MemoryRouter>)
}

function baseRoute(input: RequestInfo | URL) {
  const url = String(input)
  if (url.endsWith('/auth/me')) return json(user)
  if (url.endsWith('/auth/csrf')) return json({ token: 'csrf-token', headerName: 'X-XSRF-TOKEN' })
  if (url.endsWith('/projects')) return json([project])
  return null
}

function uploadedFile(name = 'street.jpg') {
  const file = new File(['image'], name, { type: 'image/jpeg' })
  Object.defineProperty(file, 'arrayBuffer', {
    value: async () => new TextEncoder().encode('image').buffer,
  })
  return file
}

class AutoUploadRequest extends EventTarget {
  upload = new EventTarget()
  status = 204
  open() {}
  setRequestHeader() {}
  send() {
    queueMicrotask(() => {
      this.upload.dispatchEvent(new ProgressEvent('progress', {
        lengthComputable: true,
        loaded: 5,
        total: 5,
      }))
      this.dispatchEvent(new Event('load'))
    })
  }
}

describe('new material', () => {
  beforeEach(() => {
    vi.stubGlobal('XMLHttpRequest', AutoUploadRequest)
    vi.spyOn(crypto.subtle, 'digest').mockResolvedValue(new Uint8Array(32).buffer)
  })

  it('creates a complete TEXT material snapshot', async () => {
    let payload: Record<string, unknown> | undefined
    vi.spyOn(globalThis, 'fetch').mockImplementation((input, init) => {
      const base = baseRoute(input)
      if (base) return base
      if (String(input).endsWith('/materials/text')) {
        payload = JSON.parse(String(init?.body))
        return json(textMaterial, 201)
      }
      if (String(input).endsWith('/materials/material-text/versions')) return json([textMaterial.version])
      if (String(input).endsWith('/materials/material-text')) return json(textMaterial)
      return json({}, 404)
    })
    const actor = userEvent.setup()
    renderRoute('/projects/project-flood/new')

    await actor.click(await screen.findByRole('radio', { name: /text material/i }))
    await actor.type(screen.getByLabelText(/^title/i), 'River level update')
    await actor.type(screen.getByLabelText(/text body/i), 'Water reached the second marker.')
    await actor.type(screen.getByLabelText(/location/i), 'South bridge')
    await actor.type(screen.getByLabelText(/manual tags/i), 'urgent, river')
    await actor.click(screen.getByRole('button', { name: /create material/i }))

    expect(await screen.findByRole('heading', { name: 'River level update' })).toBeInTheDocument()
    expect(payload).toEqual({
      projectId: 'project-flood',
      title: 'River level update',
      eventTime: null,
      location: 'South bridge',
      manualTags: ['urgent', 'river'],
      textBody: 'Water reached the second marker.',
    })
  })

  it('uploads FILE material directly with visible progress before ordered finalization', async () => {
    let finalizePayload: Record<string, unknown> | undefined
    vi.spyOn(globalThis, 'fetch').mockImplementation((input, init) => {
      const base = baseRoute(input)
      if (base) return base
      const url = String(input)
      if (url.endsWith('/uploads/intents')) {
        return json({ files: [{
          intentId: 'intent-photo',
          objectKey: 'uploads/photo',
          uploadUrl: '/api/v1/local-storage/uploads/intent-photo',
          requiredHeaders: { 'Content-Type': 'image/jpeg' },
          expiresAt: '2026-08-22T16:00:00Z',
        }] }, 201)
      }
      if (url.endsWith('/materials/file')) {
        finalizePayload = JSON.parse(String(init?.body))
        return json(fileMaterial, 201)
      }
      if (url.endsWith('/materials/material-file/versions')) return json([fileMaterial.version])
      if (url.endsWith('/materials/material-file')) return json(fileMaterial)
      return json({}, 404)
    })
    const actor = userEvent.setup()
    renderRoute('/projects/project-flood/new')

    await actor.click(await screen.findByRole('radio', { name: /file material/i }))
    await actor.type(screen.getByLabelText(/^title/i), 'Street evidence')
    await actor.upload(screen.getByLabelText(/^files/i), uploadedFile())
    await actor.type(screen.getByLabelText(/material notes/i), 'Photos from the east entrance.')
    await actor.click(screen.getByRole('button', { name: /create material/i }))

    await waitFor(() => expect(finalizePayload).toBeDefined())
    expect(await screen.findByRole('heading', { name: 'Street evidence' })).toBeInTheDocument()
    expect(finalizePayload).toEqual({
      projectId: 'project-flood',
      title: 'Street evidence',
      eventTime: null,
      location: null,
      manualTags: [],
      textContent: 'Photos from the east entrance.',
      uploadIntentIds: ['intent-photo'],
    })
  })

  it('keeps the form retryable and never finalizes after an interrupted upload', async () => {
    class FailedUploadRequest extends AutoUploadRequest {
      send() {
        queueMicrotask(() => this.dispatchEvent(new Event('error')))
      }
    }
    vi.stubGlobal('XMLHttpRequest', FailedUploadRequest)
    const api = vi.spyOn(globalThis, 'fetch').mockImplementation((input) => {
      const base = baseRoute(input)
      if (base) return base
      if (String(input).endsWith('/uploads/intents')) {
        return json({ files: [{
          intentId: 'intent-photo',
          objectKey: 'uploads/photo',
          uploadUrl: 'https://storage.example/upload',
          requiredHeaders: { 'Content-Type': 'image/jpeg' },
          expiresAt: '2026-08-22T16:00:00Z',
        }] }, 201)
      }
      return json({}, 404)
    })
    const actor = userEvent.setup()
    renderRoute('/projects/project-flood/new')

    await actor.click(await screen.findByRole('radio', { name: /file material/i }))
    await actor.type(screen.getByLabelText(/^title/i), 'Street evidence')
    await actor.upload(screen.getByLabelText(/^files/i), uploadedFile())
    await actor.click(screen.getByRole('button', { name: /create material/i }))

    expect(await screen.findByRole('alert')).toHaveTextContent(/retry safely/i)
    expect(api.mock.calls.some(([url]) => String(url).endsWith('/materials/file'))).toBe(false)
    expect(screen.getByRole('button', { name: /create material/i })).toBeEnabled()
  })
})

describe('owner version editor', () => {
  beforeEach(() => {
    vi.stubGlobal('XMLHttpRequest', AutoUploadRequest)
    vi.spyOn(crypto.subtle, 'digest').mockResolvedValue(new Uint8Array(32).buffer)
  })

  it('prefills the FILE snapshot and sends ordered discriminated selections', async () => {
    let versionPayload: Record<string, unknown> | undefined
    vi.spyOn(globalThis, 'fetch').mockImplementation((input, init) => {
      const base = baseRoute(input)
      if (base) return base
      const url = String(input)
      if (url.endsWith('/uploads/intents')) {
        return json({ files: [{
          intentId: 'intent-new',
          objectKey: 'uploads/new',
          uploadUrl: '/api/v1/local-storage/uploads/intent-new',
          requiredHeaders: { 'Content-Type': 'image/jpeg' },
          expiresAt: '2026-08-22T16:00:00Z',
        }] }, 201)
      }
      if (url.endsWith('/materials/material-file') && (!init?.method || init.method === 'GET')) return json(fileMaterial)
      if (url.endsWith('/materials/material-file/versions') && init?.method === 'POST') {
        versionPayload = JSON.parse(String(init.body))
        return json({
          ...fileMaterial,
          currentVersionId: 'version-file-2',
          version: { ...fileMaterial.version, id: 'version-file-2', versionNumber: 2 },
        }, 201)
      }
      if (url.endsWith('/materials/material-file/versions')) return json([fileMaterial.version])
      return json({}, 404)
    })
    const actor = userEvent.setup()
    renderRoute('/materials/material-file/edit')

    expect(await screen.findByDisplayValue('Street evidence')).toBeInTheDocument()
    await actor.click(screen.getByRole('button', { name: /move street.jpg down/i }))
    await actor.upload(screen.getByLabelText(/add files/i), uploadedFile('new.jpg'))
    await actor.click(screen.getByRole('button', { name: /move new.jpg up/i }))
    await actor.click(screen.getByRole('button', { name: /save new version/i }))

    await waitFor(() => expect(versionPayload).toEqual({
      expectedCurrentVersion: 1,
      title: 'Street evidence',
      eventTime: null,
      location: null,
      manualTags: ['field'],
      textContent: 'Photos from the east entrance.',
      files: [
        { kind: 'EXISTING', fileId: 'file-note', uploadIntentId: null },
        { kind: 'UPLOAD', fileId: null, uploadIntentId: 'intent-new' },
        { kind: 'EXISTING', fileId: 'file-photo', uploadIntentId: null },
      ],
    }))
  })

  it('explains a stale-version conflict without mutating the current snapshot', async () => {
    vi.spyOn(globalThis, 'fetch').mockImplementation((input, init) => {
      const base = baseRoute(input)
      if (base) return base
      const url = String(input)
      if (url.endsWith('/materials/material-text') && (!init?.method || init.method === 'GET')) return json(textMaterial)
      if (url.endsWith('/materials/material-text/versions') && init?.method === 'POST') {
        return json({
          code: 'VERSION_CONFLICT',
          message: 'The material has a newer version',
          requestId: 'request-conflict',
          details: [],
        }, 409)
      }
      return json({}, 404)
    })
    const actor = userEvent.setup()
    renderRoute('/materials/material-text/edit')

    await actor.clear(await screen.findByLabelText(/text body/i))
    await actor.type(screen.getByLabelText(/text body/i), 'A revised observation.')
    await actor.click(screen.getByRole('button', { name: /save new version/i }))
    expect(await screen.findByRole('alert')).toHaveTextContent(/newer version.*reload/i)
  })
})
