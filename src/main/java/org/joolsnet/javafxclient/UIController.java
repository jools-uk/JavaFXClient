package org.joolsnet.javafxclient;

import java.io.ByteArrayInputStream;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

import javafx.application.Platform;
import javafx.fxml.FXMLLoader;
import javafx.geometry.Insets;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.Tab;
import javafx.scene.control.TabPane;
import javafx.scene.control.TextArea;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.stage.Stage;

public class UIController {

    private final FXMLClient client;
    private TabPane contentTabPane;
    private TextArea logArea;
    private ComboBox<String> fxmlSelector;
    private final Map<String, GridStreamClient> gridStreamClients = new HashMap<>();
    private StatusLED serverConnectionLED;
    private StatusLED gridStreamLED;
    private Stage performanceStage;
    private PerformanceController performanceController;

    public UIController(FXMLClient client) {
        this.client = client;
    }

    public BorderPane createUI() {
        BorderPane root = new BorderPane();

        // Bottom: Log area
        logArea = new TextArea();
        logArea.setEditable(false);
        logArea.setPrefHeight(100);
        
        // Create a container for status bar and log area
        BorderPane bottomPane = new BorderPane();
        bottomPane.setTop(createStatusBar());
        bottomPane.setCenter(logArea);
        root.setBottom(bottomPane);

        // Top: Control panel
        HBox controlPanel = createControlPanel();
        root.setTop(controlPanel);

        // Center: TabPane for displaying loaded FXML files
        contentTabPane = new TabPane();
        contentTabPane.setTabClosingPolicy(TabPane.TabClosingPolicy.ALL_TABS);
        
        // Listen for tab changes to manage grid streams
        contentTabPane.getSelectionModel().selectedItemProperty().addListener((obs, oldTab, newTab) -> {
            handleTabChange(oldTab, newTab);
        });
        
        root.setCenter(contentTabPane);

        return root;
    }
    
    private HBox createStatusBar() {
        HBox statusBar = new HBox(15);
        statusBar.setPadding(new Insets(5, 10, 5, 10));
        statusBar.setStyle("-fx-background-color: #f0f0f0; -fx-border-color: #cccccc; -fx-border-width: 1 0 0 0;");
        
        // Create server connection LED
        serverConnectionLED = new StatusLED("FXML Server", StatusLED.Status.GREY);
        
        // Create grid stream connection LED (initially hidden)
        gridStreamLED = new StatusLED("Grid Stream", StatusLED.Status.GREY);
        gridStreamLED.setVisible(false);
        gridStreamLED.setManaged(false);
        
        // Spacer to push items to the left
        HBox spacer = new HBox();
        HBox.setHgrow(spacer, Priority.ALWAYS);
        
        statusBar.getChildren().addAll(serverConnectionLED, gridStreamLED, spacer);
        
        return statusBar;
    }

    private HBox createControlPanel() {
        HBox panel = new HBox(10);
        panel.setPadding(new Insets(10));

        Label label = new Label("Select FXML:");
        
        fxmlSelector = new ComboBox<>();
        fxmlSelector.setPrefWidth(200);

        Button refreshButton = new Button("Refresh List");
        refreshButton.setOnAction(e -> refreshFXMLList());

        Button loadButton = new Button("Load FXML");
        loadButton.setOnAction(e -> loadSelectedFXML());
        
        // Add spacer
        HBox spacer = new HBox();
        HBox.setHgrow(spacer, Priority.ALWAYS);
        
        // Add performance button
        Button perfButton = new Button("Performance");
        perfButton.setOnAction(e -> openPerformanceWindow());

        panel.getChildren().addAll(label, fxmlSelector, refreshButton, loadButton, spacer, perfButton);

        // Load initial list
        refreshFXMLList();

        return panel;
    }

    private void refreshFXMLList() {
        // Set to amber while connecting
        updateServerStatus(StatusLED.Status.AMBER);
        
        try {
            String[] files = client.listFXMLFiles();
            fxmlSelector.getItems().clear();
            fxmlSelector.getItems().addAll(files);
            if (files.length > 0) {
                fxmlSelector.getSelectionModel().selectFirst();
            }
            log("Loaded " + files.length + " FXML files from server");
            
            // Set to green on success
            updateServerStatus(StatusLED.Status.GREEN);
        } catch (Exception e) {
            log("Error loading FXML list: " + e.getMessage());
            showError("Connection Error", "Failed to connect to server: " + e.getMessage());
            
            // Set to red on failure
            updateServerStatus(StatusLED.Status.RED);
        }
    }

    private void loadSelectedFXML() {
        String selected = fxmlSelector.getSelectionModel().getSelectedItem();
        if (selected == null) {
            log("No FXML file selected");
            return;
        }
        
        // Check if tab already exists
        Optional<Tab> existingTab = contentTabPane.getTabs().stream()
            .filter(tab -> selected.equals(tab.getUserData()))
            .findFirst();
        
        if (existingTab.isPresent()) {
            // Tab exists, just switch to it
            contentTabPane.getSelectionModel().select(existingTab.get());
            log("Switched to existing tab: " + selected);
            return;
        }

        // Set to amber while connecting
        updateServerStatus(StatusLED.Status.AMBER);
        
        try {
            log("Fetching FXML: " + selected);
            String fxmlContent = client.fetchFXML(selected);
            log("Received FXML content (" + fxmlContent.length() + " characters)");

            // Load the FXML content
            FXMLLoader loader = new FXMLLoader();
            ByteArrayInputStream inputStream = new ByteArrayInputStream(fxmlContent.getBytes());
            Parent loadedContent = loader.load(inputStream);
            
            // Create a wrapper with padding
            BorderPane wrapper = new BorderPane();
            wrapper.setPadding(new Insets(10));
            wrapper.setCenter(loadedContent);

            // Create new tab
            Tab newTab = new Tab(selected);
            newTab.setContent(wrapper);
            newTab.setUserData(selected); // Store filename for lookup
            
            // Handle tab close - stop grid stream if active
            newTab.setOnClosed(e -> {
                stopGridStreamForTab(selected);
                log("Closed tab: " + selected);
            });
            
            contentTabPane.getTabs().add(newTab);
            contentTabPane.getSelectionModel().select(newTab);
            
            log("Successfully loaded and displayed: " + selected);
            
            // Set to green on success
            updateServerStatus(StatusLED.Status.GREEN);

            // Check if this is the flash grid FXML
            if ("flashgrid.fxml".equalsIgnoreCase(selected)) {
                startGridStream(selected, loader);
            }

        } catch (Exception e) {
            log("Error loading FXML: " + e.getMessage());
            showError("Load Error", "Failed to load FXML: " + e.getMessage());
            e.printStackTrace();
            
            // Set to red on failure
            updateServerStatus(StatusLED.Status.RED);
        }
    }

    /**
     * Handles tab switching to manage grid streams
     */
    private void handleTabChange(Tab oldTab, Tab newTab) {
        // Stop grid stream for old tab
        if (oldTab != null && oldTab.getUserData() != null) {
            String oldFileName = (String) oldTab.getUserData();
            if ("flashgrid.fxml".equalsIgnoreCase(oldFileName)) {
                stopGridStreamForTab(oldFileName);
            }
        }
        
        // Start grid stream for new tab if it's flashgrid
        if (newTab != null && newTab.getUserData() != null) {
            String newFileName = (String) newTab.getUserData();
            if ("flashgrid.fxml".equalsIgnoreCase(newFileName)) {
                // Check if we already have a stream client for this tab
                GridStreamClient streamClient = gridStreamClients.get(newFileName);
                if (streamClient != null && !streamClient.isStreaming()) {
                    // Restart the stream
                    log("Restarting grid stream for: " + newFileName);
                    streamClient.startStreaming();
                    
                    // Show grid stream LED
                    Platform.runLater(() -> {
                        gridStreamLED.setVisible(true);
                        gridStreamLED.setManaged(true);
                    });
                }
            }
        }
    }
    
    /**
     * Starts the grid streaming when flashgrid.fxml is loaded
     */
    private void startGridStream(String fileName, FXMLLoader loader) {
        try {
            // Get the controller from the loaded FXML
            FlashGridController controller = loader.getController();
            
            if (controller != null) {
                log("Starting grid stream...");
                
                // Show and configure grid stream LED
                Platform.runLater(() -> {
                    gridStreamLED.setVisible(true);
                    gridStreamLED.setManaged(true);
                });
                
                // Create and start the stream client with connection callbacks
                GridStreamClient gridStreamClient = new GridStreamClient("localhost", 8080, controller,
                    this::onGridStreamConnecting,
                    this::onGridStreamConnected,
                    this::onGridStreamDisconnected,
                    this::onGridStreamError);
                
                // Store the client for this tab
                gridStreamClients.put(fileName, gridStreamClient);
                
                gridStreamClient.startStreaming();
                
                log("Grid stream started - receiving real-time updates");
            } else {
                log("Warning: FlashGridController not found in loaded FXML");
            }
        } catch (Exception e) {
            log("Error starting grid stream: " + e.getMessage());
            e.printStackTrace();
        }
    }

    /**
     * Stops the grid stream for a specific tab
     */
    private void stopGridStreamForTab(String fileName) {
        GridStreamClient streamClient = gridStreamClients.get(fileName);
        if (streamClient != null && streamClient.isStreaming()) {
            log("Stopping grid stream for: " + fileName);
            streamClient.stopStreaming();
        }
        
        // Hide grid stream LED
        Platform.runLater(() -> {
            gridStreamLED.setVisible(false);
            gridStreamLED.setManaged(false);
        });
    }
    
    /**
     * Stops all active grid streams
     */
    private void stopAllGridStreams() {
        for (GridStreamClient streamClient : gridStreamClients.values()) {
            if (streamClient.isStreaming()) {
                streamClient.stopStreaming();
            }
        }
        gridStreamClients.clear();
        log("All grid streams stopped");
        
        // Hide grid stream LED
        Platform.runLater(() -> {
            gridStreamLED.setVisible(false);
            gridStreamLED.setManaged(false);
        });
    }

    /**
     * Cleanup method to be called on shutdown
     */
    public void cleanup() {
        stopAllGridStreams();
        closePerformanceWindow();
    }
    
    private void openPerformanceWindow() {
        if (performanceStage != null && performanceStage.isShowing()) {
            performanceStage.toFront();
            return;
        }
        
        try {
            FXMLLoader loader = new FXMLLoader(getClass().getResource("/performance.fxml"));
            Parent root = loader.load();
            performanceController = loader.getController();
            
            performanceStage = new Stage();
            performanceStage.setTitle("Performance Metrics");
            performanceStage.setScene(new Scene(root, 550, 850));
            performanceStage.setOnCloseRequest(e -> {
                if (performanceController != null) {
                    performanceController.cleanup();
                }
                performanceStage = null;
                performanceController = null;
            });
            performanceStage.show();
            
            log("Performance metrics window opened");
        } catch (Exception e) {
            log("Error opening performance window: " + e.getMessage());
            e.printStackTrace();
        }
    }
    
    private void closePerformanceWindow() {
        if (performanceStage != null) {
            if (performanceController != null) {
                performanceController.cleanup();
            }
            performanceStage.close();
            performanceStage = null;
            performanceController = null;
        }
    }

    private void log(String message) {
        logArea.appendText(message + "\n");
    }
    
    private void updateServerStatus(StatusLED.Status status) {
        Platform.runLater(() -> {
            if (serverConnectionLED != null) {
                serverConnectionLED.setStatus(status);
            }
        });
    }
    
    // Grid stream connection callbacks
    private void onGridStreamConnecting() {
        Platform.runLater(() -> {
            if (gridStreamLED != null) {
                gridStreamLED.setStatus(StatusLED.Status.AMBER);
            }
        });
        log("Grid stream: Connecting...");
    }
    
    private void onGridStreamConnected() {
        Platform.runLater(() -> {
            if (gridStreamLED != null) {
                gridStreamLED.setStatus(StatusLED.Status.GREEN);
            }
        });
        log("Grid stream: Connected");
    }
    
    private void onGridStreamDisconnected() {
        Platform.runLater(() -> {
            if (gridStreamLED != null) {
                gridStreamLED.setStatus(StatusLED.Status.RED);
            }
        });
        log("Grid stream: Disconnected");
    }
    
    private void onGridStreamError(String error) {
        Platform.runLater(() -> {
            if (gridStreamLED != null) {
                gridStreamLED.setStatus(StatusLED.Status.RED);
            }
        });
        log("Grid stream error: " + error);
    }

    private void showError(String title, String message) {
        Alert alert = new Alert(Alert.AlertType.ERROR);
        alert.setTitle(title);
        alert.setHeaderText(null);
        alert.setContentText(message);
        alert.showAndWait();
    }
}
