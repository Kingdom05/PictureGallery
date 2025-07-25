package ir.hakim.Simulators;

import javax.swing.*;
import java.io.IOException;
import java.net.Socket;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Abstract base class for all sensor simulators
 * Provides common functionality and interface for different sensor types
 */
public abstract class Simulator {
    protected String sensorName;
    protected String ip;
    protected int port;
    protected double sensorLat;
    protected double sensorLon;
    protected JTextArea logger;
    
    protected Socket socket;
    protected final AtomicBoolean connected = new AtomicBoolean(false);
    protected final AtomicBoolean waiting = new AtomicBoolean(false);
    protected final AtomicBoolean stopRequested = new AtomicBoolean(false);

    /**
     * Initialize the simulator and establish connection
     */
    public void init() {
        try {
            waiting.set(true);
            stopRequested.set(false);
            
            if (logger != null) {
                SwingUtilities.invokeLater(() -> {
                    logger.insert(String.format("Initializing %s simulator for sensor %s at %s:%d%n", 
                        getSimulatorType(), sensorName, ip, port), 0);
                });
            }
            
            // Attempt to connect
            connect();
            
            if (connected.get()) {
                if (logger != null) {
                    SwingUtilities.invokeLater(() -> {
                        logger.insert(String.format("%s simulator for sensor %s connected successfully%n", 
                            getSimulatorType(), sensorName), 0);
                    });
                }
            }
            
        } catch (Exception e) {
            connected.set(false);
            if (logger != null) {
                SwingUtilities.invokeLater(() -> {
                    logger.insert(String.format("Error initializing %s simulator for sensor %s: %s%n", 
                        getSimulatorType(), sensorName, e.getMessage()), 0);
                });
            }
            e.printStackTrace();
        } finally {
            waiting.set(false);
        }
    }

    /**
     * Establish connection to the sensor
     */
    protected void connect() {
        try {
            if (socket != null && !socket.isClosed()) {
                socket.close();
            }
            
            socket = new Socket(ip, port);
            socket.setSoTimeout(5000); // 5 second timeout
            connected.set(true);
            
        } catch (IOException e) {
            connected.set(false);
            if (logger != null) {
                SwingUtilities.invokeLater(() -> {
                    logger.insert(String.format("Failed to connect to sensor %s at %s:%d - %s%n", 
                        sensorName, ip, port, e.getMessage()), 0);
                });
            }
        }
    }

    /**
     * Process target data and send to sensor
     * @param targetId The ID of the target
     * @param latitude Current latitude of the target
     * @param longitude Current longitude of the target
     */
    public void process(int targetId, double latitude, double longitude) {
        if (!connected.get() || stopRequested.get()) {
            return;
        }
        
        try {
            // Calculate distance from sensor to target
            double distance = calculateDistance(sensorLat, sensorLon, latitude, longitude);
            
            // Send data to sensor (implemented by subclasses)
            sendData(targetId, latitude, longitude, distance);
            
        } catch (Exception e) {
            if (logger != null) {
                SwingUtilities.invokeLater(() -> {
                    logger.insert(String.format("Error processing data for sensor %s: %s%n", 
                        sensorName, e.getMessage()), 0);
                });
            }
            
            // Try to reconnect on error
            connected.set(false);
            if (!stopRequested.get()) {
                new Thread(this::init).start();
            }
        }
    }

    /**
     * Send data to the sensor (to be implemented by subclasses)
     * @param targetId The ID of the target
     * @param latitude Current latitude of the target
     * @param longitude Current longitude of the target
     * @param distance Distance from sensor to target
     */
    protected abstract void sendData(int targetId, double latitude, double longitude, double distance);

    /**
     * Get the type of this simulator (to be implemented by subclasses)
     * @return The simulator type name
     */
    protected abstract String getSimulatorType();

    /**
     * Stop the simulator and close connections
     */
    public void stop() {
        try {
            stopRequested.set(true);
            connected.set(false);
            
            if (socket != null && !socket.isClosed()) {
                socket.close();
            }
            
            if (logger != null) {
                SwingUtilities.invokeLater(() -> {
                    logger.insert(String.format("%s simulator for sensor %s stopped%n", 
                        getSimulatorType(), sensorName), 0);
                });
            }
            
        } catch (Exception e) {
            if (logger != null) {
                SwingUtilities.invokeLater(() -> {
                    logger.insert(String.format("Error stopping simulator for sensor %s: %s%n", 
                        sensorName, e.getMessage()), 0);
                });
            }
        }
    }

    /**
     * Calculate distance between two geographic points using Haversine formula
     */
    protected double calculateDistance(double lat1, double lon1, double lat2, double lon2) {
        final double EARTH_RADIUS = 6371; // kilometers
        
        double lat1Rad = Math.toRadians(lat1);
        double lat2Rad = Math.toRadians(lat2);
        double lon1Rad = Math.toRadians(lon1);
        double lon2Rad = Math.toRadians(lon2);

        double x = (lon2Rad - lon1Rad) * Math.cos((lat1Rad + lat2Rad) / 2);
        double y = (lat2Rad - lat1Rad);

        return Math.sqrt(x * x + y * y) * EARTH_RADIUS;
    }

    // Getters and Setters
    public boolean isConnected() {
        return connected.get();
    }

    public boolean isWaiting() {
        return waiting.get();
    }

    public String getSensorName() {
        return sensorName;
    }

    public void setSensorName(String sensorName) {
        this.sensorName = sensorName;
    }

    public String getIp() {
        return ip;
    }

    public void setIp(String ip) {
        this.ip = ip;
    }

    public int getPort() {
        return port;
    }

    public void setPort(int port) {
        this.port = port;
    }

    public double getSensorLat() {
        return sensorLat;
    }

    public void setSensorLat(double sensorLat) {
        this.sensorLat = sensorLat;
    }

    public double getSensorLon() {
        return sensorLon;
    }

    public void setSensorLon(double sensorLon) {
        this.sensorLon = sensorLon;
    }

    public void setLogger(JTextArea logger) {
        this.logger = logger;
    }
}