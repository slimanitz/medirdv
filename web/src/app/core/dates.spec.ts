import { addDays, groupByDay, isoDay, parisDayKey, startOfWeek, weekDays } from './dates';

describe('dates', () => {
  it('computes the Paris calendar day of an instant', () => {
    // 23:30 UTC on 11 October is already 12 October in Paris (UTC+2).
    expect(parisDayKey('2026-10-11T23:30:00Z')).toBe('2026-10-12');
    expect(parisDayKey('2026-10-12T07:00:00Z')).toBe('2026-10-12');
  });

  it('starts weeks on Monday', () => {
    const wednesday = new Date(2026, 9, 14);
    const sunday = new Date(2026, 9, 18);

    expect(isoDay(startOfWeek(wednesday))).toBe('2026-10-12');
    expect(isoDay(startOfWeek(sunday))).toBe('2026-10-12');
    expect(weekDays(startOfWeek(sunday))).toEqual([
      '2026-10-12', '2026-10-13', '2026-10-14', '2026-10-15', '2026-10-16', '2026-10-17', '2026-10-18',
    ]);
    expect(isoDay(addDays(new Date(2026, 9, 31), 1))).toBe('2026-11-01');
  });

  it('groups items by day in chronological order and ignores other weeks', () => {
    const slots = [
      { start: '2026-10-12T08:00:00Z' },
      { start: '2026-10-12T07:00:00Z' },
      { start: '2026-10-14T12:00:00Z' },
      { start: '2026-10-20T12:00:00Z' },
    ];

    const groups = groupByDay(slots, (s) => s.start, weekDays(new Date(2026, 9, 12)));

    expect(groups.get('2026-10-12')!.map((s) => s.start)).toEqual([
      '2026-10-12T07:00:00Z',
      '2026-10-12T08:00:00Z',
    ]);
    expect(groups.get('2026-10-13')).toEqual([]);
    expect(groups.get('2026-10-14')!.length).toBe(1);
    expect([...groups.values()].flat().length).toBe(3);
  });
});
