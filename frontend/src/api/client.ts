import { sha256 as nobleSha256 } from '@noble/hashes/sha256'
import { bytesToHex } from '@noble/hashes/utils'
import type {
  ApiErrorBody,
  Capabilities,
  FileMaterialInput,
  Material,
  Project,
  SearchFilters,
  SearchResult,
  TextMaterialInput,
  UploadFileSpec,
  UploadIntent,
  User,
  VersionSummary,
  VersionInput,
} from './types'

export class ApiError extends Error {
  constructor(
    public readonly status: number,
    public readonly code: string,
    message: string,
    public readonly requestId?: string,
    public readonly details: string[] = [],
  ) {
    super(message)
    this.name = 'ApiError'
  }
}

let csrf: { token: string; headerName: string } | null = null

async function parseError(response: Response): Promise<ApiError> {
  let body: Partial<ApiErrorBody> = {}
  try {
    body = await response.json()
  } catch {
    // A stable fallback avoids leaking server response content.
  }
  return new ApiError(
    response.status,
    body.code ?? 'REQUEST_FAILED',
    body.message ?? 'The request could not be completed.',
    body.requestId,
    body.details ?? [],
  )
}

async function ensureCsrf() {
  const response = await fetch('/api/v1/auth/csrf', { credentials: 'include' })
  if (!response.ok) throw await parseError(response)
  csrf = await response.json()
  return csrf!
}

export async function apiRequest<T>(
  path: string,
  init: RequestInit = {},
  mutation = false,
): Promise<T> {
  const headers = new Headers(init.headers)
  if (init.body && !(init.body instanceof FormData) && !headers.has('Content-Type')) {
    headers.set('Content-Type', 'application/json')
  }
  if (mutation) {
    const token = await ensureCsrf()
    headers.set(token.headerName, token.token)
  }
  const response = await fetch(path, { ...init, headers, credentials: 'include' })
  if (!response.ok) throw await parseError(response)
  if (response.status === 204) return undefined as T
  return response.json()
}

export function resetCsrfForTests() {
  csrf = null
}

export const api = {
  me: () => apiRequest<User>('/api/v1/auth/me'),
  login: (username: string, password: string) =>
    apiRequest<User>('/api/v1/auth/login', {
      method: 'POST',
      body: JSON.stringify({ username, password }),
    }),
  register: (username: string, password: string) =>
    apiRequest<User>('/api/v1/auth/register', {
      method: 'POST',
      body: JSON.stringify({ username, password }),
    }, true),
  changePassword: (currentPassword: string, newPassword: string) =>
    apiRequest<void>('/api/v1/auth/password', {
      method: 'POST',
      body: JSON.stringify({ currentPassword, newPassword }),
    }, true),
  logout: () => apiRequest<void>('/api/v1/auth/logout', { method: 'POST' }, true),
  capabilities: () => apiRequest<Capabilities>('/api/v1/capabilities'),
  listProjects: () => apiRequest<Project[]>('/api/v1/projects'),
  createProject: (title: string) =>
    apiRequest<Project>('/api/v1/projects', {
      method: 'POST',
      body: JSON.stringify({ title }),
    }, true),
  search: (projectId: string, filters: SearchFilters = {}) => {
    const query = new URLSearchParams()
    Object.entries(filters).forEach(([key, value]) => {
      if (value) query.set(key, value)
    })
    const suffix = query.size ? `?${query.toString()}` : ''
    return apiRequest<SearchResult[]>(`/api/v1/projects/${projectId}/materials${suffix}`)
  },
  material: (id: string) => apiRequest<Material>(`/api/v1/materials/${id}`),
  historicalMaterial: (id: string, number: number) =>
    apiRequest<Material>(`/api/v1/materials/${id}/versions/${number}`),
  versions: (id: string) => apiRequest<VersionSummary[]>(`/api/v1/materials/${id}/versions`),
  createText: (input: TextMaterialInput, key: string) =>
    apiRequest<Material>('/api/v1/materials/text', {
      method: 'POST',
      headers: { 'Idempotency-Key': key },
      body: JSON.stringify(input),
    }, true),
  createFile: (input: FileMaterialInput, key: string) =>
    apiRequest<Material>('/api/v1/materials/file', {
      method: 'POST',
      headers: { 'Idempotency-Key': key },
      body: JSON.stringify(input),
    }, true),
  createVersion: (id: string, input: VersionInput, key: string) =>
    apiRequest<Material>(`/api/v1/materials/${id}/versions`, {
      method: 'POST',
      headers: { 'Idempotency-Key': key },
      body: JSON.stringify(input),
    }, true),
  createUploadIntents: (files: UploadFileSpec[]) =>
    apiRequest<{ files: UploadIntent[] }>('/api/v1/uploads/intents', {
      method: 'POST',
      body: JSON.stringify({ files }),
    }, true),
  retryAnalysis: (fileId: string) =>
    apiRequest<{ status: string }>(`/api/v1/files/${fileId}/analysis/retry`, { method: 'POST' }, true),
  downloadUrl: (fileId: string) =>
    apiRequest<{ downloadUrl: string; expiresAt: string }>(`/api/v1/files/${fileId}/download-url`, { method: 'POST' }, true),
}

export async function sha256(file: File) {
  const bytes = new Uint8Array(await file.arrayBuffer())
  if (globalThis.crypto?.subtle) {
    try {
      const digest = await globalThis.crypto.subtle.digest('SHA-256', bytes)
      return bytesToHex(new Uint8Array(digest))
    } catch {
      // Web Crypto may be exposed but unusable on an insecure mobile/LAN origin.
    }
  }
  return bytesToHex(nobleSha256(bytes))
}

export async function putFile(
  target: UploadIntent,
  file: File,
  onProgress: (percent: number) => void,
): Promise<void> {
  const targetUrl = new URL(target.uploadUrl, window.location.href)
  const sameOrigin = targetUrl.origin === window.location.origin
  const csrfToken = sameOrigin ? await ensureCsrf() : null

  return new Promise((resolve, reject) => {
    const request = new XMLHttpRequest()
    request.open('PUT', target.uploadUrl)
    request.withCredentials = sameOrigin
    Object.entries(target.requiredHeaders).forEach(([name, value]) => request.setRequestHeader(name, value))
    if (csrfToken) request.setRequestHeader(csrfToken.headerName, csrfToken.token)
    request.upload.addEventListener('progress', (event) => {
      if (event.lengthComputable) onProgress(Math.round((event.loaded / event.total) * 100))
    })
    request.addEventListener('load', () => {
      if (request.status >= 200 && request.status < 300) {
        onProgress(100)
        resolve()
      } else {
        reject(new ApiError(request.status, 'UPLOAD_FAILED', 'The file upload failed. You can retry safely.'))
      }
    })
    request.addEventListener('error', () =>
      reject(new ApiError(0, 'UPLOAD_FAILED', 'The file upload was interrupted. You can retry safely.')),
    )
    request.send(file)
  })
}
