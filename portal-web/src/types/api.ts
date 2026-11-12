export interface ApiResponse<T> {
  code: number
  message: string
  data: T
  fieldErrors?: { field: string; reason: string }[] | null
}

export interface PageResult<T> {
  list: T[]
  total: number
  page: number
  pageSize: number
}
