package org.example;

import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.client.WireMock;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;

import java.util.Scanner;

public class PinTerminalActivator {
    private static final Logger logger = LoggerFactory.getLogger(PinTerminalActivator.class);

    private String customerId;
    private String macAddress;
    private WireMockServer wireMockServer;
    private String status;

    public PinTerminalActivator(String customerId, String macAddress) {
        this.customerId = customerId;
        this.macAddress = macAddress;
        this.wireMockServer = new WireMockServer(8080);
    }

    public void activatePinTerminal() {
        wireMockServer.start();
        configureWireMockStubs();

        int responseCode = sendRequestToSouthboundSystem();

        handleResponse(responseCode);

        wireMockServer.stop();
    }

    private void configureWireMockStubs() {
        // Wiremock 1: Successful activation
        WireMock.stubFor(WireMock.post(WireMock.urlEqualTo("/activate"))
                .withRequestBody(WireMock.equalToJson("{\"customerId\": \"" + customerId + "\", \"macAddress\": \"" + macAddress + "\"}"))
                .willReturn(WireMock.aResponse()
                        .withStatus(201)));

        // Wiremock 2: PIN terminal not found
        WireMock.stubFor(WireMock.post(WireMock.urlEqualTo("/activate"))
                .withRequestBody(WireMock.equalToJson("{\"customerId\": \"12345\", \"macAddress\": \"AA:BB:CC:DD:EE:AA\"}"))
                .willReturn(WireMock.aResponse()
                        .withStatus(404)));

        // Wiremock 3: Conflict - PIN terminal already attached to a different customer
        WireMock.stubFor(WireMock.post(WireMock.urlEqualTo("/activate"))
                .withRequestBody(WireMock.equalToJson("{\"customerId\": \"11111\", \"macAddress\": \"AA:BB:CC:DD:EE:FF\"}"))
                .willReturn(WireMock.aResponse()
                        .withStatus(409)));
    }

    private int sendRequestToSouthboundSystem() {
        try {
            URL url = new URL("https://o334q.wiremockapi.cloud/activate");  // Adjust the URL as needed

            // Open a connection to the URL
            HttpURLConnection connection = (HttpURLConnection) url.openConnection();

            connection.setRequestMethod("POST");

            // Enable input/output streams
            connection.setDoOutput(true);
            connection.setDoInput(true);

            // Set request body parameters
            String requestBody = "{\"customerId\": \"" + customerId + "\", \"macAddress\": \"" + macAddress + "\"}";
            connection.getOutputStream().write(requestBody.getBytes(StandardCharsets.UTF_8));

            // Get the HTTP response code
            int responseCode = connection.getResponseCode();

            // Read and log the response if needed
            StringBuilder response = new StringBuilder();
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(
                    responseCode < HttpURLConnection.HTTP_BAD_REQUEST ? connection.getInputStream() : connection.getErrorStream()))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    response.append(line);
                }
            }

            // Log the response using the logger
            logger.info("Response from southbound system: {}", response);

            // Close the connection
            connection.disconnect();

            return responseCode;
        } catch (java.io.IOException e) {
            // Log the exception using the logger
            logger.error("Error during southbound system request", e);
            return -1;  // Return a code indicating an error
        }
    }

    private void handleResponse(int responseCode) {
        switch (responseCode) {
            case 201:
                sendStatusToOrchestrator("ACTIVE");
                break;
            case 404:
                sendStatusToOrchestrator("INACTIVE");
                break;
            case 409:
                sendStatusToOrchestrator("CONFLICT");
                break;
            default:
                // Handle unexpected response
                sendStatusToOrchestrator("UNKNOWN");
                break;
        }
    }

    private void sendStatusToOrchestrator(String status) {
        logger.info("Status sent to orchestrator: {}", status);
        this.status = status;
    }

    private static void activatePinTerminal(int userInput) {
        String pin, address;

        switch (userInput) {
            case 1:
                pin = "12345";
                address = "AA:BB:CC:DD:EE:FF";
                break;
            case 2:
                pin = "12345";
                address = "AA:BB:CC:DD:EE:AA";
                break;
            case 3:
                pin = "11111";
                address = "AA:BB:CC:DD:EE:FF";
                break;
            default:
                return;
        }

        PinTerminalActivator activator = new PinTerminalActivator(pin, address);
        activator.activatePinTerminal();
    }

    public static void main(String[] args) {
        Scanner scanner = new Scanner(System.in);

        while (true) {
            System.out.println("Enter a number (1, 2, or 3) to activate the PinTerminalActivator, or enter 0 to exit:");

            int userInput = scanner.nextInt();

            if (userInput == 0) {
                System.out.println("Exiting the application. Goodbye!");
                break; // Exit the loop and end the program
            } else if (userInput >= 1 && userInput <= 3) {
                activatePinTerminal(userInput);
            } else {
                System.out.println("Invalid input. Please enter 1, 2, 3, or 0 to exit.");
            }
        }
    }
}