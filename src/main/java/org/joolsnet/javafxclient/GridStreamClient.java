package org.joolsnet.javafxclient;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.net.Socket;
import java.net.SocketTimeoutException;

import javafx.collections.FXCollections;
import javafx.collections.ObservableList;

/**
 * Manages the streaming connection to the server for real-time grid updates.
 */
public class GridStreamClient {

    private static final int SOCKET_TIMEOUT_MS = 30000; // 30 seconds
    private static final int HEARTBEAT_INTERVAL_MS = 10000; // 10 seconds
    
    private final String serverHost;
    private final int serverPort;
    private final FlashGridController controller;
    private final Runnable onConnecting;
    private final Runnable onConnected;
    private final Runnable onDisconnected;
    private final java.util.function.Consumer<String> onError;
    
    private Socket socket;
    private PrintWriter out;
    private BufferedReader in;
    private Thread listenerThread;
    private Thread heartbeatThread;
    private volatile boolean running = false;
    private volatile long lastDataReceived = 0;

    public GridStreamClient(String serverHost, int serverPort, FlashGridController controller,
                           Runnable onConnecting, Runnable onConnected, 
                           Runnable onDisconnected, java.util.function.Consumer<String> onError) {
        this.serverHost = serverHost;
        this.serverPort = serverPort;
        this.controller = controller;
        this.onConnecting = onConnecting;
        this.onConnected = onConnected;
        this.onDisconnected = onDisconnected;
        this.onError = onError;
    }

    /**
     * Starts the streaming connection
     */
    public void startStreaming() {
        if (running) {
            System.out.println("Streaming already active");
            return;
        }

        running = true;
        listenerThread = new Thread(this::streamingLoop, "GridStreamListener");
        listenerThread.setDaemon(true);
        listenerThread.start();
        
        // Start heartbeat monitor thread
        heartbeatThread = new Thread(this::heartbeatMonitor, "HeartbeatMonitor");
        heartbeatThread.setDaemon(true);
        heartbeatThread.start();
    }

    /**
     * Main streaming loop that runs in a background thread
     */
    private void streamingLoop() {
        try {
            // Notify connecting
            if (onConnecting != null) {
                onConnecting.run();
            }
            
            // Connect to server
            System.out.println("Connecting to grid stream server...");
            socket = new Socket(serverHost, serverPort);
            socket.setSoTimeout(SOCKET_TIMEOUT_MS); // Set socket timeout
            out = new PrintWriter(socket.getOutputStream(), true);
            in = new BufferedReader(new InputStreamReader(socket.getInputStream()));
            
            lastDataReceived = System.currentTimeMillis();

            // Request streaming
            out.println("STREAM_GRID");
            System.out.println("Requested grid stream");

            // Load initial data
            ObservableList<GridDataRow> initialData = loadInitialData();
            controller.loadInitialData(initialData);
            controller.updateStatusLabel("Streaming...");
            
            // Notify connected
            if (onConnected != null) {
                onConnected.run();
            }

            // Listen for updates
            String line;
            while (running && (line = in.readLine()) != null) {
                lastDataReceived = System.currentTimeMillis();
                
                if (line.equals("STREAM_STARTED")) {
                    System.out.println("Stream started confirmation received");
                    continue;
                }
                
                if (line.equals("HEARTBEAT")) {
                    // Server heartbeat received
                    continue;
                }

                if (line.startsWith("UPDATE|")) {
                    processUpdate(line);
                }
            }

        } catch (SocketTimeoutException e) {
            if (running) {
                System.err.println("Socket timeout - no data received");
                if (onError != null) {
                    onError.accept("Connection timeout");
                }
            }
        } catch (IOException e) {
            if (running) {
                System.err.println("Streaming error: " + e.getMessage());
                controller.onError(e.getMessage());
                if (onError != null) {
                    onError.accept(e.getMessage());
                }
            }
        } finally {
            cleanup();
            if (running) {
                controller.onStreamingStopped();
                if (onDisconnected != null) {
                    onDisconnected.run();
                }
            }
        }
    }
    
    /**
     * Monitors for heartbeat/data timeout
     */
    private void heartbeatMonitor() {
        while (running) {
            try {
                Thread.sleep(HEARTBEAT_INTERVAL_MS);
                
                // Check if we've received data recently
                long timeSinceLastData = System.currentTimeMillis() - lastDataReceived;
                if (timeSinceLastData > SOCKET_TIMEOUT_MS) {
                    System.err.println("Heartbeat timeout - no data received for " + timeSinceLastData + "ms");
                    stopStreaming();
                    break;
                }
            } catch (InterruptedException e) {
                break;
            }
        }
    }

    /**
     * Loads the initial grid data
     */
    private ObservableList<GridDataRow> loadInitialData() throws IOException {
        ObservableList<GridDataRow> data = FXCollections.observableArrayList();
        
        String line;
        boolean readingData = false;
        
        while ((line = in.readLine()) != null) {
            if (line.startsWith("ROW_COUNT:")) {
                String countStr = line.substring("ROW_COUNT:".length());
                int rowCount = Integer.parseInt(countStr);
                System.out.println("Expecting " + rowCount + " rows");
                readingData = true;
                continue;
            }
            
            if (line.equals("END_GRID_DATA")) {
                System.out.println("Finished loading initial data: " + data.size() + " rows");
                break;
            }
            
            if (readingData && !line.startsWith("ERROR:")) {
                GridDataRow row = parseRow(line);
                if (row != null) {
                    data.add(row);
                }
            }
        }
        
        return data;
    }

    /**
     * Parses a row from the server's pipe-delimited format
     * Optimized to use indexOf() instead of split() to reduce allocations
     */
    private GridDataRow parseRow(String line) {
        try {
            // Find delimiter positions
            int idx1 = line.indexOf('|');
            if (idx1 == -1) return null;
            int idx2 = line.indexOf('|', idx1 + 1);
            if (idx2 == -1) return null;
            int idx3 = line.indexOf('|', idx2 + 1);
            if (idx3 == -1) return null;
            int idx4 = line.indexOf('|', idx3 + 1);
            if (idx4 == -1) return null;
            int idx5 = line.indexOf('|', idx4 + 1);
            if (idx5 == -1) return null;
            int idx6 = line.indexOf('|', idx5 + 1);
            if (idx6 == -1) return null;
            int idx7 = line.indexOf('|', idx6 + 1);
            if (idx7 == -1) return null;
            int idx8 = line.indexOf('|', idx7 + 1);
            if (idx8 == -1) return null;
            int idx9 = line.indexOf('|', idx8 + 1);
            if (idx9 == -1) return null;
            int idx10 = line.indexOf('|', idx9 + 1);
            if (idx10 == -1) return null;
            int idx11 = line.indexOf('|', idx10 + 1);
            if (idx11 == -1) return null;
            
            // Extract fields using substring
            int id = Integer.parseInt(line.substring(0, idx1));
            String name = line.substring(idx1 + 1, idx2);
            double price = Double.parseDouble(line.substring(idx2 + 1, idx3));
            int quantity = Integer.parseInt(line.substring(idx3 + 1, idx4));
            String status = line.substring(idx4 + 1, idx5);
            String createdTimestamp = line.substring(idx5 + 1, idx6);
            String modifiedTimestamp = line.substring(idx6 + 1, idx7);
            double value = Double.parseDouble(line.substring(idx7 + 1, idx8));
            double bidPrice = Double.parseDouble(line.substring(idx8 + 1, idx9));
            int bidSize = Integer.parseInt(line.substring(idx9 + 1, idx10));
            double askPrice = Double.parseDouble(line.substring(idx10 + 1, idx11));
            int askSize = Integer.parseInt(line.substring(idx11 + 1));
            
            return new GridDataRow(id, name, price, quantity, status, 
                                   createdTimestamp, modifiedTimestamp, value,
                                   bidPrice, bidSize, askPrice, askSize);
        } catch (Exception e) {
            System.err.println("Error parsing row: " + line + " - " + e.getMessage());
        }
        return null;
    }

    /**
     * Processes an update message from the server
     * Format: UPDATE|rowId|columnName|newValue
     * Optimized to use indexOf() instead of split() to reduce allocations
     */
    private void processUpdate(String updateLine) {
        try {
            // Find delimiter positions
            int idx1 = updateLine.indexOf('|'); // After "UPDATE"
            if (idx1 == -1) return;
            int idx2 = updateLine.indexOf('|', idx1 + 1); // After rowId
            if (idx2 == -1) return;
            int idx3 = updateLine.indexOf('|', idx2 + 1); // After columnName
            if (idx3 == -1) return;
            
            // Extract fields using substring
            int rowId = Integer.parseInt(updateLine.substring(idx1 + 1, idx2));
            String columnName = updateLine.substring(idx2 + 1, idx3);
            String newValue = updateLine.substring(idx3 + 1).replace("\\\\|", "|").replace("\\\\n", "\n");
            
            controller.processCellUpdate(rowId, columnName, newValue);
        } catch (Exception e) {
            System.err.println("Error processing update: " + updateLine + " - " + e.getMessage());
        }
    }

    /**
     * Stops the streaming connection
     */
    public void stopStreaming() {
        running = false;
        
        if (out != null) {
            try {
                out.println("STOP_STREAM");
                out.flush();
            } catch (Exception e) {
                // Ignore
            }
        }
        
        cleanup();
        
        if (listenerThread != null && listenerThread.isAlive()) {
            try {
                listenerThread.join(2000);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }
        
        if (heartbeatThread != null && heartbeatThread.isAlive()) {
            try {
                heartbeatThread.interrupt();
                heartbeatThread.join(2000);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }
    }

    /**
     * Cleans up resources
     */
    private void cleanup() {
        try {
            if (in != null) {
                in.close();
            }
        } catch (IOException e) {
            // Ignore
        }
        
        try {
            if (out != null) {
                out.close();
            }
        } catch (Exception e) {
            // Ignore
        }
        
        try {
            if (socket != null && !socket.isClosed()) {
                socket.close();
            }
        } catch (IOException e) {
            // Ignore
        }
    }

    /**
     * Checks if streaming is active
     */
    public boolean isStreaming() {
        return running;
    }
}
