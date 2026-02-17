package org.joolsnet.javafxclient;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.net.Socket;

public class FXMLClient {

    private final String serverHost;
    private final int serverPort;

    public FXMLClient(String serverHost, int serverPort) {
        this.serverHost = serverHost;
        this.serverPort = serverPort;
    }

    /**
     * Fetches FXML content from the server by name
     * @param fxmlName The name of the FXML file to fetch
     * @return The FXML content as a string
     * @throws IOException if connection fails or content cannot be retrieved
     */
    public String fetchFXML(String fxmlName) throws IOException {
        try (Socket socket = new Socket(serverHost, serverPort);
             PrintWriter out = new PrintWriter(socket.getOutputStream(), true);
             BufferedReader in = new BufferedReader(new InputStreamReader(socket.getInputStream()))) {

            // Send request
            out.println("GET_FXML:" + fxmlName);

            // Read response
            StringBuilder response = new StringBuilder();
            String line;
            while ((line = in.readLine()) != null) {
                if (line.equals("END_FXML")) {
                    break;
                }
                response.append(line).append("\n");
            }

            String fxmlContent = response.toString();
            if (fxmlContent.startsWith("ERROR:")) {
                throw new IOException("Server error: " + fxmlContent);
            }

            return fxmlContent;
        }
    }

    /**
     * Fetches the list of available FXML files from the server
     * @return Array of available FXML file names
     * @throws IOException if connection fails
     */
    public String[] listFXMLFiles() throws IOException {
        try (Socket socket = new Socket(serverHost, serverPort);
             PrintWriter out = new PrintWriter(socket.getOutputStream(), true);
             BufferedReader in = new BufferedReader(new InputStreamReader(socket.getInputStream()))) {

            // Send request
            out.println("LIST_FXML");

            // Read response
            String response = in.readLine();
            if (response == null || response.isEmpty()) {
                return new String[0];
            }

            return response.split(",");
        }
    }

    public void close() {
        // Cleanup if needed
    }
}
