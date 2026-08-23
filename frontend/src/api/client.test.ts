import { beforeEach, describe, expect, it, vi } from 'vitest'
import { apiRequest, putFile, resetCsrfForTests, sha256 } from './client'
import type { UploadIntent } from './types'

describe('same-origin API client', () => {
  beforeEach(() => {
    vi.resetModules()
  })

  it('bootstraps CSRF and sends credentials plus the returned header for mutations', async () => {
    const fetcher = vi.spyOn(globalThis, 'fetch')
      .mockResolvedValueOnce(new Response(JSON.stringify({
        token: 'real-token',
        headerName: 'X-XSRF-TOKEN',
      }), { status: 200, headers: { 'Content-Type': 'application/json' } }))
      .mockResolvedValueOnce(new Response(JSON.stringify({ id: 'project-1' }), {
        status: 201,
        headers: { 'Content-Type': 'application/json' },
      }))

    await apiRequest('/api/v1/projects', {
      method: 'POST',
      body: JSON.stringify({ title: 'Flood' }),
    }, true)

    expect(fetcher).toHaveBeenNthCalledWith(1, '/api/v1/auth/csrf', { credentials: 'include' })
    const mutation = fetcher.mock.calls[1]
    expect(mutation[0]).toBe('/api/v1/projects')
    expect(mutation[1]).toEqual(expect.objectContaining({ method: 'POST', credentials: 'include' }))
    expect(new Headers(mutation[1]?.headers).get('X-XSRF-TOKEN')).toBe('real-token')
  })

  it('turns stable server error fields into an ApiError without exposing response content', async () => {
    vi.spyOn(globalThis, 'fetch').mockResolvedValue(new Response(JSON.stringify({
      code: 'VERSION_CONFLICT',
      message: 'The material has a newer version',
      requestId: 'request-7',
      details: [],
    }), { status: 409, headers: { 'Content-Type': 'application/json' } }))

    await expect(apiRequest('/api/v1/materials/one')).rejects.toEqual(expect.objectContaining({
      name: 'ApiError',
      status: 409,
      code: 'VERSION_CONFLICT',
      message: 'The material has a newer version',
      requestId: 'request-7',
    }))
  })
})

describe('browser checksum', () => {
  it('calculates SHA-256 when Web Crypto subtle is unavailable on a plain LAN origin', async () => {
    const originalCrypto = globalThis.crypto
    vi.stubGlobal('crypto', { randomUUID: originalCrypto.randomUUID?.bind(originalCrypto) })
    const file = new File(['image'], 'street.jpg', { type: 'image/jpeg' })
    Object.defineProperty(file, 'arrayBuffer', {
      value: async () => new TextEncoder().encode('image').buffer,
    })

    try {
      await expect(sha256(file)).resolves.toBe('6105d6cc76af400325e94d588ce511be5bfdbb73b437dc51eca43917d7a43e3d')
    } finally {
      vi.stubGlobal('crypto', originalCrypto)
    }
  })
})

describe('direct file upload', () => {
  class FakeUploadTarget extends EventTarget {}
  class FakeRequest extends EventTarget {
    static latest: FakeRequest
    upload = new FakeUploadTarget()
    status = 204
    method = ''
    url = ''
    headers = new Map<string, string>()
    withCredentials = false
    body?: File

    constructor() {
      super()
      FakeRequest.latest = this
    }
    open(method: string, url: string) {
      this.method = method
      this.url = url
    }
    setRequestHeader(name: string, value: string) {
      this.headers.set(name, value)
    }
    send(body: File) {
      this.body = body
    }
  }

  const intent: UploadIntent = {
    intentId: 'intent-1',
    objectKey: 'uploads/one',
    uploadUrl: '/api/v1/local-storage/uploads/intent-1',
    requiredHeaders: { 'Content-Type': 'image/jpeg', 'x-checksum': 'safe' },
    expiresAt: '2026-08-22T16:00:00Z',
  }

  beforeEach(() => {
    resetCsrfForTests()
    FakeRequest.latest = undefined as unknown as FakeRequest
  })

  it('attaches the current CSRF token and credentials to a same-origin direct upload', async () => {
    vi.stubGlobal('XMLHttpRequest', FakeRequest)
    const fetcher = vi.spyOn(globalThis, 'fetch').mockResolvedValue(new Response(JSON.stringify({
      token: 'upload-token',
      headerName: 'X-XSRF-TOKEN',
    }), { status: 200, headers: { 'Content-Type': 'application/json' } }))
    const progress: number[] = []
    const file = new File(['image'], 'street.jpg', { type: 'image/jpeg' })
    const pending = putFile(intent, file, (value) => progress.push(value))
    await vi.waitFor(() => expect(FakeRequest.latest).toBeDefined())
    const request = FakeRequest.latest

    request.upload.dispatchEvent(new ProgressEvent('progress', { lengthComputable: true, loaded: 3, total: 5 }))
    request.dispatchEvent(new Event('load'))
    await pending

    expect(fetcher).toHaveBeenCalledWith('/api/v1/auth/csrf', { credentials: 'include' })
    expect({ method: request.method, url: request.url, body: request.body }).toEqual({
      method: 'PUT',
      url: intent.uploadUrl,
      body: file,
    })
    expect(Object.fromEntries(request.headers)).toEqual({
      ...intent.requiredHeaders,
      'X-XSRF-TOKEN': 'upload-token',
    })
    expect(request.withCredentials).toBe(true)
    expect(progress).toEqual([60, 100])
  })

  it('sends only signed required headers to a cross-origin object store', async () => {
    vi.stubGlobal('XMLHttpRequest', FakeRequest)
    const fetcher = vi.spyOn(globalThis, 'fetch')
    const crossOrigin = { ...intent, uploadUrl: 'https://bucket.example/uploads/one' }
    const pending = putFile(crossOrigin, new File(['x'], 'street.jpg'), () => undefined)
    await vi.waitFor(() => expect(FakeRequest.latest).toBeDefined())
    FakeRequest.latest.dispatchEvent(new Event('load'))
    await pending
    expect(FakeRequest.latest.withCredentials).toBe(false)
    expect(Object.fromEntries(FakeRequest.latest.headers)).toEqual(intent.requiredHeaders)
    expect(fetcher).not.toHaveBeenCalled()
  })

  it('returns a retry-safe error when direct upload is interrupted', async () => {
    vi.stubGlobal('XMLHttpRequest', FakeRequest)
    vi.spyOn(globalThis, 'fetch').mockResolvedValue(new Response(JSON.stringify({
      token: 'upload-token',
      headerName: 'X-XSRF-TOKEN',
    }), { status: 200, headers: { 'Content-Type': 'application/json' } }))
    const pending = putFile(intent, new File(['x'], 'street.jpg'), () => undefined)
    await vi.waitFor(() => expect(FakeRequest.latest).toBeDefined())
    FakeRequest.latest.dispatchEvent(new Event('error'))
    await expect(pending).rejects.toEqual(expect.objectContaining({
      code: 'UPLOAD_FAILED',
      message: expect.stringMatching(/retry safely/i),
    }))
  })
})
