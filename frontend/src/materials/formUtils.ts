import { api, putFile, sha256 } from '../api/client'
import type { UploadIntent } from '../api/types'

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
  const raw = optional(value)
  return raw ? new Date(raw).toISOString() : null
}

export function idempotencyKey() {
  return typeof crypto.randomUUID === 'function'
    ? crypto.randomUUID()
    : `request-${Date.now()}-${Math.random().toString(16).slice(2)}`
}

export interface UploadProgress {
  name: string
  percent: number
  status: 'hashing' | 'uploading' | 'uploaded' | 'failed'
}

export async function uploadFiles(
  files: File[],
  update: (index: number, progress: UploadProgress) => void,
): Promise<UploadIntent[]> {
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
  for (let index = 0; index < files.length; index += 1) {
    const file = files[index]
    update(index, { name: file.name, percent: 0, status: 'uploading' })
    try {
      await putFile(intents[index], file, (percent) =>
        update(index, { name: file.name, percent, status: percent === 100 ? 'uploaded' : 'uploading' }),
      )
    } catch (error) {
      update(index, { name: file.name, percent: 0, status: 'failed' })
      throw error
    }
  }
  return intents
}
