package com.stephenmatta.ics;

import static net.fortuna.ical4j.model.Component.VEVENT;
import static net.fortuna.ical4j.model.Property.DTSTAMP;

import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.RequestHandler;
import java.io.IOException;
import java.io.InputStream;
import java.io.StringWriter;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import net.fortuna.ical4j.data.CalendarBuilder;
import net.fortuna.ical4j.data.CalendarOutputter;
import net.fortuna.ical4j.data.ParserException;
import net.fortuna.ical4j.model.Calendar;
import net.fortuna.ical4j.model.property.DtStamp;
import org.jetbrains.annotations.TestOnly;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class CombineICSFunction implements RequestHandler<Object, Map<String, Object>> {

    private static final Logger log = LoggerFactory.getLogger(CombineICSFunction.class);
    private static final HttpClient httpClient = HttpClient.newHttpClient();
    private final Configuration configuration;

    @SuppressWarnings("unused") // Used by AWS Lambda
    public CombineICSFunction() {
        this(new Configuration());
    }

    @TestOnly
    public CombineICSFunction(Configuration configuration) {
        this.configuration = configuration;
    }

    @Override
    public Map<String, Object> handleRequest(Object input, Context context) {
        try {
            return processCalendars();
        } catch (Exception e) {
            log.error("Error processing request: ", e);
            return createErrorResponse(500, "Internal Server Error");
        }
    }

    private Map<String, Object> processCalendars() throws InterruptedException, ExecutionException {
        List<String> calendarUrls = configuration.getCalendarUrls();
        List<CompletableFuture<Calendar>> calendarFutures = fetchAndParseCalendarsAsync(
            calendarUrls);

        Calendar combinedCalendar = calendarFutures.stream()
            .map(CompletableFuture::join)
            .reduce(new Calendar(), this::combineCalendars);

        combinedCalendar.withProdId("-//Stephen Matta//iCal4j 1.0//EN").withDefaults();
        return generateICSResponse(combinedCalendar);
    }

    private List<CompletableFuture<Calendar>> fetchAndParseCalendarsAsync(
        List<String> calendarUrls) {
        return calendarUrls.stream()
            .map(this::fetchAndParseCalendarAsync)
            .toList();
    }

    private CompletableFuture<Calendar> fetchAndParseCalendarAsync(String url) {
        HttpRequest request = HttpRequest.newBuilder(URI.create(url)).build();
        return httpClient.sendAsync(request, HttpResponse.BodyHandlers.ofInputStream())
            .thenApply(this::validateResponse)
            .thenApply(response -> parseCalendar(response.body()))
            .exceptionally(e -> {
                throw new FetchCalendarException("Error fetching or parsing ICS from " + url, e);
            });
    }

    private HttpResponse<InputStream> validateResponse(HttpResponse<InputStream> response) {
        if (response.statusCode() != 200) {
            throw new FetchCalendarException("Failed to fetch ICS: HTTP " + response.statusCode());
        }
        return response;
    }

    private Calendar parseCalendar(InputStream inputStream) {
        try (inputStream) {
            Calendar calendar = new CalendarBuilder().build(inputStream);
            ensureDtStamp(calendar);
            return calendar;
        } catch (IOException | ParserException e) {
            throw new ParseCalendarException("Error parsing calendar", e);
        }
    }

    private void ensureDtStamp(Calendar calendar) {
        calendar.getComponents(VEVENT).forEach(event -> {
            if (event.getProperty(DTSTAMP).isEmpty()) {
                event.add(new DtStamp());
            }
        });
    }

    private Calendar combineCalendars(Calendar combined, Calendar toAdd) {
        toAdd.getComponents(VEVENT).forEach(combined::add);
        return combined;
    }

    private Map<String, Object> generateICSResponse(Calendar combinedCalendar) {
        try (StringWriter writer = new StringWriter()) {
            new CalendarOutputter().output(combinedCalendar, writer);
            return createResponse(200, writer.toString());
        } catch (IOException e) {
            log.error("Error generating ICS response", e);
            return createErrorResponse(500, "Error generating calendar content");
        }
    }

    private Map<String, Object> createResponse(int statusCode, String body) {
        return Map.of(
            "statusCode", statusCode,
            "headers", Map.of("Content-Type", "text/calendar"),
            "body", body,
            "isBase64Encoded", false
        );
    }

    private Map<String, Object> createErrorResponse(int statusCode, String message) {
        return Map.of(
            "statusCode", statusCode,
            "headers", Map.of("Content-Type", "text/plain"),
            "body", message,
            "isBase64Encoded", false
        );
    }

    private static class FetchCalendarException extends RuntimeException {

        public FetchCalendarException(String message) {
            super(message);
        }

        public FetchCalendarException(String message, Throwable cause) {
            super(message, cause);
        }
    }

    private static class ParseCalendarException extends RuntimeException {

        public ParseCalendarException(String message, Throwable cause) {
            super(message, cause);
        }
    }
}
