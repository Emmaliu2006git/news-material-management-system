import { api, ApiError, putFile, sha256 } from '../api/client'
import type { UploadIntent, UploadPolicy } from '../api/types'
import { parseEnglishDateTime } from '../dateTime'

export function tags(value: FormDataEntryValue | null) {
  return String(value ?? '')
    .split(',')
    .map((tag) => tag.trim())
    .filter((tag, index, all) => tag && all.indexOf(tag) === index)
}

export function optional(value: FormDataEntryValue | null) {
  const cleaned = String(value ?? '').trim()
  return cleaned || null
}

export function eventTime(value: FormDataEntryValue | null) {
  return parseEnglishDateTime(value)
}

export function idempotencyKey() {
  return typeof crypto.randomUUID === 'function'
    ? crypto.randomUUID()
    : `request-${Date.now()}-${Math.random().toString(16).slice(2)}`
}

export function formatBytes(bytes: number) {
  if (bytes < 1024) return `${bytes} ${bytes === 1 ? 'byte' : 'bytes'}`
  if (bytes < 1024 * 1024) return `${Math.round(bytes / 1024)} KiB`
  return `${Math.round(bytes / (1024 * 1024))} MiB`
}

export function validateFilePolicy(
  files: Array<{ name: string; size: number }>,
  policy: UploadPolicy,
): string | null {
  if (files.length > policy.maxFiles) return `Choose no more than ${policy.maxFiles} files.`
  const oversized = files.find((file) => file.size > policy.maxFileBytes)
  if (oversized) {
    return `${oversized.name} is larger than the ${formatBytes(policy.maxFileBytes)} per-file limit.`
  }
  const total = files.reduce((sum, file) => sum + file.size, 0)
  if (total > policy.maxTotalBytes) {
    return `The selected files total ${formatBytes(total)}; the limit is ${formatBytes(policy.maxTotalBytes)}.`
  }
  return null
}

export interface UploadProgress {
  name: string
  percent: number
  status: 'hashing' | 'uploading' | 'uploaded' | 'failed'
}

export interface UploadSession {
  intents: UploadIntent[]
  uploaded: Set<number>
}

export function uploadSessionIsValid(session: UploadSession) {
  return session.intents.every((intent) => new Date(intent.expiresAt).getTime() > Date.now())
}

const DEFINITIVE_UPLOAD_INTENT_FAILURES = new Set([
  'UPLOAD_INTENT_EXPIRED',
  'UPLOAD_INTENT_NOT_FOUND',
  'UPLOAD_INTENT_CONSUMED',
  'UPLOAD_NOT_VERIFIED',
])

export function isDefinitiveUploadIntentFailure(error: unknown) {
  return error instanceof ApiError && DEFINITIVE_UPLOAD_INTENT_FAILURES.has(error.code)
}

export async function createUploadSession(
  files: File[],
  update: (index: number, progress: UploadProgress) => void,
): Promise<UploadSession> {
  const specs = []
  for (let index = 0; index < files.length; index += 1) {
    const file = files[index]
    update(index, { name: file.name, percent: 0, status: 'hashing' })
    specs.push({
      originalFilename: file.name,
      mimeType: file.type || 'application/octet-stream',
      sizeBytes: file.size,
      checksumSha256: await sha256(file),
    })
  }
  const { files: intents } = await api.createUploadIntents(specs)
  if (intents.length !== files.length) {
    throw new Error('The upload service returned an incomplete file set.')
  }
  return { intents, uploaded: new Set() }
}

export async function uploadPendingFiles(
  files: File[],
  session: UploadSession,
  update: (index: number, progress: UploadProgress) => void,
) {
  for (let index = 0; index < files.length; index += 1) {
    const file = files[index]
    if (session.uploaded.has(index)) continue
    update(index, { name: file.name, percent: 0, status: 'uploading' })
    try {
      await putFile(session.intents[index], file, (percent) =>
        update(index, { name: file.name, percent, status: percent === 100 ? 'uploaded' : 'uploading' }),
      )
      session.uploaded.add(index)
    } catch (error) {
      update(index, { name: file.name, percent: 0, status: 'failed' })
      throw error
    }
  }
}

export async function uploadFiles(
  files: File[],
  update: (index: number, progress: UploadProgress) => void,
): Promise<UploadIntent[]> {
  const session = await createUploadSession(files, update)
  await uploadPendingFiles(files, session, update)
  return session.intents
}
