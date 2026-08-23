import { beforeEach, describe, expect, it, vi } from 'vitest'
import { apiRequest, putFile } from './client'
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

  it('uploads directly to the signed target with required headers and reports progress', async () => {
    vi.stubGlobal('XMLHttpRequest', FakeRequest)
    const progress: number[] = []
    const file = new File(['image'], 'street.jpg', { type: 'image/jpeg' })
    const pending = putFile(intent, file, (value) => progress.push(value))
    const request = FakeRequest.latest

    request.upload.dispatchEvent(new ProgressEvent('progress', { lengthComputable: true, loaded: 3, total: 5 }))
    request.dispatchEvent(new Event('load'))
    await pending

    expect({ method: request.method, url: request.url, body: request.body }).toEqual({
      method: 'PUT',
      url: intent.uploadUrl,
      body: file,
    })
    expect(Object.fromEntries(request.headers)).toEqual(intent.requiredHeaders)
    expect(request.withCredentials).toBe(true)
    expect(progress).toEqual([60, 100])
  })

  it('returns a retry-safe error when direct upload is interrupted', async () => {
    vi.stubGlobal('XMLHttpRequest', FakeRequest)
    const pending = putFile(intent, new File(['x'], 'street.jpg'), () => undefined)
    FakeRequest.latest.dispatchEvent(new Event('error'))
    await expect(pending).rejects.toEqual(expect.objectContaining({
      code: 'UPLOAD_FAILED',
      message: expect.stringMatching(/retry safely/i),
    }))
  })
})
