export function getActivityTimeSlot(isoString) {
  if (!isoString) return null
  const d = new Date(isoString)
  const day = d.getDay()
  const hour = d.getHours()

  if (day === 0 || day === 6) return 'weekend'
  if (hour < 12) return 'weekday_morning'
  if (hour < 18) return 'weekday_afternoon'
  return 'weekday_evening'
}

export function matchesInterestTags(activity, tags) {
  if (!tags?.length) return true
  const activityTags = activity.tags || []
  return tags.some(t => activityTags.includes(t))
}

export function matchesTimeRange(activity, range) {
  if (!range?.[0] || !range?.[1]) return true
  const start = new Date(activity.startTime).getTime()
  const rangeStart = range[0].startOf('day').valueOf()
  const rangeEnd = range[1].endOf('day').valueOf()
  return start >= rangeStart && start <= rangeEnd
}

export function matchesTimeSlots(activity, slots) {
  if (!slots?.length) return true
  const slot = getActivityTimeSlot(activity.startTime)
  return slots.includes(slot)
}

export function matchesAvailableTime(activity, userSlots) {
  if (!userSlots?.length) return true
  const slot = getActivityTimeSlot(activity.startTime)
  return userSlots.includes(slot)
}

export function matchesActivityStatus(activity, status) {
  if (!status) return true
  return activity.status === status
}
