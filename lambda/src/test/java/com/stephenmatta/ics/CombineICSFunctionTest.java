package com.stephenmatta.ics;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.LambdaLogger;
import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.util.List;
import java.util.Map;
import net.fortuna.ical4j.data.CalendarBuilder;
import net.fortuna.ical4j.model.Calendar;
import net.fortuna.ical4j.model.Component;
import net.fortuna.ical4j.model.component.CalendarComponent;
import net.fortuna.ical4j.model.component.VEvent;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

class CombineICSFunctionTest {

    private CombineICSFunction function;
    private Context context;
    private Configuration mockConfiguration;
    private LambdaLogger mockLambdaLogger;
    private MockWebServer mockWebServer;

    @BeforeEach
    void setUp() throws Exception {
        mockWebServer = new MockWebServer();
        mockWebServer.start();

        mockConfiguration = Mockito.mock(Configuration.class);
        mockLambdaLogger = Mockito.mock(LambdaLogger.class);

        function = new CombineICSFunction(mockConfiguration);
        context = Mockito.mock(Context.class);
        when(context.getLogger()).thenReturn(mockLambdaLogger);
    }

    @AfterEach
    void tearDown() throws Exception {
        mockWebServer.shutdown();
    }

    @Test
    void shouldProcessSingleEventCalendarSuccessfully() throws Exception {
        String icsData = "BEGIN:VCALENDAR\nVERSION:2.0\nBEGIN:VEVENT\nUID:event1@example.com\nSUMMARY:Test Event\nDTSTART:20230901T120000Z\nDTEND:20230901T130000Z\nEND:VEVENT\nEND:VCALENDAR";
        mockWebServer.enqueue(
            new MockResponse().setBody(icsData).addHeader("Content-Type", "text/calendar"));

        when(mockConfiguration.getCalendarUrls()).thenReturn(
            List.of(mockWebServer.url("/event1.ics").toString()));

        Map<String, Object> response = function.handleRequest(null, context);
        assertThat(response).isNotNull();
        assertThat(response.get("statusCode")).isEqualTo(200);
        assertThat((Map<String, String>) response.get("headers")).containsEntry("Content-Type",
            "text/calendar");

        List<CalendarComponent> events = parseICSResponse((String) response.get("body"));

        assertThat(events).hasSize(1);
        assertThat(events).extracting(component -> ((VEvent) component).getUid().get().getValue())
            .contains("event1@example.com");
    }

    @Test
    void shouldProcessMultipleEventCalendarsSuccessfully() throws Exception {
        String icsData1 = "BEGIN:VCALENDAR\nVERSION:2.0\nBEGIN:VEVENT\nUID:event1@example.com\nSUMMARY:Test Event 1\nDTSTART:20230901T120000Z\nDTEND:20230901T130000Z\nEND:VEVENT\nEND:VCALENDAR";
        String icsData2 = "BEGIN:VCALENDAR\nVERSION:2.0\nBEGIN:VEVENT\nUID:event2@example.com\nSUMMARY:Test Event 2\nDTSTART:20230902T120000Z\nDTEND:20230902T130000Z\nEND:VEVENT\nEND:VCALENDAR";

        mockWebServer.enqueue(
            new MockResponse().setBody(icsData1).addHeader("Content-Type", "text/calendar"));
        mockWebServer.enqueue(
            new MockResponse().setBody(icsData2).addHeader("Content-Type", "text/calendar"));

        when(mockConfiguration.getCalendarUrls()).thenReturn(List.of(
            mockWebServer.url("/event1.ics").toString(),
            mockWebServer.url("/event2.ics").toString()
        ));

        Map<String, Object> response = function.handleRequest(null, context);
        assertThat(response).isNotNull();
        assertThat(response.get("statusCode")).isEqualTo(200);
        assertThat((Map<String, String>) response.get("headers")).containsEntry("Content-Type",
            "text/calendar");

        List<CalendarComponent> events = parseICSResponse((String) response.get("body"));

        assertThat(events).hasSize(2);
        assertThat(events).extracting(component -> component.getUid().get().getValue())
            .containsExactlyInAnyOrder("event1@example.com", "event2@example.com");
    }

    @Test
    void shouldProcessEmptyCalendarSuccessfully() throws Exception {
        mockWebServer.enqueue(new MockResponse()
            .setBody("BEGIN:VCALENDAR\nVERSION:2.0\nEND:VCALENDAR")
            .addHeader("Content-Type", "text/calendar"));

        when(mockConfiguration.getCalendarUrls()).thenReturn(
            List.of(mockWebServer.url("/empty.ics").toString()));

        Map<String, Object> response = function.handleRequest(null, context);
        assertThat(response).isNotNull();
        assertThat(response.get("statusCode")).isEqualTo(200);

        List<CalendarComponent> events = parseICSResponse((String) response.get("body"));

        assertThat(events).isEmpty();
    }

    @Test
    void shouldReturnErrorResponseOnServerError() {
        mockWebServer.enqueue(new MockResponse().setResponseCode(500));

        when(mockConfiguration.getCalendarUrls()).thenReturn(
            List.of(mockWebServer.url("/error.ics").toString()));

        Map<String, Object> response = function.handleRequest(null, context);
        assertThat(response).isNotNull();
        assertThat(response.get("statusCode")).isEqualTo(500);
        assertThat((String) response.get("body")).contains("Internal Server Error");
    }

    private List<CalendarComponent> parseICSResponse(String icsContent) throws Exception {
        InputStream icsInputStream = new ByteArrayInputStream(icsContent.getBytes());
        CalendarBuilder builder = new CalendarBuilder();
        Calendar calendar = builder.build(icsInputStream);
        return calendar.getComponents(Component.VEVENT);
    }
}
