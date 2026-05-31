/*
 * Service layer for plugin "scm-github".
 *
 * Tool-level plugin (lives at `service:scm:github`). The parent
 * `plugin-scm` delegates the subscription-row hooks to us via its
 * `subPluginIdFor` delegation. Mirrors the legacy `github.js`:
 *
 *   - renderFeatures   → a home link to the GitHub repository
 *     (`https://github.com/<user>/<repository>`).
 *   - renderDetailsKey → the repository chip
 *     (`service:scm:github:repository`).
 *
 * The legacy live contributor sparkline + issues/stars/watchers counts
 * read `subscription.data` and are omitted here, like the other
 * live-data carousels.
 *
 * Kept free of Vue SFC imports so it can be unit-tested without a DOM.
 */
import { h } from 'vue'
import { VBtn, VChip, VIcon, useI18nStore } from '@ligoj/host'

const PARAM_USER = 'service:scm:github:user'
const PARAM_REPO = 'service:scm:github:repository'

/** GitHub repository home link. Mirrors the legacy renderFeatures(). */
function renderFeatures(subscription) {
  const params = subscription?.parameters
  const user = params?.[PARAM_USER]
  const repo = params?.[PARAM_REPO]
  if (!user || !repo) return []
  const { t } = useI18nStore()
  return [
    h(
      VBtn,
      {
        icon: true,
        size: 'small',
        variant: 'text',
        title: t('service:scm:github:repository'),
        href: `https://github.com/${encodeURIComponent(user)}/${encodeURIComponent(repo)}`,
        target: '_blank',
        rel: 'noopener noreferrer',
      },
      () => h(VIcon, { size: 'small' }, () => 'mdi-github'),
    ),
  ]
}

/** Repository chip. Mirrors the legacy renderKey('service:scm:github:repository'). */
function renderDetailsKey(subscription) {
  const repo = subscription?.parameters?.[PARAM_REPO]
  if (!repo) return null
  const { t } = useI18nStore()
  return h(
    VChip,
    { size: 'small', variant: 'tonal', class: 'mr-1', title: t('service:scm:github:repository') },
    () => [h(VIcon, { start: true, size: 'small' }, () => 'mdi-github'), ' ', String(repo)],
  )
}

export default { renderFeatures, renderDetailsKey }
