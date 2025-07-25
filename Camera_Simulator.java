package ir.hakim.Simulators;

import javax.swing.*;
import java.io.PrintWriter;
import java.text.SimpleDateFormat;
import java.util.Date;

/**
 * Camera sensor simulator implementation
 * Simulates a camera sensor that captures visual data of targets within its field of view
 */
public class Camera_Simulator extends Simulator {
    
    private static final double CAMERA_RANGE = 10.0; // kilometers - shorter range than radar
    private static final double FIELD_OF_VIEW = 90.0; // degrees
    private PrintWriter output;
    private final SimpleDateFormat dateFormat = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");

    @Override
    protected void connect() {
        try {
            super.connect();
            
            if (connected.get()) {
                // Initialize output stream for sending data
                output = new PrintWriter(socket.getOutputStream(), true);
                
                // Send initialization message
                String initMessage = String.format("[%s] CAMERA_INIT: Sensor %s at (%.6f, %.6f) initialized with %.1f° FOV", 
                    dateFormat.format(new Date()), sensorName, sensorLat, sensorLon, FIELD_OF_VIEW);
                output.println(initMessage);
                
                if (logger != null) {
                    SwingUtilities.invokeLater(() -> {
                        logger.insert(String.format("Camera sensor %s initialization message sent%n", sensorName), 0);
                    });
                }
            }
        } catch (Exception e) {
            connected.set(false);
            if (logger != null) {
                SwingUtilities.invokeLater(() -> {
                    logger.insert(String.format("Error initializing camera sensor %s: %s%n", sensorName, e.getMessage()), 0);
                });
            }
        }
    }

    @Override
    protected void sendData(int targetId, double latitude, double longitude, double distance) {
        try {
            if (output == null || !connected.get()) {
                return;
            }

            // Check if target is within camera range
            if (distance <= CAMERA_RANGE) {
                // Calculate bearing from sensor to target
                double bearing = calculateBearing(sensorLat, sensorLon, latitude, longitude);
                
                // Simulate camera field of view check (simplified - assumes camera points north)
                double cameraDirection = 0.0; // North
                double relativeBearing = Math.abs(bearing - cameraDirection);
                if (relativeBearing > 180) {
                    relativeBearing = 360 - relativeBearing;
                }
                
                if (relativeBearing <= FIELD_OF_VIEW / 2) {
                    // Target is within field of view
                    
                    // Simulate image quality based on distance
                    String imageQuality = getImageQuality(distance);
                    
                    // Create camera detection message
                    String cameraMessage = String.format(
                        "[%s] CAMERA_CAPTURE: Target %d captured at distance %.2f km, bearing %.1f degrees, quality: %s from sensor %s",
                        dateFormat.format(new Date()), targetId, distance, bearing, imageQuality, sensorName
                    );
                    
                    output.println(cameraMessage);
                    
                    if (logger != null) {
                        SwingUtilities.invokeLater(() -> {
                            logger.insert(String.format("Camera %s: Target %d captured (%.2f km, %.1f°, %s quality)%n", 
                                sensorName, targetId, distance, bearing, imageQuality), 0);
                        });
                    }
                } else {
                    // Target out of field of view
                    if (Math.random() < 0.05) { // 5% chance to log out-of-FOV
                        if (logger != null) {
                            SwingUtilities.invokeLater(() -> {
                                logger.insert(String.format("Camera %s: Target %d out of field of view (bearing %.1f°)%n", 
                                    sensorName, targetId, bearing), 0);
                            });
                        }
                    }
                }
            } else {
                // Target out of range
                if (Math.random() < 0.05) { // 5% chance to log out-of-range
                    if (logger != null) {
                        SwingUtilities.invokeLater(() -> {
                            logger.insert(String.format("Camera %s: Target %d out of range (%.2f km > %.2f km)%n", 
                                sensorName, targetId, distance, CAMERA_RANGE), 0);
                        });
                    }
                }
            }
            
        } catch (Exception e) {
            if (logger != null) {
                SwingUtilities.invokeLater(() -> {
                    logger.insert(String.format("Error sending camera data for sensor %s: %s%n", 
                        sensorName, e.getMessage()), 0);
                });
            }
            throw e; // Re-throw to trigger reconnection
        }
    }

    /**
     * Determine image quality based on distance
     */
    private String getImageQuality(double distance) {
        if (distance <= 2.0) {
            return "EXCELLENT";
        } else if (distance <= 5.0) {
            return "GOOD";
        } else if (distance <= 8.0) {
            return "FAIR";
        } else {
            return "POOR";
        }
    }

    /**
     * Calculate bearing from sensor to target
     */
    private double calculateBearing(double lat1, double lon1, double lat2, double lon2) {
        double lat1Rad = Math.toRadians(lat1);
        double lat2Rad = Math.toRadians(lat2);
        double deltaLonRad = Math.toRadians(lon2 - lon1);

        double y = Math.sin(deltaLonRad) * Math.cos(lat2Rad);
        double x = Math.cos(lat1Rad) * Math.sin(lat2Rad) - 
                   Math.sin(lat1Rad) * Math.cos(lat2Rad) * Math.cos(deltaLonRad);

        double bearingRad = Math.atan2(y, x);
        double bearingDeg = Math.toDegrees(bearingRad);
        
        // Normalize to 0-360 degrees
        return (bearingDeg + 360) % 360;
    }

    @Override
    protected String getSimulatorType() {
        return "Camera";
    }

    @Override
    public void stop() {
        try {
            if (output != null && connected.get()) {
                String stopMessage = String.format("[%s] CAMERA_STOP: Sensor %s shutting down", 
                    dateFormat.format(new Date()), sensorName);
                output.println(stopMessage);
                output.close();
            }
        } catch (Exception e) {
            // Ignore errors during shutdown
        } finally {
            output = null;
            super.stop();
        }
    }
}