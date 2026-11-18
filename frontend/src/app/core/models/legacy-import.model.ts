/** Mirrors com.courier.modules.legacyimport.application.* — what the import would do / did with a row. */
export type LegacyRowOutcome = 'NEW' | 'EXISTING' | 'INVALID';

export interface LegacyPreviewRow {
  rowNo: number;
  consignmentNo: string;
  outcome: LegacyRowOutcome;
  reason: string | null;
  differences: string[];
  origin: string;
}

/** A legacy "From" text, how many NEW rows use it, and the branch it maps to (null = unmapped). */
export interface LegacyOriginSummary {
  originKey: string;
  newRows: number;
  mappedBranchId: string | null;
}

export interface LegacyPreviewResult {
  fileHash: string;
  total: number;
  newCount: number;
  existingCount: number;
  differsCount: number;
  invalidCount: number;
  origins: LegacyOriginSummary[];
  rows: LegacyPreviewRow[];
}

export interface LegacyCommitResult {
  batchId: string | null;
  created: number;
  skippedExisting: number;
  invalid: number;
  rows: LegacyPreviewRow[];
}

export interface LegacyImportBatch {
  id: string;
  fileName: string;
  newCount: number;
  existingCount: number;
  invalidCount: number;
  status: string;
  createdAt: string;
}
