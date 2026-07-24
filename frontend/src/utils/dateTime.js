import dayjs from 'dayjs'

export function toLocalDateTimeString(value) {
  if (!value) return null
  const parsed = dayjs(value)
  if (!parsed.isValid()) return null
  return parsed.format('YYYY-MM-DDTHH:mm:ss')
}
