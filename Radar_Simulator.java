package ir.hakim.Simulators;

import javax.swing.*;
import java.io.PrintWriter;
import java.text.SimpleDateFormat;
import java.util.Date;

/**
 * Radar sensor simulator implementation
 * Simulates a radar sensor that tracks targets within its range
 */
public class Radar_Simulator extends Simulator {
    
    private static final double RADAR_RANGE = 50.0; // kilometers
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
                String initMessage = String.format("[%s] RADAR_INIT: Sensor %s at (%.6f, %.6f) initialized", 
                    dateFormat.format(new Date()), sensorName, sensorLat, sensorLon);
                output.println(initMessage);
                
                if (logger != null) {
                    SwingUtilities.invokeLater(() -> {
                        logger.insert(String.format("Radar sensor %s initialization message sent%n", sensorName), 0);
                    });
                }
            }
        } catch (Exception e) {
            connected.set(false);
            if (logger != null) {
                SwingUtilities.invokeLater(() -> {
                    logger.insert(String.format("Error initializing radar sensor %s: %s%n", sensorName, e.getMessage()), 0);
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

            // Check if target is within radar range
            if (distance <= RADAR_RANGE) {
                // Calculate bearing from sensor to target
                double bearing = calculateBearing(sensorLat, sensorLon, latitude, longitude);
                
                // Create radar detection message
                String radarMessage = String.format(
                    "[%s] RADAR_DETECT: Target %d detected at distance %.2f km, bearing %.1f degrees from sensor %s",
                    dateFormat.format(new Date()), targetId, distance, bearing, sensorName
                );
                
                output.println(radarMessage);
                
                if (logger != null) {
                    SwingUtilities.invokeLater(() -> {
                        logger.insert(String.format("Radar %s: Target %d detected (%.2f km, %.1f°)%n", 
                            sensorName, targetId, distance, bearing), 0);
                    });
                }
            } else {
                // Target out of range - send loss message occasionally
                if (Math.random() < 0.1) { // 10% chance to log out-of-range
                    if (logger != null) {
                        SwingUtilities.invokeLater(() -> {
                            logger.insert(String.format("Radar %s: Target %d out of range (%.2f km > %.2f km)%n", 
                                sensorName, targetId, distance, RADAR_RANGE), 0);
                        });
                    }
                }
            }
            
        } catch (Exception e) {
            if (logger != null) {
                SwingUtilities.invokeLater(() -> {
                    logger.insert(String.format("Error sending radar data for sensor %s: %s%n", 
                        sensorName, e.getMessage()), 0);
                });
            }
            throw e; // Re-throw to trigger reconnection
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
        return "Radar";
    }

    @Override
    public void stop() {
        try {
            if (output != null && connected.get()) {
                String stopMessage = String.format("[%s] RADAR_STOP: Sensor %s shutting down", 
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