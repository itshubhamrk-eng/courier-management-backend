/** The backend's uniform response envelope (ARCHITECTURE §6). Every endpoint returns this. */
export interface ApiResponse<T> {
  success: boolean;
  message: string;
  data: T;
  errorCode?: string;
  errors?: FieldError[];
  timestamp: string;
  path?: string;
  requestId?: string;
}

export interface FieldError {
  field: string;
  message: string;
  rejectedValue?: unknown;
}
