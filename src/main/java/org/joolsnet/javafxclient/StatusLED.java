package org.joolsnet.javafxclient;

import javafx.geometry.Pos;
import javafx.scene.control.Label;
import javafx.scene.layout.HBox;
import javafx.scene.paint.Color;
import javafx.scene.shape.Circle;

/**
 * A status LED indicator with RAG (Red, Amber, Green) + Grey states
 */
public class StatusLED extends HBox {
    
    public enum Status {
        RED(Color.rgb(220, 0, 0)),
        AMBER(Color.rgb(255, 180, 0)),
        GREEN(Color.rgb(0, 200, 0)),
        GREY(Color.rgb(128, 128, 128));
        
        private final Color color;
        
        Status(Color color) {
            this.color = color;
        }
        
        public Color getColor() {
            return color;
        }
    }
    
    private final Circle indicator;
    private final Label label;
    private Status currentStatus;
    
    public StatusLED(String labelText) {
        this(labelText, Status.GREY);
    }
    
    public StatusLED(String labelText, Status initialStatus) {
        super(5);
        setAlignment(Pos.CENTER_LEFT);
        
        // Create LED indicator
        indicator = new Circle(6);
        indicator.setStroke(Color.BLACK);
        indicator.setStrokeWidth(0.5);
        
        // Create label
        label = new Label(labelText);
        label.setStyle("-fx-font-size: 11px;");
        
        getChildren().addAll(indicator, label);
        
        // Set initial status - done after all fields are initialized
        this.currentStatus = initialStatus;
        indicator.setFill(initialStatus.getColor());
    }
    
    public void setStatus(Status status) {
        this.currentStatus = status;
        indicator.setFill(status.getColor());
    }
    
    public Status getStatus() {
        return currentStatus;
    }
    
    public void setLabelText(String text) {
        label.setText(text);
    }
}
