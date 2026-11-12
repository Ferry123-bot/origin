export interface ApiResponse<T> {
  code: number
  message: string
  data: T
  fieldErrors?: { field: string; reason: string }[] | null
}
