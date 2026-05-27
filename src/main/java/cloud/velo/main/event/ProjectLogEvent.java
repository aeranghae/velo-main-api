package cloud.velo.main.event;

import cloud.velo.main.domain.ProjectStatus;

public record ProjectLogEvent(
        String uuid,
        String logLevel, // INFO, ERROR, WARN, DEBUG 등
        String message,
        ProjectStatus status,
        boolean isActivityFeed) {}