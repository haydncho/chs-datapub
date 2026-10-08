import { session } from '@/app/session'
import { SEED_ORG } from '@/mock/B1'

/**
 * Seed to render before the server answers (or when it is unreachable). The bundled seeds are
 * 示例市第一人民医院's own figures, so a hospital identity of any other organisation starts from the
 * empty variant instead — it must never see another institution's 本院具名 data, not even briefly.
 */
export function ownSeed<T>(seed: T, empty: T): T {
  const id = session.current?.identity
  if (id && id.role === 'hospital' && id.orgId !== SEED_ORG) return empty
  return seed
}

/** organisation name for the empty state header */
export function viewerOrgName(): string {
  return session.current?.identity.orgName ?? ''
}
