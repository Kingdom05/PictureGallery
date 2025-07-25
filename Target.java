package ir.hakim.classes;

import com.fasterxml.jackson.annotation.JsonAlias;
import ir.hakim.Simulators.Simulator;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import javax.swing.*;
import java.util.*;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import static java.util.concurrent.TimeUnit.*;

@JsonIgnoreProperties(ignoreUnknown = true)
public class Target {

    String relatedSensorString;
    UUID uid;
    Integer id;

    Double speed;
    List<String> relatedSensors;

    HashMap<String, Simulator> simulators;
    JTextArea logger;
    private String name;
    @JsonAlias("Route")
    private List<?> path;  // مسیر چند نقطه ای به جای مختصات جداگانه

    // پارامترهای محاسباتی و وضعیت متحرک سازی
    private static final double EARTH_RADIUS = 6371;

    // برای حرکت در مسیر چند نقطه ای:
    private int currentSegmentIndex = 0;  // شاخص قطعه مسیر که هدف در آن قرار دارد
    private double latIncrement, lonIncrement, currentLat, currentLon;
    private volatile boolean stopRequested = false;

    private final ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(1);
    private ScheduledFuture<?> moverHandle;

    public Target() {
        this.uid = UUID.randomUUID();
        this.path = new ArrayList<>();
        this.relatedSensors = new ArrayList<>();
        this.speed = 0.0;
        this.id = 0;
    }

    // سازنده اصلی با path و سرعت
    public Target(Integer id, List<ir.hakim.classes.Point> path, Double speed, ArrayList<String> selectedSensors) {
        this.uid = UUID.randomUUID();
        this.id = id != null ? id : 0;
        this.path = path != null ? new ArrayList<>(path) : new ArrayList<>();
        this.speed = speed != null ? speed : 0.0;
        this.relatedSensors = selectedSensors != null ? new ArrayList<>(selectedSensors) : new ArrayList<>();
    }

    public Target(Integer id, List<Point> pathPoint, double speed, List<String> selectedSensors) {
        this.uid = UUID.randomUUID();
        this.id = id != null ? id : 0;
        this.path = pathPoint != null ? new ArrayList<>(pathPoint) : new ArrayList<>();
        this.speed = speed;
        this.relatedSensors = selectedSensors != null ? new ArrayList<>(selectedSensors) : new ArrayList<>();
    }

    // متد اصلی حرکت هدف در مسیر چند نقطه ای
    final Runnable mover = new Runnable() {
        @Override
        public void run() {
            try {
                if (stopRequested || path == null || path.size() < 2 || currentSegmentIndex >= path.size() - 1) {
                    // پایان حرکت
                    if (moverHandle != null && !moverHandle.isCancelled()) {
                        moverHandle.cancel(true);
                    }
                    if (logger != null) {
                        SwingUtilities.invokeLater(() -> {
                            logger.insert(String.format("%n==================== [%s] [%s] Target %d moves finished ====================%n%n", 
                                new Date(), name != null ? name : "Unknown", id), 0);
                        });
                    }
                    return;
                }

                Point startPoint = (Point) path.get(currentSegmentIndex);
                Point endPoint = (Point) path.get(currentSegmentIndex + 1);

                if (startPoint == null || endPoint == null) {
                    if (logger != null) {
                        SwingUtilities.invokeLater(() -> {
                            logger.insert(String.format("Invalid path points for target %d. Stopping movement.%n", id), 0);
                        });
                    }
                    stop();
                    return;
                }

                // حرکت در قطعه فعلی
                currentLat = changeValue(currentLat, endPoint.getLatitude(), latIncrement);
                currentLon = changeValue(currentLon, endPoint.getLongitude(), lonIncrement);

                // اگر به انتهای قطعه رسیدیم، به قطعه بعدی بریم
                if (Math.abs(currentLat - endPoint.getLatitude()) < 0.0001 && 
                    Math.abs(currentLon - endPoint.getLongitude()) < 0.0001) {
                    currentSegmentIndex++;
                    if (currentSegmentIndex < path.size() - 1) {
                        setupSegmentMovement((Point) path.get(currentSegmentIndex), (Point) path.get(currentSegmentIndex + 1));
                    }
                }

                // ارسال به شبیه سازها
                if (relatedSensors != null && simulators != null) {
                    for (String sensorName : relatedSensors) {
                        if (sensorName != null && !sensorName.trim().isEmpty()) {
                            Simulator simulator = simulators.get(sensorName);
                            if (simulator != null && simulator.isConnected()) {
                                simulator.process(id, currentLat, currentLon);
                            } else if (simulator != null && !simulator.isWaiting()) {
                                new Thread(() -> {
                                    try {
                                        simulator.init();
                                    } catch (Exception e) {
                                        if (logger != null) {
                                            SwingUtilities.invokeLater(() -> {
                                                logger.insert(String.format("Error initializing simulator %s: %s%n", sensorName, e.getMessage()), 0);
                                            });
                                        }
                                    }
                                }).start();
                            }
                        }
                    }
                }
            } catch (Exception e) {
                if (logger != null) {
                    SwingUtilities.invokeLater(() -> {
                        logger.insert(String.format("Error in target %d movement: %s%n", id, e.getMessage()), 0);
                    });
                }
                e.printStackTrace();
            }
        }
    };

    // مقدار دهی اولیه برای حرکت در یک قطعه مسیر
    private void setupSegmentMovement(Point start, Point end) {
        if (start == null || end == null) {
            if (logger != null) {
                SwingUtilities.invokeLater(() -> {
                    logger.insert(String.format("Invalid segment points for target %d%n", id), 0);
                });
            }
            return;
        }

        try {
            double distance = calculateDistance(start.getLatitude(), start.getLongitude(), end.getLatitude(), end.getLongitude());
            if (distance == 0 || speed == null || speed <= 0) {
                if (logger != null) {
                    SwingUtilities.invokeLater(() -> {
                        logger.insert(String.format("Invalid distance or speed for target %d%n", id), 0);
                    });
                }
                return;
            }

            double arrivalSeconds = distance / (speed / 3600); // سرعت کیلومتر بر ساعت به ثانیه تبدیل شده
            if (arrivalSeconds > 0) {
                latIncrement = (end.getLatitude() - start.getLatitude()) / arrivalSeconds;
                lonIncrement = (end.getLongitude() - start.getLongitude()) / arrivalSeconds;
            } else {
                latIncrement = 0;
                lonIncrement = 0;
            }

            currentLat = start.getLatitude();
            currentLon = start.getLongitude();
        } catch (Exception e) {
            if (logger != null) {
                SwingUtilities.invokeLater(() -> {
                    logger.insert(String.format("Error setting up segment movement for target %d: %s%n", id, e.getMessage()), 0);
                });
            }
            e.printStackTrace();
        }
    }

    // شروع حرکت
    public void run() {
        try {
            if (path == null || path.size() < 2) {
                if (logger != null) {
                    SwingUtilities.invokeLater(() -> {
                        logger.insert(String.format("Target %d path is invalid or too short.%n", id), 0);
                    });
                } else {
                    System.out.println(String.format("Target %d path is invalid or too short.", id));
                }
                return;
            }

            stopRequested = false;
            currentSegmentIndex = 0;
            setupSegmentMovement((Point) path.get(0), (Point) path.get(1));
            
            if (moverHandle != null && !moverHandle.isCancelled()) {
                moverHandle.cancel(true);
            }
            
            moverHandle = scheduler.scheduleAtFixedRate(mover, 0, 1, SECONDS);

            if (logger != null) {
                SwingUtilities.invokeLater(() -> {
                    logger.insert(String.format("%n==================== [%s] [%s] Target %d starts moving... ====================%n%n", 
                        new Date(), name != null ? name : "Unknown", id), 0);
                });
            }
        } catch (Exception e) {
            if (logger != null) {
                SwingUtilities.invokeLater(() -> {
                    logger.insert(String.format("Error starting target %d: %s%n", id, e.getMessage()), 0);
                });
            }
            e.printStackTrace();
        }
    }

    public void stop() {
        try {
            stopRequested = true;

            if (moverHandle != null && !moverHandle.isCancelled()) {
                moverHandle.cancel(true);
            }

            if (relatedSensors != null && simulators != null) {
                for (String sensorName : relatedSensors) {
                    if (sensorName != null && !sensorName.trim().isEmpty()) {
                        Simulator simulator = simulators.get(sensorName);
                        if (simulator != null) {
                            try {
                                simulator.stop();
                            } catch (Exception e) {
                                if (logger != null) {
                                    SwingUtilities.invokeLater(() -> {
                                        logger.insert(String.format("Error stopping simulator %s: %s%n", sensorName, e.getMessage()), 0);
                                    });
                                }
                            }
                        }
                    }
                }
            }

            if (logger != null) {
                SwingUtilities.invokeLater(() -> {
                    logger.insert(String.format("%n==================== [%s] [%s] Target %d stopped! ====================%n%n", 
                        new Date(), name != null ? name : "Unknown", id), 0);
                });
            }
        } catch (Exception e) {
            if (logger != null) {
                SwingUtilities.invokeLater(() -> {
                    logger.insert(String.format("Error stopping target %d: %s%n", id, e.getMessage()), 0);
                });
            }
            e.printStackTrace();
        }
    }

    private double changeValue(double curValue, double endValue, double change) {
        if (Double.isNaN(change) || Double.isInfinite(change)) {
            return curValue;
        }
        
        double value = curValue;
        if (change > 0)
            value = Math.min(endValue, curValue + change);
        else if (change < 0)
            value = Math.max(endValue, curValue + change);
        return value;
    }

    private double calculateDistance(double lat1, double lon1, double lat2, double lon2) {
        try {
            double lat1Rad = Math.toRadians(lat1);
            double lat2Rad = Math.toRadians(lat2);
            double lon1Rad = Math.toRadians(lon1);
            double lon2Rad = Math.toRadians(lon2);

            double x = (lon2Rad - lon1Rad) * Math.cos((lat1Rad + lat2Rad) / 2);
            double y = (lat2Rad - lat1Rad);

            return Math.sqrt(x * x + y * y) * EARTH_RADIUS;
        } catch (Exception e) {
            if (logger != null) {
                SwingUtilities.invokeLater(() -> {
                    logger.insert(String.format("Error calculating distance for target %d: %s%n", id, e.getMessage()), 0);
                });
            }
            return 0.0;
        }
    }

    public void setLogger(JTextArea logger) {
        this.logger = logger;
    }

    public String getUid() {
        return uid != null ? uid.toString() : UUID.randomUUID().toString();
    }

    public void setUid(String uid) {
        try {
            this.uid = uid != null ? UUID.fromString(uid) : UUID.randomUUID();
        } catch (IllegalArgumentException e) {
            this.uid = UUID.randomUUID();
            if (logger != null) {
                SwingUtilities.invokeLater(() -> {
                    logger.insert(String.format("Invalid UID format, generated new UID for target %d%n", id), 0);
                });
            }
        }
    }

    public int getId() {
        return id != null ? id : 0;
    }

    public void setId(int id) {
        this.id = id;
    }

    public double getSpeed() {
        return speed != null ? speed : 0.0;
    }

    public void setSpeed(double speed) {
        this.speed = speed;
    }

    public List<String> getRelatedSensors() {
        return this.relatedSensors != null ? this.relatedSensors : new ArrayList<>();
    }

    public String getRelatedSensorsString() {
        if (relatedSensors == null || relatedSensors.isEmpty())
            return "";
        return String.join(", ", relatedSensors);
    }

    public void setRelatedSensors(List<String> relatedSensors) {
        this.relatedSensors = relatedSensors != null ? new ArrayList<>(relatedSensors) : new ArrayList<>();
    }

    public void setRelatedSensorsString(String relatedSensorNames) {
        this.relatedSensors = getListOfSensorNames(relatedSensorNames);
    }

    public void setSimulators(HashMap<String, Simulator> simulators) {
        this.simulators = simulators;
    }

    public static List<String> getListOfSensorNames(String sensorNames) {
        List<String> lstSensorNames = new ArrayList<>();
        if (sensorNames != null && !sensorNames.trim().isEmpty()) {
            String[] names = sensorNames.split(",");
            for (String name : names) {
                if (name != null && !name.trim().isEmpty()) {
                    lstSensorNames.add(name.trim());
                }
            }
        }
        return lstSensorNames;
    }

    public static String getStringOfSensorNames(List<String> sensorNames) {
        if (sensorNames == null || sensorNames.isEmpty())
            return "";
        else {
            return sensorNames.stream()
                    .filter(Objects::nonNull)
                    .filter(s -> !s.trim().isEmpty())
                    .reduce((a, b) -> a + ", " + b)
                    .orElse("");
        }
    }

    public String getName() {
        return name != null ? name : "Target_" + id;
    }

    public void setName(String name) {
        this.name = name;
    }

    public Collection<? extends ir.hakim.classes.Point> getPath() {
        if (path == null) {
            return new ArrayList<>();
        }
        try {
            return (Collection<? extends ir.hakim.classes.Point>) path;
        } catch (ClassCastException e) {
            return new ArrayList<>();
        }
    }

    public void setPath(List<Point> path) {
        this.path = path != null ? new ArrayList<>(path) : new ArrayList<>();
    }

    public String[] getSensorIds() {
        if (relatedSensors == null || relatedSensors.isEmpty()) {
            return new String[0];
        }
        return relatedSensors.toArray(new String[0]);
    }

    // Clean up resources when target is destroyed
    @Override
    protected void finalize() throws Throwable {
        try {
            stop();
            if (scheduler != null && !scheduler.isShutdown()) {
                scheduler.shutdown();
            }
        } finally {
            super.finalize();
        }
    }
}