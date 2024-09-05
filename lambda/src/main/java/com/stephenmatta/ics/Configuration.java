package com.stephenmatta.ics;

import java.util.Arrays;
import java.util.List;

public class Configuration {

    private final List<String> calendarUrls;

    public Configuration() {
        var urls = System.getenv("ICS_URLS");
        if (urls == null || urls.isEmpty()) {
            throw new IllegalArgumentException(
                "Required environment variable ICS_URLS is not set or is empty.");
        }
        calendarUrls = Arrays.stream(urls.split(",")).map(String::trim).toList();
    }

    public List<String> getCalendarUrls() {
        return calendarUrls;
    }
}
