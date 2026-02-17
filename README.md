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
