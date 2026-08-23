import { expect, test, type Page } from '@playwright/test'

const password = 'demo-password'
const onePixelPng = Buffer.from(
  'iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAQAAAC1HAwCAAAAC0lEQVR42mP8/x8AAusB9WlWlWQAAAAASUVORK5CYII=',
  'base64',
)

function usesMobileNavigation(page: Page) {
  return (page.viewportSize()?.width ?? Number.POSITIVE_INFINITY) <= 559
}

async function login(page: Page, username: string) {
  await page.goto('/login')
  await page.getByLabel('Username').fill(username)
  await page.getByLabel('Password').fill(password)
  await page.getByRole('button', { name: 'Sign in' }).click()
  await expect(page).toHaveURL(/\/projects$/)
  if (usesMobileNavigation(page)) {
    await page.getByRole('button', { name: 'Open navigation' }).click()
  }
  await expect(page.getByTitle('Signed-in user')).toHaveText(username)
  if (usesMobileNavigation(page)) {
    await page.getByRole('button', { name: 'Close navigation' }).click()
  }
}

test('packaged newsroom happy path preserves history, files, AI isolation, and ownership', async ({ page }) => {
  const suffix = `${Date.now().toString(36)}-${Math.random().toString(36).slice(2, 8)}`
  const projectTitle = `E2E flood ${suffix}`
  const textV1Title = `Field note ${suffix}`
  const textV2Title = `Verified field note ${suffix}`
  const oldMarker = `old-marker-${suffix}`
  const currentMarker = `current-marker-${suffix}`
  const fileTitle = `Photo evidence ${suffix}`
  const successFilename = `street-${suffix}.png`
  const failedFilename = `fail-ai-${suffix}.png`

  await login(page, 'emma')

  await page.getByLabel('New project title').fill(projectTitle)
  await page.getByRole('button', { name: 'Create project' }).click()
  await page.getByRole('link', { name: projectTitle }).click()
  await expect(page.getByRole('heading', { name: projectTitle })).toBeVisible()

  await page.getByRole('link', { name: 'Add material' }).click()
  await page.getByLabel(/^Title/).fill(textV1Title)
  await page.getByLabel(/^Manual tags/).fill(`flood, ${oldMarker}`)
  await page.getByLabel(/^Text body/).fill(`Initial eyewitness report ${oldMarker}`)
  await page.getByRole('button', { name: 'Create material' }).click()
  await expect(page.getByRole('heading', { name: textV1Title })).toBeVisible()
  await expect(page.locator('.version-chip')).toHaveText('Version 1')
  const textMaterialUrl = page.url()
  const textMaterialId = new URL(textMaterialUrl).pathname.split('/').at(-1)!

  await page.getByRole('link', { name: 'Create new version' }).click()
  await page.getByLabel(/^Title/).fill(textV2Title)
  await page.getByLabel(/^Manual tags/).fill(`flood, ${currentMarker}`)
  await page.getByLabel(/^Text body/).fill(`Confirmed eyewitness report ${currentMarker}`)
  await page.getByRole('button', { name: 'Save new version' }).click()
  await expect(page.getByRole('heading', { name: textV2Title })).toBeVisible()
  await expect(page.locator('.version-chip')).toHaveText('Version 2')

  await page.getByRole('link', { name: /Version 1/ }).click()
  await expect(page.getByRole('status')).toContainText('Historical version 1')
  await expect(page.getByRole('heading', { name: textV1Title })).toBeVisible()
  await expect(page.getByText(`Initial eyewitness report ${oldMarker}`)).toBeVisible()
  await page.getByRole('link', { name: 'View current version' }).click()
  await expect(page.getByRole('heading', { name: textV2Title })).toBeVisible()

  await page.getByRole('link', { name: 'Project materials' }).click()
  const query = page.getByLabel('Search current materials')
  await query.fill(oldMarker)
  await page.getByRole('button', { name: 'Search' }).click()
  await expect(page.getByText('0 found')).toBeVisible()
  await query.fill(currentMarker)
  await page.getByRole('button', { name: 'Search' }).click()
  await expect(page.getByRole('link', { name: textV2Title })).toBeVisible()

  await page.getByRole('link', { name: 'Add material' }).click()
  await page.getByRole('radio', { name: /File material/ }).check()
  await page.getByLabel(/^Title/).fill(fileTitle)
  await page.getByLabel(/^Manual tags/).fill('photo, evidence')
  await page.getByLabel(/^Files/).setInputFiles([
    { name: successFilename, mimeType: 'image/png', buffer: onePixelPng },
    { name: failedFilename, mimeType: 'image/png', buffer: onePixelPng },
  ])
  await page.getByLabel(/^Material notes/).fill('Two independent image assets; one intentionally exercises AI failure isolation.')
  await page.getByRole('button', { name: 'Create material' }).click()
  await expect(page.getByRole('heading', { name: fileTitle })).toBeVisible()
  const fileMaterialUrl = page.url()
  const successCard = page.getByRole('article', { name: successFilename })
  const failedCard = page.getByRole('article', { name: failedFilename })
  await expect(successCard).toBeVisible()
  await expect(failedCard).toBeVisible()

  await expect.poll(async () => {
    await page.reload()
    return successCard.locator('.status-pill').textContent()
  }, { timeout: 20_000 }).toBe('COMPLETED')
  await expect(successCard.getByText(/Local demo analysis for image/)).toBeVisible()
  await expect(successCard.getByRole('list', { name: 'AI tags' })).toContainText('demo-analysis')

  await expect.poll(async () => {
    await page.reload()
    return failedCard.locator('.status-pill').textContent()
  }, { timeout: 20_000 }).toBe('FAILED')
  await expect(failedCard.getByRole('button', { name: 'Retry AI analysis' })).toBeVisible()
  await expect(page.getByText('Two independent image assets; one intentionally exercises AI failure isolation.')).toBeVisible()

  const downloadPromise = page.waitForEvent('download')
  await successCard.getByRole('button', { name: `Download ${successFilename}` }).click()
  const download = await downloadPromise
  expect(download.suggestedFilename()).toBe(successFilename)
  await download.delete()

  await page.getByRole('link', { name: 'Project materials' }).click()
  await query.fill('demo-analysis')
  await page.getByRole('button', { name: 'Search' }).click()
  await expect(page.getByRole('link', { name: fileTitle })).toBeVisible()
  await expect(page.getByText('Matched an AI-analyzed image')).toBeVisible()

  if (usesMobileNavigation(page)) {
    await page.getByRole('button', { name: 'Open navigation' }).click()
  }
  await page.getByRole('button', { name: 'Log out' }).click()
  await expect(page).toHaveURL(/\/login$/)
  await login(page, 'alex')
  await page.goto(fileMaterialUrl)
  await expect(page.getByRole('heading', { name: fileTitle })).toBeVisible()
  await expect(page.getByRole('link', { name: 'Create new version' })).toHaveCount(0)

  const denied = await page.evaluate(async ({ materialId }) => {
    const csrf = await fetch('/api/v1/auth/csrf').then((response) => response.json()) as {
      token: string
      headerName: string
    }
    const response = await fetch(`/api/v1/materials/${materialId}/versions`, {
      method: 'POST',
      credentials: 'include',
      headers: {
        'Content-Type': 'application/json',
        'Idempotency-Key': crypto.randomUUID(),
        [csrf.headerName]: csrf.token,
      },
      body: JSON.stringify({
        expectedCurrentVersion: 2,
        title: 'Unauthorized version',
        manualTags: [],
        textBody: 'This write must be rejected by the server.',
      }),
    })
    return { status: response.status, body: await response.json() }
  }, { materialId: textMaterialId })
  expect(denied.status).toBe(403)
  expect(denied.body.code).toBe('FORBIDDEN')
})
