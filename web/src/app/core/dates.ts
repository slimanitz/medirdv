/** Date helpers. Practices are in France: everything is displayed in Europe/Paris time. */
export const PARIS = 'Europe/Paris';

const dayKeyFormat = new Intl.DateTimeFormat('en-CA', {
  timeZone: PARIS,
  year: 'numeric',
  month: '2-digit',
  day: '2-digit',
});

/** ISO calendar day (YYYY-MM-DD) of an instant, in Paris time. */
export function parisDayKey(instant: string | Date): string {
  return dayKeyFormat.format(typeof instant === 'string' ? new Date(instant) : instant);
}

/** Monday (local calendar) of the week containing the date. */
export function startOfWeek(date: Date): Date {
  const result = new Date(date.getFullYear(), date.getMonth(), date.getDate());
  const offset = (result.getDay() + 6) % 7;
  result.setDate(result.getDate() - offset);
  return result;
}

export function addDays(date: Date, days: number): Date {
  const result = new Date(date);
  result.setDate(result.getDate() + days);
  return result;
}

/** YYYY-MM-DD of a local calendar date. */
export function isoDay(date: Date): string {
  const month = String(date.getMonth() + 1).padStart(2, '0');
  const day = String(date.getDate()).padStart(2, '0');
  return `${date.getFullYear()}-${month}-${day}`;
}

/** The 7 ISO days of the week starting at the given Monday. */
export function weekDays(monday: Date): string[] {
  return Array.from({ length: 7 }, (_, i) => isoDay(addDays(monday, i)));
}

/** Groups items by Paris day, keeping only the given days, in order. */
export function groupByDay<T>(items: T[], start: (item: T) => string, days: string[]): Map<string, T[]> {
  const groups = new Map<string, T[]>(days.map((d) => [d, []]));
  for (const item of [...items].sort((a, b) => start(a).localeCompare(start(b)))) {
    groups.get(parisDayKey(start(item)))?.push(item);
  }
  return groups;
}
