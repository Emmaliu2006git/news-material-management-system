export type MaterialType = 'TEXT' | 'FILE'
export type AnalysisStatus = 'PENDING' | 'PROCESSING' | 'COMPLETED' | 'FAILED'

export interface User {
  id: string
  username: string
}

export interface Project {
  id: string
  title: string
  creatorId: string
  creatorUsername: string
  createdAt: string
  updatedAt: string
}

export interface ImageAnalysis {
  status: AnalysisStatus
  aiDescription: string | null
  aiTags: string[]
  generatedTime: string | null
}

export interface FileAsset {
  id: string
  uploaderId: string
  uploaderUsername: string
  originalFilename: string
  mimeType: string
  sizeBytes: number
  checksumSha256: string
  fileCreatedTime: string
  imageAnalysis: ImageAnalysis | null
}

export interface TextVersionContent {
  kind: 'TEXT'
  textBody: string
}

export interface FileVersionContent {
  kind: 'FILE'
  textContent: string | null
  files: FileAsset[]
}

export type VersionContent = TextVersionContent | FileVersionContent

export interface VersionSnapshot {
  title: string
  eventTime: string | null
  location: string | null
  manualTags: string[]
  content: VersionContent
}

export interface Version {
  id: string
  versionNumber: number
  createdBy: string
  creatorUsername: string
  versionCreatedTime: string
  snapshot: VersionSnapshot
}

export interface Material {
  id: string
  projectId: string
  projectTitle: string
  ownerId: string
  ownerUsername: string
  type: MaterialType
  materialCreatedTime: string
  currentVersionId: string
  canEdit: boolean
  historical: boolean
  version: Version
}

export interface SearchResult {
  id: string
  projectId: string
  ownerId: string
  ownerUsername: string
  type: MaterialType
  materialCreatedTime: string
  currentVersionId: string
  currentVersionNumber: number
  title: string
  eventTime: string | null
  location: string | null
  manualTags: string[]
  matchedFileId: string | null
}

export interface CommonMaterialInput {
  projectId: string
  title: string
  eventTime: string | null
  location: string | null
  manualTags: string[]
}

export interface TextMaterialInput extends CommonMaterialInput {
  textBody: string
}

export interface FileMaterialInput extends CommonMaterialInput {
  textContent: string | null
  uploadIntentIds: string[]
}

export interface UploadFileSpec {
  originalFilename: string
  mimeType: string
  sizeBytes: number
  checksumSha256: string
}

export interface UploadIntent {
  intentId: string
  objectKey: string
  uploadUrl: string
  requiredHeaders: Record<string, string>
  expiresAt: string
}

export type FileSelection =
  | { kind: 'EXISTING'; fileId: string; uploadIntentId: null }
  | { kind: 'UPLOAD'; fileId: null; uploadIntentId: string }

export interface VersionInput {
  expectedCurrentVersion: number
  title: string
  eventTime: string | null
  location: string | null
  manualTags: string[]
  textBody?: string
  textContent?: string | null
  files?: FileSelection[]
}

export interface ApiErrorBody {
  code: string
  message: string
  requestId: string
  details: string[]
}

export interface SearchFilters {
  q?: string
  materialType?: MaterialType
  ownerId?: string
  createdFrom?: string
  createdTo?: string
  eventFrom?: string
  eventTo?: string
  location?: string
  tag?: string
}
