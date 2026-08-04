/** The backend's PageResponse<T> — a stable pagination envelope, not Spring's Page. */
export interface Page<T> {
  content: T[];
  page: number;
  size: number;
  totalElements: number;
  totalPages: number;
  first: boolean;
  last: boolean;
  hasNext: boolean;
}

/** Query params common to every paged/sorted/filtered list endpoint. */
export interface PageQuery {
  page?: number;
  size?: number;
  sort?: string; // e.g. "branchCode,asc"
  [filter: string]: string | number | boolean | undefined;
}

export const emptyPage = <T>(): Page<T> => ({
  content: [], page: 0, size: 20, totalElements: 0, totalPages: 0,
  first: true, last: true, hasNext: false
});
