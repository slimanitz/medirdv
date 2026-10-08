package io.github.slimanitz.medirdv.scheduling.practitioner;

import org.junit.jupiter.api.Test;

import java.time.DayOfWeek;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class SlotCalculatorTest {

    private static final ZoneId PARIS = ZoneId.of("Europe/Paris");
    private static final UUID PRACTITIONER = UUID.randomUUID();
    // Monday 12 October 2026
    private static final LocalDate MONDAY = LocalDate.of(2026, 10, 12);
    private static final Instant LONG_AGO = Instant.parse("2020-01-01T00:00:00Z");

    private static Availability availability(DayOfWeek day, String start, String end, int minutes) {
        return new Availability(UUID.randomUUID(), PRACTITIONER, day, LocalTime.parse(start), LocalTime.parse(end),
                minutes);
    }

    @Test
    void cutsAvailabilityIntoSlotsInParisTime() {
        List<Slot> slots = SlotCalculator.freeSlots(List.of(availability(DayOfWeek.MONDAY, "09:00", "10:00", 30)),
                List.of(), MONDAY, MONDAY, PARIS, LONG_AGO);

        assertThat(slots).containsExactly(
                new Slot(Instant.parse("2026-10-12T07:00:00Z"), Instant.parse("2026-10-12T07:30:00Z")),
                new Slot(Instant.parse("2026-10-12T07:30:00Z"), Instant.parse("2026-10-12T08:00:00Z")));
    }

    @Test
    void dropsTrailingPartialSlot() {
        List<Slot> slots = SlotCalculator.freeSlots(List.of(availability(DayOfWeek.MONDAY, "09:00", "10:10", 30)),
                List.of(), MONDAY, MONDAY, PARIS, LONG_AGO);

        assertThat(slots).hasSize(2);
    }

    @Test
    void removesBusySlotsAndOverlaps() {
        Slot busy = new Slot(Instant.parse("2026-10-12T07:15:00Z"), Instant.parse("2026-10-12T07:45:00Z"));

        List<Slot> slots = SlotCalculator.freeSlots(List.of(availability(DayOfWeek.MONDAY, "09:00", "10:30", 30)),
                List.of(busy), MONDAY, MONDAY, PARIS, LONG_AGO);

        assertThat(slots).extracting(Slot::start)
                .containsExactly(Instant.parse("2026-10-12T08:00:00Z"));
    }

    @Test
    void neverOffersPastSlots() {
        Instant now = Instant.parse("2026-10-12T07:10:00Z");

        List<Slot> slots = SlotCalculator.freeSlots(List.of(availability(DayOfWeek.MONDAY, "09:00", "10:00", 30)),
                List.of(), MONDAY, MONDAY, PARIS, now);

        assertThat(slots).extracting(Slot::start).containsExactly(Instant.parse("2026-10-12T07:30:00Z"));
    }

    @Test
    void expandsOnlyMatchingWeekdaysOverARangeAndSortsResult() {
        var availabilities = List.of(
                availability(DayOfWeek.WEDNESDAY, "14:00", "15:00", 60),
                availability(DayOfWeek.MONDAY, "09:00", "10:00", 60));

        List<Slot> slots = SlotCalculator.freeSlots(availabilities, List.of(), MONDAY, MONDAY.plusDays(13), PARIS,
                LONG_AGO);

        assertThat(slots).extracting(Slot::start).containsExactly(
                Instant.parse("2026-10-12T07:00:00Z"),  // Mon 09:00 CEST
                Instant.parse("2026-10-14T12:00:00Z"),  // Wed 14:00 CEST
                Instant.parse("2026-10-19T07:00:00Z"),  // Mon 09:00 CEST
                Instant.parse("2026-10-21T12:00:00Z")); // Wed 14:00 CEST
    }

    @Test
    void followsDaylightSavingTimeChange() {
        // Monday 2 November 2026 is after the switch to winter time (UTC+1).
        LocalDate winterMonday = LocalDate.of(2026, 11, 2);

        List<Slot> slots = SlotCalculator.freeSlots(List.of(availability(DayOfWeek.MONDAY, "09:00", "09:30", 30)),
                List.of(), winterMonday, winterMonday, PARIS, LONG_AGO);

        assertThat(slots).extracting(Slot::start).containsExactly(Instant.parse("2026-11-02T08:00:00Z"));
    }

    @Test
    void skipsSlotsThatDoNotExistDuringTheSpringForwardGap() {
        // Sunday 28 March 2027: Paris clocks jump from 02:00 to 03:00.
        LocalDate dstSunday = LocalDate.of(2027, 3, 28);

        List<Slot> slots = SlotCalculator.freeSlots(List.of(availability(DayOfWeek.SUNDAY, "01:30", "04:00", 30)),
                List.of(), dstSunday, dstSunday, PARIS, LONG_AGO);

        assertThat(slots).extracting(Slot::start).containsExactly(
                Instant.parse("2027-03-28T00:30:00Z"),  // 01:30 CET
                Instant.parse("2027-03-28T01:00:00Z"),  // 03:00 CEST
                Instant.parse("2027-03-28T01:30:00Z")); // 03:30 CEST
        assertThat(slots).doesNotHaveDuplicates();
    }
}
