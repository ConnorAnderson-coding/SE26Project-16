/**
 * Format activity hotness score for display.
 * Backend stores a floating composite score (views/signups/check-ins/favorites + decay).
 */
export function formatHotness(score) {
  const value = Number(score)
  if (!Number.isFinite(value) || value <= 0) {
    return '0'
  }
  if (value >= 100) {
    return value.toFixed(0)
  }
  if (value >= 10) {
    return value.toFixed(1)
  }
  return value.toFixed(2)
}

export function getHotnessValue(activity) {
  if (!activity) {
    return 0
  }
  const value = Number(activity.hotnessScore)
  return Number.isFinite(value) ? value : 0
}
