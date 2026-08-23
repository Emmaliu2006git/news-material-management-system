import { vi } from 'vitest'
import type { Material } from '../api/types'

export const user = { id: 'user-emma', username: 'emma' }

export const project = {
  id: 'project-flood',
  title: 'Downtown flood',
  creatorId: 'user-emma',
  creatorUsername: 'emma',
  createdAt: '2026-08-22T12:00:00Z',
  updatedAt: '2026-08-22T12:00:00Z',
}

export const textMaterial: Material = {
  id: 'material-text',
  projectId: project.id,
  projectTitle: project.title,
  ownerId: user.id,
  ownerUsername: user.username,
  type: 'TEXT',
  materialCreatedTime: '2026-08-22T12:05:00Z',
  currentVersionId: 'version-text-2',
  canEdit: true,
  historical: false,
  version: {
    id: 'version-text-2',
    versionNumber: 2,
    createdBy: user.id,
    creatorUsername: user.username,
    versionCreatedTime: '2026-08-22T13:00:00Z',
    snapshot: {
      title: 'River level update',
      eventTime: '2026-08-22T11:30:00Z',
      location: 'South bridge',
      manualTags: ['urgent', 'river'],
      content: { kind: 'TEXT', textBody: 'Water reached the second marker.' },
    },
  },
}

export const historicalTextMaterial: Material = {
  ...textMaterial,
  canEdit: false,
  historical: true,
  version: {
    ...textMaterial.version,
    id: 'version-text-1',
    versionNumber: 1,
    versionCreatedTime: '2026-08-22T12:05:00Z',
    snapshot: {
      ...textMaterial.version.snapshot,
      title: 'Initial river level',
      content: { kind: 'TEXT', textBody: 'Water reached the first marker.' },
    },
  },
}

export const fileMaterial: Material = {
  id: 'material-file',
  projectId: project.id,
  projectTitle: project.title,
  ownerId: user.id,
  ownerUsername: user.username,
  type: 'FILE',
  materialCreatedTime: '2026-08-22T14:05:00Z',
  currentVersionId: 'version-file-1',
  canEdit: true,
  historical: false,
  version: {
    id: 'version-file-1',
    versionNumber: 1,
    createdBy: user.id,
    creatorUsername: user.username,
    versionCreatedTime: '2026-08-22T14:05:00Z',
    snapshot: {
      title: 'Street evidence',
      eventTime: null,
      location: null,
      manualTags: ['field'],
      content: {
        kind: 'FILE',
        textContent: 'Photos from the east entrance.',
        files: [
          {
            id: 'file-photo',
            uploaderId: user.id,
            uploaderUsername: user.username,
            originalFilename: 'street.jpg',
            mimeType: 'image/jpeg',
            sizeBytes: 2048,
            checksumSha256: 'a'.repeat(64),
            fileCreatedTime: '2026-08-22T14:03:00Z',
            imageAnalysis: {
              status: 'FAILED',
              aiDescription: 'A flooded street beside brick buildings.',
              aiTags: ['flood', 'street'],
              generatedTime: '2026-08-22T14:04:00Z',
            },
          },
          {
            id: 'file-note',
            uploaderId: user.id,
            uploaderUsername: user.username,
            originalFilename: 'notes.txt',
            mimeType: 'text/plain',
            sizeBytes: 512,
            checksumSha256: 'b'.repeat(64),
            fileCreatedTime: '2026-08-22T14:03:30Z',
            imageAnalysis: null,
          },
        ],
      },
    },
  },
}

export function json(data: unknown, status = 200) {
  return Promise.resolve(
    new Response(JSON.stringify(data), {
      status,
      headers: { 'Content-Type': 'application/json' },
    }),
  )
}

export function empty(status = 204) {
  return Promise.resolve(new Response(null, { status }))
}

function versionSummary(material: Material) {
  return {
    id: material.version.id,
    versionNumber: material.version.versionNumber,
    createdBy: material.version.createdBy,
    creatorUsername: material.version.creatorUsername,
    versionCreatedTime: material.version.versionCreatedTime,
    title: material.version.snapshot.title,
  }
}

export function installApi(options?: {
  me?: typeof user | null
  material?: typeof textMaterial | typeof fileMaterial
  historical?: typeof historicalTextMaterial
  search?: unknown[]
}) {
  return vi.spyOn(globalThis, 'fetch').mockImplementation((input, init) => {
    const url = String(input)
    if (url.endsWith('/auth/csrf')) return json({ token: 'csrf-token', headerName: 'X-XSRF-TOKEN' })
    if (url.endsWith('/capabilities')) return json({ upload: { maxFiles: 10, maxFileBytes: 52428800, maxTotalBytes: 209715200 } })
    if (url.endsWith('/auth/me')) {
      return options?.me === null
        ? json({ code: 'AUTHENTICATION_REQUIRED', message: 'Authentication required', requestId: 'req', details: [] }, 401)
        : json(options?.me ?? user)
    }
    if (url.endsWith('/projects') && (!init?.method || init.method === 'GET')) return json([project])
    if (url.includes('/projects/project-flood/materials')) return json(options?.search ?? [])
    if (url.endsWith('/materials/material-text/versions')) return json([versionSummary(historicalTextMaterial), versionSummary(textMaterial)])
    if (url.endsWith('/materials/material-text/versions/1')) return json(options?.historical ?? historicalTextMaterial)
    if (url.endsWith('/materials/material-text')) return json(options?.material ?? textMaterial)
    if (url.endsWith('/materials/material-file/versions')) return json([versionSummary(fileMaterial)])
    if (url.endsWith('/materials/material-file')) return json(options?.material ?? fileMaterial)
    return json({ code: 'NOT_FOUND', message: 'No test route', requestId: 'req', details: [] }, 404)
  })
}

