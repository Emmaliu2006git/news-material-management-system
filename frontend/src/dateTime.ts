export const ENGLISH_DATE_PLACEHOLDER = 'MM/DD/YYYY'
export const ENGLISH_DATE_TIME_PLACEHOLDER = 'MM/DD/YYYY HH:MM'

type DateParts = {
  year: number
  month: number
  day: number
}

function invalid(label: string, format: string): never {
  throw new Error(`Enter ${label} as ${format}.`)
}

function dateParts(value: FormDataEntryValue | null, label: string): DateParts | null {
  const raw = String(value ?? '').trim()
  if (!raw) return null
  const match = /^(\d{2})\/(\d{2})\/(\d{4})$/.exec(raw)
  if (!match) invalid(label, ENGLISH_DATE_PLACEHOLDER)

  const month = Number(match[1])
  const day = Number(match[2])
  const year = Number(match[3])
  const candidate = new Date(Date.UTC(year, month - 1, day))
  if (
    candidate.getUTCFullYear() !== year
    || candidate.getUTCMonth() !== month - 1
    || candidate.getUTCDate() !== day
  ) {
    invalid(label, ENGLISH_DATE_PLACEHOLDER)
  }
  return { year, month, day }
}

export function englishDateBoundary(
  value: FormDataEntryValue | null,
  label: string,
  endOfDay = false,
) {
  const parts = dateParts(value, label)
  if (!parts) return undefined
  return new Date(Date.UTC(
    parts.year,
    parts.month - 1,
    parts.day,
    endOfDay ? 23 : 0,
    endOfDay ? 59 : 0,
    endOfDay ? 59 : 0,
    endOfDay ? 999 : 0,
  )).toISOString()
}

export function parseEnglishDateTime(
  value: FormDataEntryValue | null,
  label = 'Event time',
) {
  const raw = String(value ?? '').trim()
  if (!raw) return null
  const match = /^(\d{2})\/(\d{2})\/(\d{4}) (\d{2}):(\d{2})$/.exec(raw)
  if (!match) invalid(label, ENGLISH_DATE_TIME_PLACEHOLDER)

  const month = Number(match[1])
  const day = Number(match[2])
  const year = Number(match[3])
  const hour = Number(match[4])
  const minute = Number(match[5])
  const candidate = new Date(year, month - 1, day, hour, minute)
  if (
    candidate.getFullYear() !== year
    || candidate.getMonth() !== month - 1
    || candidate.getDate() !== day
    || candidate.getHours() !== hour
    || candidate.getMinutes() !== minute
  ) {
    invalid(label, ENGLISH_DATE_TIME_PLACEHOLDER)
  }
  return candidate.toISOString()
}

function pad(value: number) {
  return String(value).padStart(2, '0')
}

export function formatEnglishDateTimeInput(value: string | null) {
  if (!value) return ''
  const date = new Date(value)
  if (Number.isNaN(date.getTime())) return ''
  return `${pad(date.getMonth() + 1)}/${pad(date.getDate())}/${date.getFullYear()} ${pad(date.getHours())}:${pad(date.getMinutes())}`
}

export function formatEnglishDate(value: string) {
  return new Intl.DateTimeFormat('en-US', { dateStyle: 'medium' }).format(new Date(value))
}

export function formatEnglishDateTime(value: string) {
  return new Intl.DateTimeFormat('en-US', {
    dateStyle: 'medium',
    timeStyle: 'short',
  }).format(new Date(value))
}
