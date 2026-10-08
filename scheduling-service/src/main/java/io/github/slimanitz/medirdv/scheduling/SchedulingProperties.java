package io.github.slimanitz.medirdv.scheduling;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

import java.time.Duration;
import java.time.ZoneId;

/**
 * @param zone              time zone in which availabilities are expressed (practices are in France)
 * @param validationTimeout PENDING_VALIDATION appointments older than this are rejected by the saga timeout
 * @param maxSlotRangeDays  maximum number of days requested at once on the slots endpoint
 */
@ConfigurationProperties("medirdv.scheduling")
public record SchedulingProperties(@DefaultValue("Europe/Paris") ZoneId zone,
                                   @DefaultValue("PT2M") Duration validationTimeout,
                                   @DefaultValue("31") int maxSlotRangeDays) {
}
