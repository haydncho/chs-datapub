import type { A3Arrival, A3Source, A3SourceStatus } from '@/mock/A3'

/** A source with its effective (state-adjusted) status and history. */
export interface A3Row {
  src: A3Source
  status: A3SourceStatus
  history: A3Arrival[]
}
