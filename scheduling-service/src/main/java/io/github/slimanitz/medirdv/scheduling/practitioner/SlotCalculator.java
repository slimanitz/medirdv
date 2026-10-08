package io.github.slimanitz.medirdv.scheduling.practitioner;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * Computes free slots: weekly availabilities expanded over a date range, minus slots overlapping
 * a busy (pending or confirmed) appointment, minus slots that already started. Pure function,
 * no I/O, so it is unit-tested exhaustively.
 */
public final class SlotCalculator {

    private SlotCalculator() {
    }

    /**
     * @param from inclusive first day, in {@code zone}
     * @param to   inclusive last day, in {@code zone}
     * @param busy time ranges already taken
     * @param now  slots starting before this instant are not offered
     */
    public static List<Slot> freeSlots(List<Availability> availabilities, List<Slot> busy, LocalDate from,
                                       LocalDate to, ZoneId zone, Instant now) {
        List<Slot> result = new ArrayList<>();
        for (LocalDate day = from; !day.isAfter(to); day = day.plusDays(1)) {
            for (Availability availability : availabilities) {
                if (availability.getWeekday() != day.getDayOfWeek()) {
                    continue;
                }
                LocalTime cursor = availability.getStartTime();
                while (!cursor.plusMinutes(availability.getSlotMinutes()).isAfter(availability.getEndTime())
                        && cursor.plusMinutes(availability.getSlotMinutes()).isAfter(cursor)) {
                    ZonedDateTime zoned = ZonedDateTime.of(day, cursor, zone);
                    // Spring-forward DST gap (e.g. 02:00-03:00 on the last Sunday of March in Paris):
                    // the local time does not exist and would be shifted onto the next slot. Skip it.
                    if (zoned.toLocalTime().equals(cursor)) {
                        Instant start = zoned.toInstant();
                        Instant end = start.plusSeconds(availability.getSlotMinutes() * 60L);
                        Slot slot = new Slot(start, end);
                        if (!start.isBefore(now) && busy.stream().noneMatch(b -> slot.overlaps(b.start(), b.end()))
                                && result.stream().noneMatch(s -> s.start().equals(start))) {
                            result.add(slot);
                        }
                    }
                    cursor = cursor.plusMinutes(availability.getSlotMinutes());
                }
            }
        }
        result.sort(Comparator.comparing(Slot::start));
        return result;
    }
}
