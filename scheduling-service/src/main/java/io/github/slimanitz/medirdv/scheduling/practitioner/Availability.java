package io.github.slimanitz.medirdv.scheduling.practitioner;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.DayOfWeek;
import java.time.LocalTime;
import java.util.UUID;

/** Weekly recurring opening hours of a practitioner, cut into slots of {@code slotMinutes}. */
@Entity
@Table(name = "availabilities")
public class Availability {

    @Id
    private UUID id;

    @Column(name = "practitioner_id", nullable = false)
    private UUID practitionerId;

    /** ISO day of week, 1 = Monday. */
    @Column(nullable = false)
    private int weekday;

    @Column(name = "start_time", nullable = false)
    private LocalTime startTime;

    @Column(name = "end_time", nullable = false)
    private LocalTime endTime;

    @Column(name = "slot_minutes", nullable = false)
    private int slotMinutes;

    protected Availability() {
    }

    public Availability(UUID id, UUID practitionerId, DayOfWeek weekday, LocalTime startTime, LocalTime endTime,
                        int slotMinutes) {
        if (!endTime.isAfter(startTime) || slotMinutes <= 0) {
            throw new IllegalArgumentException("Invalid availability");
        }
        this.id = id;
        this.practitionerId = practitionerId;
        this.weekday = weekday.getValue();
        this.startTime = startTime;
        this.endTime = endTime;
        this.slotMinutes = slotMinutes;
    }

    public UUID getId() {
        return id;
    }

    public UUID getPractitionerId() {
        return practitionerId;
    }

    public DayOfWeek getWeekday() {
        return DayOfWeek.of(weekday);
    }

    public LocalTime getStartTime() {
        return startTime;
    }

    public LocalTime getEndTime() {
        return endTime;
    }

    public int getSlotMinutes() {
        return slotMinutes;
    }
}
