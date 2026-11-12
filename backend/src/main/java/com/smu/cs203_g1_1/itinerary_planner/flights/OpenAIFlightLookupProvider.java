package com.smu.cs203_g1_1.itinerary_planner.flights;

import java.time.LocalDate;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

@Component
public class OpenAIFlightLookupProvider implements FlightLookupProvider {

    private final RestClient restClient;
    private final ObjectMapper objectMapper;
    private final String apiKey;
    private final String model;

    public OpenAIFlightLookupProvider(
        RestClient.Builder builder,
        @Value("${OPENAI_API_KEY:}") String apiKey,
        @Value("${app.flights.openai.model}") String model) {

        this.restClient = builder
                .baseUrl("https://api.openai.com/v1")
                .defaultHeader("Authorization", "Bearer " + apiKey)
                .build();

        this.objectMapper = new ObjectMapper();
        this.apiKey = apiKey;
        this.model = model;
    }

    @Override
    public List<FlightRecord> findFlights(
            String flightNumber,
            LocalDate flightDate) {

        if (apiKey == null || apiKey.isBlank()) {
            throw new IllegalStateException(
                    "OpenAI API key is not configured");
        }

        try {
            String response = restClient.post()
                .uri("/responses")
                .contentType(MediaType.APPLICATION_JSON)
                .body(buildRequest(flightNumber, flightDate))
                .retrieve()
                .body(String.class);

            JsonNode responseJson = objectMapper.readTree(response);
            String output = extractOutputText(responseJson);

            if (output == null || output.isBlank()) {
                return List.of();
            }

            return parseFlights(output, flightNumber, flightDate);

        } catch (Exception e) {
            throw new IllegalStateException(
                    "Failed to retrieve flight information using OpenAI",
                    e);
        }
    }

    private Object buildRequest(
            String flightNumber,
            LocalDate flightDate) {

        String prompt = """
                Search the web for flight information matching:

                Flight number: %s
                Flight date: %s

                Find flights matching this exact flight number and date.
                Use web search to verify the information.

                Return ONLY a valid JSON array. Do not include Markdown,
                explanations, or code fences.

                Each array element must have these fields:
                {
                  "flightNumber": "flight number",
                  "airline": "airline name or null",
                  "departureAirportCode": "IATA code or null",
                  "departureAirportName": "airport name or null",
                  "departureTerminal": "terminal or null",
                  "arrivalAirportCode": "IATA code or null",
                  "arrivalAirportName": "airport name or null",
                  "arrivalTerminal": "terminal or null",
                  "departureTime": "ISO-8601 datetime with UTC offset or null",
                  "arrivalTime": "ISO-8601 datetime with UTC offset or null"
                }

                Important rules:
                - Do not invent or estimate missing information.
                - Use null for any field you cannot verify.
                - Include the timezone offset in both datetime fields.
                - Return an empty array if no matching flight is found.
                - Do not return flights with a different flight number or date.
                """.formatted(flightNumber, flightDate);

        return java.util.Map.of(
                "model", model,
                "tools", List.of(
                    Map.of(
                        "type", "web_search",
                        "search_context_size", "medium"
                    )
                ),
                "tool_choice", "required",
                "include", List.of("web_search_call.action.sources"),
                "input", prompt
        );
    }

    private String extractOutputText(JsonNode response) {

        if (response == null || !response.path("output").isArray()) {
            return null;
        }

        for (JsonNode outputItem : response.path("output")) {

            if (!"message".equals(outputItem.path("type").asText())) {
                continue;
            }

            for (JsonNode content : outputItem.path("content")) {

                if ("output_text".equals(content.path("type").asText())) {
                    return content.path("text").asText(null);
                }
            }
        }

        return null;
    }

    private List<FlightRecord> parseFlights(
            String output,
            String requestedFlightNumber,
            LocalDate requestedDate) throws Exception {

        JsonNode flightsJson = objectMapper.readTree(output);

        if (!flightsJson.isArray()) {
            throw new IllegalStateException(
                    "OpenAI returned an invalid flight response");
        }

        List<FlightRecord> flights = new ArrayList<>();

        for (JsonNode flight : flightsJson) {

            String returnedFlightNumber =
                    text(flight, "flightNumber");

            // Reject results that do not match the requested flight.
            if (returnedFlightNumber == null
                    || !returnedFlightNumber.replaceAll("\\s+", "")
                            .equalsIgnoreCase(
                                    requestedFlightNumber.replaceAll("\\s+", ""))) {
                continue;
            }

            ZonedDateTime departureTime =
                    parseTime(flight, "departureTime");

            ZonedDateTime arrivalTime =
                    parseTime(flight, "arrivalTime");

            // Reject results that do not contain both required times.
            if (departureTime == null || arrivalTime == null) {
                continue;
            }

            // Check the departure date in its own local timezone.
            if (!departureTime.toLocalDate().equals(requestedDate)) {
                continue;
            }

            flights.add(new FlightRecord(
                    returnedFlightNumber,
                    text(flight, "airline"),

                    text(flight, "departureAirportCode"),
                    text(flight, "departureAirportName"),
                    text(flight, "departureTerminal"),

                    text(flight, "arrivalAirportCode"),
                    text(flight, "arrivalAirportName"),
                    text(flight, "arrivalTerminal"),

                    departureTime,
                    arrivalTime
            ));
        }

        return flights;
    }

    private static String text(JsonNode node, String field) {
        JsonNode value = node.get(field);

        if (value == null || value.isNull()) {
            return null;
        }

        String text = value.asText();

        return text.isBlank() ? null : text;
    }

    private static ZonedDateTime parseTime(
            JsonNode node,
            String field) {

        String value = text(node, field);

        if (value == null) {
            return null;
        }

        try {
            return ZonedDateTime.parse(value);
        } catch (Exception e) {
            return null;
        }
    }
}
