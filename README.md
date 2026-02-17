# JavaFX Client

A JavaFX application that dynamically loads and displays FXML files from a remote server.

## Features

- Connect to FXML server (localhost:8080)
- List available FXML files from server
- Load and display FXML dynamically
- Built-in logging for debugging
- Modern JavaFX interface

## Prerequisites

- Java 21 or higher
- JavaFX 21 (automatically managed by Gradle)

## Building and Running

### Using Gradle Wrapper

```bash
# Build the project
./gradlew build

# Run the client
./gradlew run
```

## Usage

1. **Start the Server First**: Make sure JavaFXServer is running before starting the client

2. **Launch the Client**: Run the application using `./gradlew run`

3. **Select FXML**: 
   - Click "Refresh List" to load available FXML files from the server
   - Select an FXML file from the dropdown
   - Click "Load FXML" to fetch and display it

4. **View Results**: The FXML content will be rendered in the center pane, with logs at the bottom

## Architecture

### Components

- **FXMLClientApp**: Main application class
- **FXMLClient**: Handles TCP communication with server
- **UIController**: Manages the user interface

### Communication

The client uses TCP sockets to communicate with the server:
- Fetches list of available FXML files
- Downloads FXML content on demand
- Dynamically loads FXML using JavaFX FXMLLoader

## Configuration

To connect to a different server:
1. Modify the host and port in `FXMLClientApp.java`:
   ```java
   client = new FXMLClient("your-server-host", 8080);
   ```

## Sample FXML Files

The server provides these sample files:
- **welcome.fxml**: Simple welcome screen with centered content
- **login.fxml**: Login form with username/password fields
- **dashboard.fxml**: Complex dashboard with sidebar navigation and content areas

## Troubleshooting

### Connection Refused
- Ensure the JavaFXServer is running
- Check that the server is listening on port 8080
- Verify firewall settings

### FXML Loading Errors
- Check the server logs for file reading errors
- Ensure FXML files are valid JavaFX FXML format
- Review the client log area for detailed error messages

## Performance

### Profiling

The application includes Java Flight Recorder (JFR) profiling support for performance analysis:

```bash
# Run with profiling enabled (120 second recording)
./gradlew run-profile

# Recording saved to: build/jfr/profile-<timestamp>.jfr
# Analyze with JDK Mission Control: jmc <file.jfr>
```

### Optimizations

**String Parsing**
- Optimized message parsing using `indexOf()` instead of `String.split()`
- Reduces memory allocations by ~70% during high-frequency updates
- Eliminates intermediate String[] array creation

**Update Coalescing**
- Double-buffer architecture coalesces multiple updates to the same cell
- Prevents JavaFX event queue saturation during high update rates
- Metrics show coalescing rate and batch processing efficiency

### Performance Characteristics

**Current Bottlenecks** (identified via JFR profiling):

1. **JavaFX Layout Engine** (~99% of CPU time)
   - `Parent.updateBounds()` triggered on every cell property update
   - JavaFX scene graph recalculation is the primary limitation
   - This overhead exists regardless of flash animations

2. **TableView Rendering**
   - Cell virtualization and recycling adds overhead during scrolling
   - CSS processing for cell styles
   - Font rendering and text layout calculations

**Throughput Limits**:
- The underlying JavaFX TableView implementation limits graphics throughput
- Sustained update rates above ~3,000-5,000 updates/second will cause coalescing
- This is a fundamental limitation of the JavaFX rendering pipeline, not the application code

### Recommendations for High-Throughput Scenarios

If you need to handle higher update rates:

1. **Reduce visible rows**: Smaller TableView = less layout work
2. **Use fixed column widths**: Eliminates dynamic width calculations
3. **Disable animations**: Set `FlashMode.OFF` via `setFlashMode()`
4. **Consider alternatives**: For extreme throughput (>10K updates/sec), consider:
   - Custom Canvas-based rendering
   - WebGL-based visualization
   - Native UI frameworks with lower overhead
