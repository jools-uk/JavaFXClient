package org.joolsnet.javafxclient;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import javafx.animation.KeyFrame;
import javafx.animation.Timeline;
import javafx.application.Platform;
import javafx.beans.value.ChangeListener;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.fxml.FXML;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.TableCell;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.control.cell.PropertyValueFactory;
import javafx.util.Duration;

/**
 * Controller for the Flash Grid FXML screen.
 * Manages the TableView and processes real-time updates from the server.
 */
public class FlashGridController {

    /**
     * Flash animation modes
     */
    public enum FlashMode {
        FULL,    // Full fade animation
        SIMPLE,  // Quick flash without fade
        OFF      // No flash animation
    }

    @FXML
    private TableView<GridDataRow> dataGrid;

    @FXML
    private TableColumn<GridDataRow, Integer> idColumn;

    @FXML
    private TableColumn<GridDataRow, String> nameColumn;

    @FXML
    private TableColumn<GridDataRow, Double> priceColumn;

    @FXML
    private TableColumn<GridDataRow, Integer> quantityColumn;

    @FXML
    private TableColumn<GridDataRow, String> statusColumn;

    @FXML
    private TableColumn<GridDataRow, String> createdTimestampColumn;

    @FXML
    private TableColumn<GridDataRow, String> modifiedTimestampColumn;

    @FXML
    private TableColumn<GridDataRow, Double> valueColumn;

    @FXML
    private TableColumn<GridDataRow, Double> bidPriceColumn;

    @FXML
    private TableColumn<GridDataRow, Integer> bidSizeColumn;

    @FXML
    private TableColumn<GridDataRow, Double> askPriceColumn;

    @FXML
    private TableColumn<GridDataRow, Integer> askSizeColumn;

    @FXML
    private Label statusLabel;

    @FXML
    private Label updateCountLabel;

    @FXML
    private Label rowCountLabel;

    @FXML
    private Button refreshButton;

    @FXML
    private Button clearButton;

    private ObservableList<GridDataRow> data;
    private int updateCount = 0;
    
    // Flash animation mode
    private FlashMode flashMode = FlashMode.OFF;
    
    // Double-buffer update coalescing system
    private final AtomicReference<ConcurrentHashMap<String, CellUpdate>> updateBuffer = 
        new AtomicReference<>(new ConcurrentHashMap<>());
    private final AtomicBoolean processingScheduled = new AtomicBoolean(false);
    private final AtomicInteger totalUpdatesReceived = new AtomicInteger(0);
    private final AtomicInteger coalescedUpdates = new AtomicInteger(0);
    private final AtomicBoolean currentBatchHadCoalescing = new AtomicBoolean(false);
    
    // Performance monitoring fields
    private long lastReportTime = System.currentTimeMillis();
    private int updatesInWindow = 0;
    private long slowUpdateCount = 0;
    private final AtomicInteger batchesProcessed = new AtomicInteger(0);
    private final AtomicInteger batchesWithCoalescing = new AtomicInteger(0);
    
    /**
     * Represents a pending cell update
     */
    private static class CellUpdate {
        final int rowId;
        final String columnName;
        final String newValue;
        
        CellUpdate(int rowId, String columnName, String newValue) {
            this.rowId = rowId;
            this.columnName = columnName;
            this.newValue = newValue;
        }
    }

    @FXML
    public void initialize() {
        // Initialize the observable list
        data = FXCollections.observableArrayList();
        dataGrid.setItems(data);

        // Bind columns to properties
        idColumn.setCellValueFactory(new PropertyValueFactory<>("id"));
        nameColumn.setCellValueFactory(new PropertyValueFactory<>("name"));
        priceColumn.setCellValueFactory(new PropertyValueFactory<>("price"));
        quantityColumn.setCellValueFactory(new PropertyValueFactory<>("quantity"));
        statusColumn.setCellValueFactory(new PropertyValueFactory<>("status"));
        createdTimestampColumn.setCellValueFactory(new PropertyValueFactory<>("createdTimestamp"));
        modifiedTimestampColumn.setCellValueFactory(new PropertyValueFactory<>("modifiedTimestamp"));
        valueColumn.setCellValueFactory(new PropertyValueFactory<>("value"));
        bidPriceColumn.setCellValueFactory(new PropertyValueFactory<>("bidPrice"));
        bidSizeColumn.setCellValueFactory(new PropertyValueFactory<>("bidSize"));
        askPriceColumn.setCellValueFactory(new PropertyValueFactory<>("askPrice"));
        askSizeColumn.setCellValueFactory(new PropertyValueFactory<>("askSize"));

        // Format double columns with flash animation
        priceColumn.setCellFactory(column -> new FlashingDoubleCell("$%.2f"));
        valueColumn.setCellFactory(column -> new FlashingDoubleCell("$%.2f"));
        bidPriceColumn.setCellFactory(column -> new FlashingDoubleCell("$%.2f"));
        askPriceColumn.setCellFactory(column -> new FlashingDoubleCell("$%.2f"));
        
        // Add flash animation to integer columns
        quantityColumn.setCellFactory(column -> new FlashingIntegerCell());
        idColumn.setCellFactory(column -> new FlashingIntegerCell());
        bidSizeColumn.setCellFactory(column -> new FlashingIntegerCell());
        askSizeColumn.setCellFactory(column -> new FlashingIntegerCell());
        
        // Add flash animation to string columns
        nameColumn.setCellFactory(column -> new FlashingStringCell());
        statusColumn.setCellFactory(column -> new FlashingStringCell());
        createdTimestampColumn.setCellFactory(column -> new FlashingStringCell());
        modifiedTimestampColumn.setCellFactory(column -> new FlashingStringCell());

        // Setup button actions
        if (refreshButton != null) {
            refreshButton.setOnAction(e -> handleRefresh());
        }

        if (clearButton != null) {
            clearButton.setOnAction(e -> handleClearSelection());
        }

        // Initial status
        updateStatusLabel("Initializing...");
    }
    
    /**
     * Sets the flash animation mode
     */
    public void setFlashMode(FlashMode mode) {
        this.flashMode = mode;
        System.out.println("Flash mode set to: " + mode);
    }
    
    /**
     * Gets the current flash animation mode
     */
    public FlashMode getFlashMode() {
        return flashMode;
    }

    /**
     * Loads initial data from the server
     */
    public void loadInitialData(ObservableList<GridDataRow> initialData) {
        Platform.runLater(() -> {
            data.clear();
            data.addAll(initialData);
            updateRowCount();
            updateStatusLabel("Connected");
            System.out.println("Loaded " + data.size() + " rows into grid");
        });
    }

    /**
     * Processes a cell update from the server.
     * Updates are added to a buffer and coalesced (newer updates replace older ones for the same row/column).
     * Processing is triggered asynchronously via Platform.runLater.
     */
    public void processCellUpdate(int rowId, String columnName, String newValue) {
        totalUpdatesReceived.incrementAndGet();
        
        // Create unique key for this cell
        String key = rowId + ":" + columnName;
        
        // Add to buffer (automatically coalesces if same key exists)
        ConcurrentHashMap<String, CellUpdate> buffer = updateBuffer.get();
        CellUpdate oldUpdate = buffer.put(key, new CellUpdate(rowId, columnName, newValue));
        
        // Track if we coalesced an update
        if (oldUpdate != null) {
            coalescedUpdates.incrementAndGet();
            currentBatchHadCoalescing.set(true);
        }
        
        // Update pending count for monitoring
        int pending = buffer.size();
        if (pending > 100) {
            System.out.println("WARNING: " + pending + " coalesced updates in buffer!");
        }
        
        // Schedule processing if not already scheduled
        if (processingScheduled.compareAndSet(false, true)) {
            Platform.runLater(this::processPendingUpdates);
        }
    }
    
    /**
     * Processes all pending updates from the buffer.
     * This runs on the JavaFX Application Thread.
     */
    private void processPendingUpdates() {
        try {
            long startTime = System.nanoTime();
            
            // Check if current batch had coalescing before swapping
            boolean batchHadCoalescing = currentBatchHadCoalescing.getAndSet(false);
            
            // Atomically swap buffer with a fresh one
            ConcurrentHashMap<String, CellUpdate> toProcess = updateBuffer.getAndSet(new ConcurrentHashMap<>());
            
            // Allow new processing to be scheduled
            processingScheduled.set(false);
            
            // If buffer is empty, we're done
            if (toProcess.isEmpty()) {
                return;
            }
            
            // Track batch metrics
            batchesProcessed.incrementAndGet();
            if (batchHadCoalescing) {
                batchesWithCoalescing.incrementAndGet();
            }
            
            int batchSize = toProcess.size();
            int processed = 0;
            
            // Process all updates in the batch
            for (Map.Entry<String, CellUpdate> entry : toProcess.entrySet()) {
                CellUpdate update = entry.getValue();
                
                // Find and update the row
                if (update.rowId >= 0 && update.rowId < data.size()) {
                    GridDataRow row = data.get(update.rowId);
                    row.updateField(update.columnName, update.newValue);
                    processed++;
                    
                    // Increment update counter
                    updateCount++;
                    updatesInWindow++;
                }
            }
            
            // Update UI
            if (updateCountLabel != null) {
                updateCountLabel.setText("Updates: " + updateCount);
            }
            
            // Track processing time
            long elapsed = System.nanoTime() - startTime;
            if (elapsed > 5_000_000) { // > 5ms (batch threshold)
                slowUpdateCount++;
                System.out.println("Slow batch #" + slowUpdateCount + ": " + (elapsed/1_000_000.0) + "ms for " + batchSize + " updates");
            }
            
            // Report throughput every second
            long now = System.currentTimeMillis();
            if (now - lastReportTime >= 1000) {
                int total = totalUpdatesReceived.getAndSet(0);
                int coalesced = coalescedUpdates.getAndSet(0);
                int batches = batchesProcessed.getAndSet(0);
                int batchesCoalesced = batchesWithCoalescing.getAndSet(0);
                int remaining = updateBuffer.get().size();
                double coalescingRate = total > 0 ? (coalesced * 100.0 / total) : 0;
                double batchCoalescingRate = batches > 0 ? (batchesCoalesced * 100.0 / batches) : 0;
                
                System.out.println(String.format("[PERF] Updates/sec: %d | Processed: %d | Coalesced: %d (%.1f%%) | Batches: %d (%d with coalescing, %.1f%%) | Buffered: %d | Slow batches: %d",
                    total, updatesInWindow, coalesced, coalescingRate, batches, batchesCoalesced, batchCoalescingRate, remaining, slowUpdateCount));
                updatesInWindow = 0;
                lastReportTime = now;
            }
            
            // If more updates arrived while we were processing, schedule another batch
            if (!updateBuffer.get().isEmpty() && processingScheduled.compareAndSet(false, true)) {
                Platform.runLater(this::processPendingUpdates);
            }
            
        } catch (Exception e) {
            System.err.println("Error processing batch updates: " + e.getMessage());
            processingScheduled.set(false); // Reset on error
        }
    }

    /**
     * Updates the status label
     */
    public void updateStatusLabel(String status) {
        Platform.runLater(() -> {
            if (statusLabel != null) {
                statusLabel.setText("Status: " + status);
            }
        });
    }

    /**
     * Updates the row count label
     */
    private void updateRowCount() {
        if (rowCountLabel != null) {
            rowCountLabel.setText(String.valueOf(data.size()));
        }
    }

    /**
     * Handle refresh button click
     */
    private void handleRefresh() {
        System.out.println("Refresh requested");
        // Could trigger a full data reload here if needed
    }

    /**
     * Handle clear selection button click
     */
    private void handleClearSelection() {
        dataGrid.getSelectionModel().clearSelection();
    }

    /**
     * Called when streaming stops
     */
    public void onStreamingStopped() {
        updateStatusLabel("Disconnected");
    }

    /**
     * Called when an error occurs
     */
    public void onError(String error) {
        updateStatusLabel("Error: " + error);
    }

    /**
     * Custom TableCell for Double values that flashes green/red on value changes
     */
    class FlashingDoubleCell extends TableCell<GridDataRow, Double> {
        private final String format;
        private GridDataRow currentRow = null;
        private ChangeListener<Number> priceListener = null;
        private ChangeListener<Number> valueListener = null;
        private ChangeListener<Number> bidPriceListener = null;
        private ChangeListener<Number> askPriceListener = null;
        private Timeline flashTimeline = null;

        public FlashingDoubleCell(String format) {
            this.format = format;
        }

        @Override
        protected void updateItem(Double item, boolean empty) {
            super.updateItem(item, empty);
            
            // Get the row from the table
            GridDataRow row = empty ? null : getTableRow().getItem();
            
            // Remove old listeners if row changed
            if (currentRow != row) {
                removeListeners();
                // Stop any ongoing flash animation when reusing the cell
                stopFlashAnimation();
            }
            
            if (empty || item == null || row == null) {
                setText(null);
                setStyle("");
                currentRow = null;
                // Stop flash animation when cell is emptied
                stopFlashAnimation();
            } else {
                setText(String.format(format, item));
                
                // Set up listener for this row if it's new
                if (currentRow != row) {
                    currentRow = row;
                    setupPropertyListener();
                }
            }
        }
        
        private void removeListeners() {
            if (currentRow != null) {
                if (priceListener != null) {
                    currentRow.priceProperty().removeListener(priceListener);
                    priceListener = null;
                }
                if (valueListener != null) {
                    currentRow.valueProperty().removeListener(valueListener);
                    valueListener = null;
                }
                if (bidPriceListener != null) {
                    currentRow.bidPriceProperty().removeListener(bidPriceListener);
                    bidPriceListener = null;
                }
                if (askPriceListener != null) {
                    currentRow.askPriceProperty().removeListener(askPriceListener);
                    askPriceListener = null;
                }
            }
        }
        
        private void stopFlashAnimation() {
            if (flashTimeline != null) {
                flashTimeline.stop();
                flashTimeline = null;
            }
            // Reset style to default
            setStyle("");
        }
        
        private void setupPropertyListener() {
            if (currentRow == null) return;
            
            // Determine which property to listen to based on the column
            TableColumn<GridDataRow, Double> column = getTableColumn();
            if (column == null) return;
            
            // Listen to the appropriate property
            if (column == priceColumn) {
                priceListener = (obs, oldVal, newVal) -> {
                    // Verify this cell still displays this row
                    if (getTableRow() != null && getTableRow().getItem() == currentRow && oldVal != null && newVal != null && !oldVal.equals(newVal)) {
                        double delta = newVal.doubleValue() - oldVal.doubleValue();
                        setText(String.format(format, newVal));
                        flashCell(delta > 0);
                    }
                };
                currentRow.priceProperty().addListener(priceListener);
            } else if (column == valueColumn) {
                valueListener = (obs, oldVal, newVal) -> {
                    // Verify this cell still displays this row
                    if (getTableRow() != null && getTableRow().getItem() == currentRow && oldVal != null && newVal != null && !oldVal.equals(newVal)) {
                        double delta = newVal.doubleValue() - oldVal.doubleValue();
                        setText(String.format(format, newVal));
                        flashCell(delta > 0);
                    }
                };
                currentRow.valueProperty().addListener(valueListener);
            } else if (column == bidPriceColumn) {
                bidPriceListener = (obs, oldVal, newVal) -> {
                    // Verify this cell still displays this row
                    if (getTableRow() != null && getTableRow().getItem() == currentRow && oldVal != null && newVal != null && !oldVal.equals(newVal)) {
                        double delta = newVal.doubleValue() - oldVal.doubleValue();
                        setText(String.format(format, newVal));
                        flashCell(delta > 0);
                    }
                };
                currentRow.bidPriceProperty().addListener(bidPriceListener);
            } else if (column == askPriceColumn) {
                askPriceListener = (obs, oldVal, newVal) -> {
                    // Verify this cell still displays this row
                    if (getTableRow() != null && getTableRow().getItem() == currentRow && oldVal != null && newVal != null && !oldVal.equals(newVal)) {
                        double delta = newVal.doubleValue() - oldVal.doubleValue();
                        setText(String.format(format, newVal));
                        flashCell(delta > 0);
                    }
                };
                currentRow.askPriceProperty().addListener(askPriceListener);
            }
        }

        private void flashCell(boolean positive) {
            // Check flash mode
            if (flashMode == FlashMode.OFF) {
                return;
            }
            
            // Cancel any existing flash animation
            if (flashTimeline != null) {
                flashTimeline.stop();
            }
            
            // Determine base color (green for positive, red for negative)
            String baseColor = positive ? "0, 200, 0" : "200, 0, 0";
            
            if (flashMode == FlashMode.SIMPLE) {
                // Simple mode - just set color briefly without fade
                setStyle("-fx-background-color: rgba(" + baseColor + ", 1.0);");
                flashTimeline = new Timeline(
                    new KeyFrame(Duration.millis(150), e -> setStyle(""))
                );
                flashTimeline.play();
            } else {
                // Full mode - create smooth fade animation
                flashTimeline = new Timeline(
                    new KeyFrame(Duration.millis(0), e -> setStyle("-fx-background-color: rgba(" + baseColor + ", 1.0);")),
                    new KeyFrame(Duration.millis(100), e -> setStyle("-fx-background-color: rgba(" + baseColor + ", 0.8);")),
                    new KeyFrame(Duration.millis(200), e -> setStyle("-fx-background-color: rgba(" + baseColor + ", 0.6);")),
                    new KeyFrame(Duration.millis(300), e -> setStyle("-fx-background-color: rgba(" + baseColor + ", 0.4);")),
                    new KeyFrame(Duration.millis(400), e -> setStyle("-fx-background-color: rgba(" + baseColor + ", 0.2);")),
                    new KeyFrame(Duration.millis(500), e -> setStyle(""))
                );
                flashTimeline.play();
            }
        }
    }

    /**
     * Custom TableCell for Integer values that flashes green/red on value changes
     */
    class FlashingIntegerCell extends TableCell<GridDataRow, Integer> {
        private GridDataRow currentRow = null;
        private ChangeListener<Number> idListener = null;
        private ChangeListener<Number> quantityListener = null;
        private ChangeListener<Number> bidSizeListener = null;
        private ChangeListener<Number> askSizeListener = null;
        private Timeline flashTimeline = null;

        public FlashingIntegerCell() {
        }

        @Override
        protected void updateItem(Integer item, boolean empty) {
            super.updateItem(item, empty);
            
            // Get the row from the table
            GridDataRow row = empty ? null : getTableRow().getItem();
            
            // Remove old listeners if row changed
            if (currentRow != row) {
                removeListeners();
                // Stop any ongoing flash animation when reusing the cell
                stopFlashAnimation();
            }
            
            if (empty || item == null || row == null) {
                setText(null);
                setStyle("");
                currentRow = null;
                // Stop flash animation when cell is emptied
                stopFlashAnimation();
            } else {
                setText(String.valueOf(item));
                
                // Set up listener for this row if it's new
                if (currentRow != row) {
                    currentRow = row;
                    setupPropertyListener();
                }
            }
        }
        
        private void removeListeners() {
            if (currentRow != null) {
                if (idListener != null) {
                    currentRow.idProperty().removeListener(idListener);
                    idListener = null;
                }
                if (quantityListener != null) {
                    currentRow.quantityProperty().removeListener(quantityListener);
                    quantityListener = null;
                }
                if (bidSizeListener != null) {
                    currentRow.bidSizeProperty().removeListener(bidSizeListener);
                    bidSizeListener = null;
                }
                if (askSizeListener != null) {
                    currentRow.askSizeProperty().removeListener(askSizeListener);
                    askSizeListener = null;
                }
            }
        }
        
        private void stopFlashAnimation() {
            if (flashTimeline != null) {
                flashTimeline.stop();
                flashTimeline = null;
            }
            // Reset style to default
            setStyle("");
        }
        
        private void setupPropertyListener() {
            if (currentRow == null) return;
            
            // Determine which property to listen to based on the column
            TableColumn<GridDataRow, Integer> column = getTableColumn();
            if (column == null) return;
            
            // Listen to the appropriate property
            if (column == idColumn) {
                idListener = (obs, oldVal, newVal) -> {
                    // Verify this cell still displays this row
                    if (getTableRow() != null && getTableRow().getItem() == currentRow && oldVal != null && newVal != null && !oldVal.equals(newVal)) {
                        int delta = newVal.intValue() - oldVal.intValue();
                        setText(String.valueOf(newVal));
                        flashCell(delta > 0);
                    }
                };
                currentRow.idProperty().addListener(idListener);
            } else if (column == quantityColumn) {
                quantityListener = (obs, oldVal, newVal) -> {
                    // Verify this cell still displays this row
                    if (getTableRow() != null && getTableRow().getItem() == currentRow && oldVal != null && newVal != null && !oldVal.equals(newVal)) {
                        int delta = newVal.intValue() - oldVal.intValue();
                        setText(String.valueOf(newVal));
                        flashCell(delta > 0);
                    }
                };
                currentRow.quantityProperty().addListener(quantityListener);
            } else if (column == bidSizeColumn) {
                bidSizeListener = (obs, oldVal, newVal) -> {
                    // Verify this cell still displays this row
                    if (getTableRow() != null && getTableRow().getItem() == currentRow && oldVal != null && newVal != null && !oldVal.equals(newVal)) {
                        int delta = newVal.intValue() - oldVal.intValue();
                        setText(String.valueOf(newVal));
                        flashCell(delta > 0);
                    }
                };
                currentRow.bidSizeProperty().addListener(bidSizeListener);
            } else if (column == askSizeColumn) {
                askSizeListener = (obs, oldVal, newVal) -> {
                    // Verify this cell still displays this row
                    if (getTableRow() != null && getTableRow().getItem() == currentRow && oldVal != null && newVal != null && !oldVal.equals(newVal)) {
                        int delta = newVal.intValue() - oldVal.intValue();
                        setText(String.valueOf(newVal));
                        flashCell(delta > 0);
                    }
                };
                currentRow.askSizeProperty().addListener(askSizeListener);
            }
        }

        private void flashCell(boolean positive) {
            // Check flash mode
            if (flashMode == FlashMode.OFF) {
                return;
            }
            
            // Cancel any existing flash animation
            if (flashTimeline != null) {
                flashTimeline.stop();
            }
            
            // Determine base color (green for positive, red for negative)
            String baseColor = positive ? "0, 200, 0" : "200, 0, 0";
            
            if (flashMode == FlashMode.SIMPLE) {
                // Simple mode - just set color briefly without fade
                setStyle("-fx-background-color: rgba(" + baseColor + ", 1.0);");
                flashTimeline = new Timeline(
                    new KeyFrame(Duration.millis(150), e -> setStyle(""))
                );
                flashTimeline.play();
            } else {
                // Full mode - create smooth fade animation
                flashTimeline = new Timeline(
                    new KeyFrame(Duration.millis(0), e -> setStyle("-fx-background-color: rgba(" + baseColor + ", 1.0);")),
                    new KeyFrame(Duration.millis(100), e -> setStyle("-fx-background-color: rgba(" + baseColor + ", 0.8);")),
                    new KeyFrame(Duration.millis(200), e -> setStyle("-fx-background-color: rgba(" + baseColor + ", 0.6);")),
                    new KeyFrame(Duration.millis(300), e -> setStyle("-fx-background-color: rgba(" + baseColor + ", 0.4);")),
                    new KeyFrame(Duration.millis(400), e -> setStyle("-fx-background-color: rgba(" + baseColor + ", 0.2);")),
                    new KeyFrame(Duration.millis(500), e -> setStyle(""))
                );
                flashTimeline.play();
            }
        }
    }

    /**
     * Custom TableCell for String values that flashes light blue on value changes
     */
    class FlashingStringCell extends TableCell<GridDataRow, String> {
        private GridDataRow currentRow = null;
        private ChangeListener<String> nameListener = null;
        private ChangeListener<String> statusListener = null;
        private ChangeListener<String> createdTimestampListener = null;
        private ChangeListener<String> modifiedTimestampListener = null;
        private Timeline flashTimeline = null;

        public FlashingStringCell() {
        }

        @Override
        protected void updateItem(String item, boolean empty) {
            super.updateItem(item, empty);
            
            // Get the row from the table
            GridDataRow row = empty ? null : getTableRow().getItem();
            
            // Remove old listeners if row changed
            if (currentRow != row) {
                removeListeners();
                // Stop any ongoing flash animation when reusing the cell
                stopFlashAnimation();
            }
            
            if (empty || item == null || row == null) {
                setText(null);
                setStyle("");
                currentRow = null;
                // Stop flash animation when cell is emptied
                stopFlashAnimation();
            } else {
                setText(item);
                
                // Set up listener for this row if it's new
                if (currentRow != row) {
                    currentRow = row;
                    setupPropertyListener();
                }
            }
        }
        
        private void removeListeners() {
            if (currentRow != null) {
                if (nameListener != null) {
                    currentRow.nameProperty().removeListener(nameListener);
                    nameListener = null;
                }
                if (statusListener != null) {
                    currentRow.statusProperty().removeListener(statusListener);
                    statusListener = null;
                }
                if (createdTimestampListener != null) {
                    currentRow.createdTimestampProperty().removeListener(createdTimestampListener);
                    createdTimestampListener = null;
                }
                if (modifiedTimestampListener != null) {
                    currentRow.modifiedTimestampProperty().removeListener(modifiedTimestampListener);
                    modifiedTimestampListener = null;
                }
            }
        }
        
        private void stopFlashAnimation() {
            if (flashTimeline != null) {
                flashTimeline.stop();
                flashTimeline = null;
            }
            // Reset style to default
            setStyle("");
        }
        
        private void setupPropertyListener() {
            if (currentRow == null) return;
            
            // Determine which property to listen to based on the column
            TableColumn<GridDataRow, String> column = getTableColumn();
            if (column == null) return;
            
            // Listen to the appropriate property
            if (column == nameColumn) {
                nameListener = (obs, oldVal, newVal) -> {
                    // Verify this cell still displays this row
                    if (getTableRow() != null && getTableRow().getItem() == currentRow && oldVal != null && newVal != null && !oldVal.equals(newVal)) {
                        setText(newVal);
                        flashCell();
                    }
                };
                currentRow.nameProperty().addListener(nameListener);
            } else if (column == statusColumn) {
                statusListener = (obs, oldVal, newVal) -> {
                    // Verify this cell still displays this row
                    if (getTableRow() != null && getTableRow().getItem() == currentRow && oldVal != null && newVal != null && !oldVal.equals(newVal)) {
                        setText(newVal);
                        flashCell();
                    }
                };
                currentRow.statusProperty().addListener(statusListener);
            } else if (column == createdTimestampColumn) {
                createdTimestampListener = (obs, oldVal, newVal) -> {
                    // Verify this cell still displays this row
                    if (getTableRow() != null && getTableRow().getItem() == currentRow && oldVal != null && newVal != null && !oldVal.equals(newVal)) {
                        setText(newVal);
                        flashCell();
                    }
                };
                currentRow.createdTimestampProperty().addListener(createdTimestampListener);
            } else if (column == modifiedTimestampColumn) {
                modifiedTimestampListener = (obs, oldVal, newVal) -> {
                    // Verify this cell still displays this row
                    if (getTableRow() != null && getTableRow().getItem() == currentRow && oldVal != null && newVal != null && !oldVal.equals(newVal)) {
                        setText(newVal);
                        flashCell();
                    }
                };
                currentRow.modifiedTimestampProperty().addListener(modifiedTimestampListener);
            }
        }

        private void flashCell() {
            // Check flash mode
            if (flashMode == FlashMode.OFF) {
                return;
            }
            
            // Cancel any existing flash animation
            if (flashTimeline != null) {
                flashTimeline.stop();
            }
            
            // Light blue color
            String baseColor = "173, 216, 230";
            
            if (flashMode == FlashMode.SIMPLE) {
                // Simple mode - just set color briefly without fade
                setStyle("-fx-background-color: rgba(" + baseColor + ", 1.0);");
                flashTimeline = new Timeline(
                    new KeyFrame(Duration.millis(150), e -> setStyle(""))
                );
                flashTimeline.play();
            } else {
                // Full mode - create smooth fade animation
                flashTimeline = new Timeline(
                    new KeyFrame(Duration.millis(0), e -> setStyle("-fx-background-color: rgba(" + baseColor + ", 1.0);")),
                    new KeyFrame(Duration.millis(100), e -> setStyle("-fx-background-color: rgba(" + baseColor + ", 0.8);")),
                    new KeyFrame(Duration.millis(200), e -> setStyle("-fx-background-color: rgba(" + baseColor + ", 0.6);")),
                    new KeyFrame(Duration.millis(300), e -> setStyle("-fx-background-color: rgba(" + baseColor + ", 0.4);")),
                    new KeyFrame(Duration.millis(400), e -> setStyle("-fx-background-color: rgba(" + baseColor + ", 0.2);")),
                    new KeyFrame(Duration.millis(500), e -> setStyle(""))
                );
                flashTimeline.play();
            }
        }
    }
}
