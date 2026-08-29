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
  if (url.endsWith('/capabilities')) {
    return json({ upload: { maxFiles: 10, maxFileBytes: 52428800, maxTotalBytes: 209715200 } })
  }
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
  send(body?: File) {
    void body
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
    const eventTimeInput = screen.getByLabelText(/event time/i)
    expect(eventTimeInput).toHaveAttribute('type', 'text')
    expect(eventTimeInput).toHaveAttribute('placeholder', 'MM/DD/YYYY HH:MM')
    await actor.type(eventTimeInput, '08/22/2026 06:30')
    await actor.type(screen.getByLabelText(/text body/i), 'Water reached the second marker.')
    await actor.type(screen.getByLabelText(/location/i), 'South bridge')
    await actor.type(screen.getByLabelText(/manual tags/i), 'urgent, river')
    await actor.click(screen.getByRole('button', { name: /create material/i }))

    expect(await screen.findByRole('heading', { name: 'River level update' })).toBeInTheDocument()
    expect(payload).toEqual({
      projectId: 'project-flood',
      title: 'River level update',
      eventTime: expect.stringMatching(/Z$/),
      location: 'South bridge',
      manualTags: ['urgent', 'river'],
      textBody: 'Water reached the second marker.',
    })
    const savedEventTime = new Date(String(payload?.eventTime))
    expect([
      savedEventTime.getFullYear(),
      savedEventTime.getMonth() + 1,
      savedEventTime.getDate(),
      savedEventTime.getHours(),
      savedEventTime.getMinutes(),
    ]).toEqual([2026, 8, 22, 6, 30])
  })

  it('replays a lost TEXT finalization with the same payload and idempotency key', async () => {
    const attempts: Array<{ key: string | null; body: string }> = []
    vi.spyOn(globalThis, 'fetch').mockImplementation((input, init) => {
      const base = baseRoute(input)
      if (base) return base
      const url = String(input)
      if (url.endsWith('/materials/text')) {
        attempts.push({
          key: new Headers(init?.headers).get('Idempotency-Key'),
          body: String(init?.body),
        })
        return attempts.length === 1
          ? Promise.reject(new TypeError('Response was lost'))
          : json(textMaterial, 201)
      }
      if (url.endsWith('/materials/material-text/versions')) return json([textMaterial.version])
      if (url.endsWith('/materials/material-text')) return json(textMaterial)
      return json({}, 404)
    })
    const actor = userEvent.setup()
    renderRoute('/projects/project-flood/new')

    await actor.type(await screen.findByLabelText(/^title/i), 'River level update')
    await actor.type(screen.getByLabelText(/text body/i), 'Water reached the second marker.')
    await actor.click(screen.getByRole('button', { name: /create material/i }))
    expect(await screen.findByRole('alert')).toHaveTextContent(/response was lost/i)

    await actor.click(screen.getByRole('button', { name: /create material/i }))
    expect(await screen.findByRole('heading', { name: 'River level update' })).toBeInTheDocument()
    expect(attempts).toHaveLength(2)
    expect(attempts[1]).toEqual(attempts[0])
  })

  it('uses server capabilities for file count and aggregate-size validation', async () => {
    const api = vi.spyOn(globalThis, 'fetch').mockImplementation((input) => {
      if (String(input).endsWith('/capabilities')) {
        return json({ upload: { maxFiles: 2, maxFileBytes: 5, maxTotalBytes: 6 } })
      }
      return baseRoute(input) ?? json({}, 404)
    })
    const actor = userEvent.setup({ applyAccept: false })
    renderRoute('/projects/project-flood/new')

    await actor.click(await screen.findByRole('radio', { name: /file material/i }))
    await actor.upload(screen.getByLabelText(/^files/i), [
      uploadedFile('one.jpg'), uploadedFile('two.jpg'), uploadedFile('three.jpg'),
    ])
    expect(await screen.findByRole('alert')).toHaveTextContent(/no more than 2 files/i)

    await actor.upload(screen.getByLabelText(/^files/i), [
      uploadedFile('one.jpg'), uploadedFile('two.jpg'),
    ])
    expect(await screen.findByRole('alert')).toHaveTextContent(/total.*6 bytes/i)
    expect(api.mock.calls.some(([url]) => String(url).endsWith('/uploads/intents'))).toBe(false)
  })

  it('rejects more than ten files before requesting upload intents', async () => {
    const api = vi.spyOn(globalThis, 'fetch').mockImplementation((input) => baseRoute(input) ?? json({}, 404))
    const actor = userEvent.setup({ applyAccept: false })
    renderRoute('/projects/project-flood/new')

    await actor.click(await screen.findByRole('radio', { name: /file material/i }))
    await actor.upload(screen.getByLabelText(/^files/i), Array.from({ length: 11 }, (_, index) => uploadedFile(`photo-${index}.jpg`)))
    expect(await screen.findByRole('alert')).toHaveTextContent(/no more than 10/i)
    expect(api.mock.calls.some(([url]) => String(url).endsWith('/uploads/intents'))).toBe(false)
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

  it('replays a frozen FILE finalization after its upload intent expires client-side', async () => {
    let now = Date.parse('2026-08-22T12:00:00Z')
    vi.spyOn(Date, 'now').mockImplementation(() => now)
    let intentRequests = 0
    let uploads = 0
    class CountingUploadRequest extends AutoUploadRequest {
      send(body?: File) {
        uploads += 1
        super.send(body)
      }
    }
    vi.stubGlobal('XMLHttpRequest', CountingUploadRequest)
    const attempts: Array<{ key: string | null; body: string }> = []
    vi.spyOn(globalThis, 'fetch').mockImplementation((input, init) => {
      const base = baseRoute(input)
      if (base) return base
      const url = String(input)
      if (url.endsWith('/uploads/intents')) {
        intentRequests += 1
        return json({ files: [{
          intentId: 'intent-expiring',
          objectKey: 'uploads/expiring',
          uploadUrl: '/api/v1/local-storage/uploads/intent-expiring',
          requiredHeaders: { 'Content-Type': 'image/jpeg' },
          expiresAt: '2026-08-22T12:01:00Z',
        }] }, 201)
      }
      if (url.endsWith('/materials/file')) {
        attempts.push({
          key: new Headers(init?.headers).get('Idempotency-Key'),
          body: String(init?.body),
        })
        return attempts.length === 1
          ? Promise.reject(new TypeError('Final response was lost'))
          : json(fileMaterial, 201)
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
    await actor.click(screen.getByRole('button', { name: /create material/i }))
    expect(await screen.findByRole('alert')).toHaveTextContent(/final response was lost/i)

    now = Date.parse('2026-08-22T12:02:00Z')
    await actor.click(screen.getByRole('button', { name: /create material/i }))
    expect(await screen.findByRole('heading', { name: 'Street evidence' })).toBeInTheDocument()
    expect(attempts).toHaveLength(2)
    expect(attempts[1]).toEqual(attempts[0])
    expect(intentRequests).toBe(1)
    expect(uploads).toBe(1)
  })

  it('safely resets uploads only after a definitive uncommitted expired-intent response', async () => {
    let intentRequests = 0
    let uploads = 0
    class CountingUploadRequest extends AutoUploadRequest {
      send(body?: File) {
        uploads += 1
        super.send(body)
      }
    }
    vi.stubGlobal('XMLHttpRequest', CountingUploadRequest)
    const attempts: Array<{ key: string | null; body: string }> = []
    vi.spyOn(globalThis, 'fetch').mockImplementation((input, init) => {
      const base = baseRoute(input)
      if (base) return base
      const url = String(input)
      if (url.endsWith('/uploads/intents')) {
        intentRequests += 1
        const suffix = intentRequests
        return json({ files: [{
          intentId: `intent-${suffix}`,
          objectKey: `uploads/${suffix}`,
          uploadUrl: `/api/v1/local-storage/uploads/intent-${suffix}`,
          requiredHeaders: { 'Content-Type': 'image/jpeg' },
          expiresAt: '2099-08-22T16:00:00Z',
        }] }, 201)
      }
      if (url.endsWith('/materials/file')) {
        attempts.push({
          key: new Headers(init?.headers).get('Idempotency-Key'),
          body: String(init?.body),
        })
        return attempts.length === 1
          ? json({
            code: 'UPLOAD_INTENT_EXPIRED',
            message: 'Upload intent has expired',
            requestId: 'expired-request',
            details: [],
          }, 410)
          : json(fileMaterial, 201)
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
    await actor.click(screen.getByRole('button', { name: /create material/i }))
    expect(await screen.findByRole('alert')).toHaveTextContent(/expired/i)

    await actor.click(screen.getByRole('button', { name: /create material/i }))
    expect(await screen.findByRole('heading', { name: 'Street evidence' })).toBeInTheDocument()
    expect(intentRequests).toBe(2)
    expect(uploads).toBe(2)
    expect(attempts[1].key).not.toBe(attempts[0].key)
    expect(JSON.parse(attempts[1].body).uploadIntentIds).toEqual(['intent-2'])
  })

  it('retries only a failed PUT with its original intent before finalizing ordered files', async () => {
    const uploadAttempts = new Map<string, number>()
    class PartialUploadRequest extends AutoUploadRequest {
      send(body?: File) {
        const filename = body?.name ?? ''
        const attempt = (uploadAttempts.get(filename) ?? 0) + 1
        uploadAttempts.set(filename, attempt)
        queueMicrotask(() => {
          if (filename === 'second.jpg' && attempt === 1) {
            this.dispatchEvent(new Event('error'))
          } else {
            this.dispatchEvent(new Event('load'))
          }
        })
      }
    }
    vi.stubGlobal('XMLHttpRequest', PartialUploadRequest)
    let intentRequests = 0
    let finalizePayload: Record<string, unknown> | undefined
    vi.spyOn(globalThis, 'fetch').mockImplementation((input, init) => {
      const base = baseRoute(input)
      if (base) return base
      const url = String(input)
      if (url.endsWith('/uploads/intents')) {
        intentRequests += 1
        return json({ files: ['first', 'second'].map((name) => ({
          intentId: `intent-${name}`,
          objectKey: `uploads/${name}`,
          uploadUrl: `/api/v1/local-storage/uploads/intent-${name}`,
          requiredHeaders: { 'Content-Type': 'image/jpeg' },
          expiresAt: '2099-08-22T16:00:00Z',
        })) }, 201)
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
    await actor.upload(screen.getByLabelText(/^files/i), [
      uploadedFile('first.jpg'),
      uploadedFile('second.jpg'),
    ])
    await actor.click(screen.getByRole('button', { name: /create material/i }))
    expect(await screen.findByRole('alert')).toHaveTextContent(/retry safely/i)

    await actor.click(screen.getByRole('button', { name: /create material/i }))
    expect(await screen.findByRole('heading', { name: 'Street evidence' })).toBeInTheDocument()
    expect(intentRequests).toBe(1)
    expect(uploadAttempts).toEqual(new Map([['first.jpg', 1], ['second.jpg', 2]]))
    expect(finalizePayload).toEqual(expect.objectContaining({
      uploadIntentIds: ['intent-first', 'intent-second'],
    }))
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

  it('uses server capabilities when retained files already consume the version limit', async () => {
    const api = vi.spyOn(globalThis, 'fetch').mockImplementation((input, init) => {
      const url = String(input)
      if (url.endsWith('/capabilities')) {
        return json({ upload: { maxFiles: 2, maxFileBytes: 52428800, maxTotalBytes: 209715200 } })
      }
      const base = baseRoute(input)
      if (base) return base
      if (url.endsWith('/materials/material-file') && (!init?.method || init.method === 'GET')) return json(fileMaterial)
      return json({}, 404)
    })
    const actor = userEvent.setup({ applyAccept: false })
    renderRoute('/materials/material-file/edit')

    await screen.findByDisplayValue('Street evidence')
    await actor.upload(screen.getByLabelText(/add files/i), uploadedFile('too-many.jpg'))
    expect(await screen.findByRole('alert')).toHaveTextContent(/no more than 2 files/i)
    expect(api.mock.calls.some(([url]) => String(url).endsWith('/uploads/intents'))).toBe(false)
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

  it('replays a FILE version finalization with the same key, payload, intents, and order', async () => {
    let now = Date.parse('2026-08-22T12:00:00Z')
    vi.spyOn(Date, 'now').mockImplementation(() => now)
    let intentRequests = 0
    let uploads = 0
    class CountingUploadRequest extends AutoUploadRequest {
      send() {
        uploads += 1
        super.send()
      }
    }
    vi.stubGlobal('XMLHttpRequest', CountingUploadRequest)
    const attempts: Array<{ key: string | null; body: string }> = []
    vi.spyOn(globalThis, 'fetch').mockImplementation((input, init) => {
      const base = baseRoute(input)
      if (base) return base
      const url = String(input)
      if (url.endsWith('/uploads/intents')) {
        intentRequests += 1
        return json({ files: [{
          intentId: 'intent-new',
          objectKey: 'uploads/new',
          uploadUrl: '/api/v1/local-storage/uploads/intent-new',
          requiredHeaders: { 'Content-Type': 'image/jpeg' },
          expiresAt: '2026-08-22T12:01:00Z',
        }] }, 201)
      }
      if (url.endsWith('/materials/material-file') && (!init?.method || init.method === 'GET')) return json(fileMaterial)
      if (url.endsWith('/materials/material-file/versions') && init?.method === 'POST') {
        attempts.push({
          key: new Headers(init.headers).get('Idempotency-Key'),
          body: String(init.body),
        })
        return attempts.length === 1
          ? Promise.reject(new TypeError('Final response was lost'))
          : json(fileMaterial, 201)
      }
      if (url.endsWith('/materials/material-file/versions')) return json([fileMaterial.version])
      return json({}, 404)
    })
    const actor = userEvent.setup()
    renderRoute('/materials/material-file/edit')

    await screen.findByDisplayValue('Street evidence')
    await actor.click(screen.getByRole('button', { name: /move street.jpg down/i }))
    await actor.upload(screen.getByLabelText(/add files/i), uploadedFile('new.jpg'))
    await actor.click(screen.getByRole('button', { name: /move new.jpg up/i }))
    await actor.click(screen.getByRole('button', { name: /save new version/i }))
    expect(await screen.findByRole('alert')).toHaveTextContent(/final response was lost/i)

    now = Date.parse('2026-08-22T12:02:00Z')
    await actor.click(screen.getByRole('button', { name: /save new version/i }))
    await waitFor(() => expect(attempts).toHaveLength(2))
    expect(attempts[1]).toEqual(attempts[0])
    expect(JSON.parse(attempts[0].body).files).toEqual([
      { kind: 'EXISTING', fileId: 'file-note', uploadIntentId: null },
      { kind: 'UPLOAD', fileId: null, uploadIntentId: 'intent-new' },
      { kind: 'EXISTING', fileId: 'file-photo', uploadIntentId: null },
    ])
    expect(intentRequests).toBe(1)
    expect(uploads).toBe(1)
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
    const eventTimeInput = screen.getByLabelText(/event time/i) as HTMLInputElement
    expect(eventTimeInput).toHaveAttribute('type', 'text')
    expect(eventTimeInput).toHaveAttribute('placeholder', 'MM/DD/YYYY HH:MM')
    expect(eventTimeInput.value).toMatch(/^\d{2}\/\d{2}\/\d{4} \d{2}:\d{2}$/)
    await actor.type(screen.getByLabelText(/text body/i), 'A revised observation.')
    await actor.click(screen.getByRole('button', { name: /save new version/i }))
    expect(await screen.findByRole('alert')).toHaveTextContent(/newer version.*reload/i)
  })
})
