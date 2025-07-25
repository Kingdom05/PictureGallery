package ir.hakim.forms;

import ir.hakim.classes.Point;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import ir.hakim.Simulators.Simulator;
import ir.hakim.classes.*;
import org.bson.Document;

import javax.swing.*;
import java.awt.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.List;
import java.util.*;
import java.util.stream.Collectors;

import static javax.swing.SwingUtilities.updateComponentTreeUI;

@SuppressWarnings("rawtypes")
public class MainForm {
    private final String configFileName = "config.data";
    private List<String> lstIncludedSensorIds;
    private JPanel pnlMain;
    private JTable jtblSensors;
    private final ReadOnlyTableModel sensorsTableModel = new ReadOnlyTableModel(),
            targetsTableModel = new ReadOnlyTableModel();
    private final Icon deleteIcon = new ImageIcon(SVGTranscoder.createImage(18, String.valueOf(getClass().getClassLoader().getResource("gc.svg"))));
    private JLabel lblSensors;
    private JLabel lblIncludedSensors;
    private JButton btnAddSensor;
    private JLabel lblNoSensors;
    private JScrollPane jspSensors;
    private JLabel lblTargets;
    private JLabel lblDefinedTargets;
    private JLabel lblNoTargets;
    private JScrollPane jspTargets;
    private JTable jtblTargets;
    private JButton btnAddTarget;
    private JTabbedPane tabs;
    private JButton btnStart;
    private JButton btnStop;
    private JLabel lblSimulation;
    private JTextArea txtLogs;
    private JButton btnClear;
    private JButton btnSelectPoint;
    private final Icon editIcon = new ImageIcon(SVGTranscoder.createImage(18, String.valueOf(getClass().getClassLoader().getResource("edit.svg"))));

    private void removeSensorIdFromConfig(String sensorId) {
        try {
            File f = new File(configFileName);
            if (!f.exists()) return;

            List<String> lines = Files.readAllLines(Paths.get(configFileName));
            lines.removeIf(line -> line.trim().equals(sensorId));

            Files.write(Paths.get(configFileName), lines);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private List<Target> runningTargets;

    public MainForm(Component parent, Target originalTarget) {
        jtblSensors.setModel(sensorsTableModel);
        jtblTargets.setModel(targetsTableModel);

        configUIComponents();

        btnAddSensor.addActionListener(e -> {
            SelectSensorsDialog selectSensorsDialog = new SelectSensorsDialog(lstIncludedSensorIds);
            List<String> selectedSensors = selectSensorsDialog.showDialog(parent);
            if (selectedSensors != null && !selectedSensors.isEmpty()) {
                if (lstIncludedSensorIds == null) {
                    lstIncludedSensorIds = new ArrayList<>(selectedSensors);
                    loadSensors(lstIncludedSensorIds, true);
                    lblNoSensors.setVisible(false);
                    jspSensors.setVisible(true);
                } else {
                    lstIncludedSensorIds.addAll(selectedSensors);
                    loadSensors(selectedSensors, false);
                }

                // save sensor ids to file
                saveSensors(selectedSensors);
            }
        });

        btnAddTarget.addActionListener(e -> {
            TargetPropertiesDialog targetPropertiesDialog = new TargetPropertiesDialog();
            Target target = targetPropertiesDialog.showDialog(parent, originalTarget);
            if (target != null) {
                this.addTarget(target);
                this.setTargetsTableUI();
            }
        });

        jtblSensors.addMouseListener(new MouseAdapter() {
            @Override
            public void mousePressed(MouseEvent e) {
                int row = jtblSensors.rowAtPoint(e.getPoint());
                int col = jtblSensors.columnAtPoint(e.getPoint());
                int colsCount = sensorsTableModel.getColumnCount();

                if (row >= 0 && col == colsCount - 1) { // ستون آخر: دکمه حذف
                    jtblSensors.clearSelection();
                    e.consume();
                } else {
                    super.mousePressed(e);
                }
            }

            @Override
            public void mouseClicked(MouseEvent e) {
                int row = jtblSensors.rowAtPoint(e.getPoint());
                int col = jtblSensors.columnAtPoint(e.getPoint());

                if (col == sensorsTableModel.getColumnCount() - 1) { // ستون حذف
                    int confirm = JOptionPane.showConfirmDialog(jtblSensors, "Do you want to delete this sensor?", "Confirm", JOptionPane.YES_NO_OPTION);
                    if (confirm == JOptionPane.YES_OPTION) {
                        // حذف از مدل
                        String deletedSensorId = lstIncludedSensorIds.get(row);
                        lstIncludedSensorIds.remove(row);
                        sensorsTableModel.removeRow(row);
                        // حذف از فایل
                        removeSensorIdFromConfig(deletedSensorId);
                    }
                }
            }
        });

        jtblTargets.addMouseListener(new MouseAdapter() {
            @Override
            public void mousePressed(MouseEvent e) {
                int row = jtblTargets.rowAtPoint(e.getPoint());
                int col = jtblTargets.columnAtPoint(e.getPoint());
                int colsCount = targetsTableModel.getColumnCount();

                if (row >= 0 && col >= colsCount - 2) {
                    jtblTargets.clearSelection();
                    e.consume();
                } else {
                    super.mousePressed(e);
                }
            }

            @Override
            public void mouseClicked(MouseEvent e) {
                int row = jtblTargets.rowAtPoint(e.getPoint());
                int col = jtblTargets.columnAtPoint(e.getPoint());
                int colsCount = targetsTableModel.getColumnCount();

                if (row >= 0 && col >= colsCount - 2) {
                    if (col == colsCount - 2) { // edit target
                        // create the target instance from row data
                        Target target = getTarget(row);

                        // show target properties dialog with filled values
                        TargetPropertiesDialog targetPropertiesDialog = new TargetPropertiesDialog();
                        Target editedTarget = targetPropertiesDialog.showDialog(parent, target);

                        if (editedTarget != null) {
                            // به‌روزرسانی مقادیر سطر جدول با مقادیر جدید
                            jtblTargets.setValueAt(editedTarget.getId(), row, 0);

                            // نمایش مسیر جدید به صورت لیست نقاط در ستون دوم
                            StringBuilder routeBuilder = new StringBuilder();
                            for (ir.hakim.classes.Point p : editedTarget.getPath()) {
                                routeBuilder.append("(")
                                        .append(p.getLatitude())
                                        .append(", ")
                                        .append(p.getLongitude())
                                        .append(")\n");
                            }
                            jtblTargets.setValueAt(routeBuilder.toString().trim(), row, 1); // ستون Route

                            jtblTargets.setValueAt(editedTarget.getSpeed(), row, 2);

                            StringBuilder sensorNamesText = new StringBuilder();
                            for (String s : editedTarget.getRelatedSensors()) {
                                sensorNamesText.append(s).append("\n");
                            }
                            jtblTargets.setValueAt(sensorNamesText.toString().trim(), row, 3);
                        }

                    } else if (col == colsCount - 1) { // delete target
                        // create the target instance from row data
                        Target target = getTarget(row);
                        int confirm = JOptionPane.showConfirmDialog(parent, String.format("Do you want to remove target with id %s ?", target.getId()),
                                "Are you sure to delete?", JOptionPane.YES_NO_OPTION);
                        if (confirm == JOptionPane.YES_OPTION) {
                            // delete from db
                            HashMap<String, Object> filters = new HashMap<>();
                            filters.put("uid", target.getUid());
                            DBHelper.deleteDoc("targets", filters);

                            // delete from view
                            targetsTableModel.removeRow(row);
                        }
                    }
                }
            }
        });

        // loading previously added sensors (from file)
        List<String> lstIds = loadSensorIds();
        if (lstIds == null || lstIds.isEmpty()) {
            // if any sensors was not added before, show no sensors label
            jspSensors.setVisible(false);
            lblNoSensors.setText("There is no sensors included for simulation!");
            lblNoSensors.setVisible(true);
        } else {
            lstIncludedSensorIds = lstIds;
            // load saved ids related sensors from db (in background)
            new SwingWorker<Void, Void>() {
                @Override
                protected Void doInBackground() {
                    SwingUtilities.invokeLater(() -> {
                        jspSensors.setVisible(false);
                        lblNoSensors.setText("Loading...");
                        lblNoSensors.setVisible(true);
                    });
                    
                    loadSensors(lstIncludedSensorIds, true);
                    
                    SwingUtilities.invokeLater(() -> {
                        lblNoSensors.setVisible(false);
                        jspSensors.setVisible(true);
                    });
                    return null;
                }
            }.execute();
        }

        // load targets from db (in background)
        new SwingWorker<Void, Void>() {
            @Override
            protected Void doInBackground() {
                SwingUtilities.invokeLater(() -> {
                    jspTargets.setVisible(false);
                    lblNoTargets.setText("Loading...");
                    lblNoTargets.setVisible(true);
                });
                
                loadTargets();
                
                SwingUtilities.invokeLater(() -> {
                    setTargetsTableUI();
                });
                return null;
            }
        }.execute();

        btnStart.addActionListener(e -> {
            // lock the start button
            this.lockStart(true);

            // initialize list of running targets
            runningTargets = new ArrayList<>();

            // create a simulator for each sensor (depend on its sensor type) and create a map for each sensor name and its simulator
            HashMap<String, Simulator> simulators = new HashMap<>();

            // for each target
            for (int row = 0; row < targetsTableModel.getRowCount(); row++) {
                try {
                    // create the target instance
                    Target target = getTarget(row);

                    // get list of related sensorNames
                    List<String> relatedSensorNames = target.getRelatedSensors();
                    
                    if (relatedSensorNames == null || relatedSensorNames.isEmpty()) {
                        txtLogs.insert(String.format("Target %d has no related sensors. Skipping...%n", target.getId()), 0);
                        continue;
                    }

                    // for each sensor
                    Vector sensorsVector = sensorsTableModel.getDataVector();
                    for (String sensorName : relatedSensorNames) {
                        // find sensor row data
                        List<Vector> found = (List<Vector>) sensorsVector.stream().filter(r ->
                                ((Vector) r).elementAt(0).equals(sensorName)).collect(Collectors.toList());
                        
                        if (found.isEmpty()) {
                            txtLogs.insert(String.format("Sensor %s not found in sensors table. Skipping...%n", sensorName), 0);
                            continue;
                        }
                        
                        // get sensor type and other properties
                        Vector sensorRow = found.get(0);
                        String sensorType = sensorRow.elementAt(1).toString();
                        String ip = sensorRow.elementAt(2).toString();
                        int port = Integer.parseInt(sensorRow.elementAt(3).toString());
                        double sensorLat = Double.parseDouble(sensorRow.elementAt(4).toString());
                        double sensorLon = Double.parseDouble(sensorRow.elementAt(5).toString());
                        
                        try {
                            // create a simulator appropriate to sensor
                            // if sensor's simulator is not added before
                            if (!simulators.containsKey(sensorName)) {
                                Simulator simulator = (Simulator) Class.forName("ir.hakim.Simulators." + sensorType + "_Simulator").newInstance();
                                simulator.setSensorName(sensorName);
                                simulator.setIp(ip);
                                simulator.setPort(port);
                                simulator.setSensorLat(sensorLat);
                                simulator.setSensorLon(sensorLon);
                                simulator.setLogger(txtLogs);
                                simulators.put(sensorName, simulator);
                            }
                        } catch (InstantiationException | IllegalAccessException | ClassNotFoundException ex) {
                            txtLogs.insert(String.format("Error creating simulator for sensor %s: %s%n", sensorName, ex.getMessage()), 0);
                            ex.printStackTrace();
                            // release the start button
                            this.lockStart(false);
                            return;
                        }
                    }

                    // set the simulators map for target
                    target.setSimulators(simulators);

                    // set target logger
                    target.setLogger(txtLogs);

                    // run the target
                    target.run();

                    // catch running targets for probable stop
                    runningTargets.add(target);
                } catch (Exception ex) {
                    txtLogs.insert(String.format("Error processing target at row %d: %s%n", row, ex.getMessage()), 0);
                    ex.printStackTrace();
                }
            }
            
            if (runningTargets.isEmpty()) {
                txtLogs.insert("No targets were started. Please check your configuration.%n", 0);
                this.lockStart(false);
            }
        });

        btnStop.addActionListener(e -> {
            // stop all running targets
            if (runningTargets != null) {
                for (Target t : runningTargets) {
                    try {
                        t.stop();
                    } catch (Exception ex) {
                        txtLogs.insert(String.format("Error stopping target %d: %s%n", t.getId(), ex.getMessage()), 0);
                    }
                }
            }

            // release the start button
            this.lockStart(false);
        });

        btnClear.addActionListener(e -> {
            txtLogs.setText(null);
        });
    }
    


    public static void main(String[] args, Target originalTarget) {
        SwingUtilities.invokeLater(() -> {
            JFrame frame = new JFrame("Target Based Simulator");
            frame.setContentPane(new MainForm(frame, originalTarget).pnlMain);

            frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
            frame.pack();

            // makes center form
            final Toolkit toolkit = Toolkit.getDefaultToolkit();
            final Dimension screenSize = toolkit.getScreenSize();
            final int x = (screenSize.width - frame.getWidth()) / 2;
            final int y = (screenSize.height - frame.getHeight()) / 2;
            frame.setLocation(x, y);

            try {
                UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());
                updateComponentTreeUI(frame);
            } catch (ClassNotFoundException | InstantiationException | IllegalAccessException |
                     UnsupportedLookAndFeelException e) {
                throw new RuntimeException(e);
            }
            
            frame.setVisible(true);
        });
    }

    private void configUIComponents() {
        // style the form
        String fontNameSegoeUI = "Segoe UI";

        // ==================== Sensors ====================
        lblSensors.setFont(new Font(fontNameSegoeUI, Font.PLAIN, 36));
        lblIncludedSensors.setFont(new Font(fontNameSegoeUI, Font.BOLD, 18));
        jspSensors.setVisible(false);
        sensorsTableModel.setIconColumnsCount(1);

        // ==================== Targets ====================
        lblTargets.setFont(new Font(fontNameSegoeUI, Font.PLAIN, 36));
        lblDefinedTargets.setFont(new Font(fontNameSegoeUI, Font.BOLD, 18));
        jspTargets.setVisible(false);
        targetsTableModel.setIconColumnsCount(2);

        // ==================== Simulation ====================
        lblSimulation.setFont(new Font(fontNameSegoeUI, Font.PLAIN, 36));
        txtLogs.setFont(new Font("Courier New", Font.PLAIN, 12));

        // select Targets tab
        tabs.setSelectedIndex(1);
    }

    private void lockStart(boolean lock) {
        this.btnStart.setEnabled(!lock);
        this.btnStop.setEnabled(lock);
    }

    private void setTargetsTableUI() {
        boolean targetsAvailable = targetsTableModel.getRowCount() > 0;
        if (targetsAvailable) {
            lblNoTargets.setVisible(false);
            jspTargets.setVisible(true);

            int colsCount = jtblTargets.getColumnCount();
            if (colsCount >= 2) {
                jtblTargets.getColumnModel().getColumn(colsCount - 1).setMaxWidth(28);
                jtblTargets.getColumnModel().getColumn(colsCount - 2).setMaxWidth(28);
            }
        } else {
            jspTargets.setVisible(false);
            lblNoTargets.setText("There is no targets defined for simulation!");
            lblNoTargets.setVisible(true);
        }
    }

    private List<String> loadSensorIds() {
        List<String> lstIds = null;
        try {
            File f = new File(configFileName);
            if (f.exists())
                lstIds = Files.readAllLines(Paths.get(configFileName));
        } catch (IOException e) {
            e.printStackTrace();
        }
        return lstIds;
    }

    private void saveSensors(List<String> ids) {
        try (BufferedWriter writer = new BufferedWriter(new FileWriter(configFileName, true))) {
            for (String id : ids) {
                writer.append(id);
                writer.append("\r\n");
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private void loadSensors(List<String> sensorIdsToLoad, boolean isFirstLoad) {
        try {
            List<Document> docsList = DBHelper.loadThisSensors(sensorIdsToLoad);

            if (docsList == null || docsList.isEmpty()) {
                SwingUtilities.invokeLater(() -> {
                    sensorsTableModel.setRowCount(0);
                });
                return;
            }

            ArrayList<String> lstHeaders = new ArrayList<>(Arrays.asList(Constants.sensorsColumnHeaders));
            lstHeaders.remove(0);
            lstHeaders.add("");

            Vector<String> columnNames = new Vector<>(lstHeaders);

            ArrayList<String> lstColumns = new ArrayList<>(Arrays.asList(Constants.sensorsColumnNames));
            int columnCount = lstColumns.size();

            SwingUtilities.invokeLater(() -> {
                if (isFirstLoad) {
                    Vector<Vector<Object>> data = new Vector<>();

                    for (Document doc : docsList) {
                        Vector<Object> row = new Vector<>();
                        for (int i = 1; i < columnCount; i++) {
                            row.add(doc.get(lstColumns.get(i)));
                        }

                        row.add(deleteIcon);
                        data.add(row);
                    }

                    sensorsTableModel.setDataVector(data, columnNames);
                } else {
                    for (Document doc : docsList) {
                        Vector<Object> row = new Vector<>();
                        for (int i = 1; i < columnCount; i++) {
                            row.add(doc.get(lstColumns.get(i)));
                        }

                        row.add(deleteIcon);
                        sensorsTableModel.addRow(row);
                    }
                }

                int deleteColIndex = sensorsTableModel.getColumnCount() - 1;
                if (deleteColIndex >= 0) {
                    jtblSensors.getColumnModel().getColumn(deleteColIndex).setMaxWidth(28);
                    jtblSensors.getColumnModel().getColumn(deleteColIndex).setMinWidth(28);
                }
            });
        } catch (Exception ex) {
            ex.printStackTrace();
            SwingUtilities.invokeLater(() -> {
                txtLogs.insert(String.format("Error loading sensors: %s%n", ex.getMessage()), 0);
            });
        }
    }

    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> {
            JFrame frame = new JFrame("Main Form");
            MainForm mainForm = new MainForm(frame, null);

            mainForm.pnlMain.setFocusable(false);

            frame.setContentPane(mainForm.pnlMain);
            frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
            frame.pack();
            frame.setLocationRelativeTo(null);

            try {
                UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());
                updateComponentTreeUI(frame);
            } catch (Exception e) {
                e.printStackTrace();
            }

            frame.setVisible(true);
        });
    }

    private void loadTargets() {
        try {
            List<Document> docsList = DBHelper.loadCollection(Constants.TARGETS_COLLECTION_NAME);

            if (docsList == null || docsList.isEmpty()) {
                return;
            }

            Vector<String> columnHeaders = new Vector<>(Arrays.asList(Constants.targetsColumnHeaders));
            columnHeaders.add(""); // ویرایش
            columnHeaders.add(""); // حذف

            Vector<Vector<Object>> rows = new Vector<>();
            List<String> ids = new ArrayList<>();
            ObjectMapper mapper = new ObjectMapper();

            for (Document doc : docsList) {
                try {
                    Target target = mapper.readValue(doc.toJson(), Target.class);
                    target.setUid(doc.get("uid").toString()); // اطمینان از اینکه UID تنظیم شده

                    Vector<Object> row = new Vector<>();
                    row.add(target.getId()); // شناسه

                    // مسیر Route (Path)
                    StringBuilder pathText = new StringBuilder();
                    List<Point> path = (List<Point>) target.getPath();
                    if (path != null) {
                        for (Point p : path) {
                            if (p != null) {
                                pathText.append("(")
                                        .append(p.getLatitude())
                                        .append(", ")
                                        .append(p.getLongitude())
                                        .append(")\n");
                            }
                        }
                    }
                    row.add(pathText.toString().trim()); // ستون Route

                    // سرعت
                    row.add(target.getSpeed()); // ستون Speed

                    // سنسورهای مرتبط
                    StringBuilder sensorNamesText = new StringBuilder();
                    List<String> relatedSensors = target.getRelatedSensors();
                    if (relatedSensors != null) {
                        for (String name : relatedSensors) {
                            if (name != null && !name.trim().isEmpty()) {
                                sensorNamesText.append(name).append("\n");
                            }
                        }
                    }
                    row.add(sensorNamesText.toString().trim()); // ستون Related Sensors

                    // آیکون‌ها
                    row.add(editIcon);   // ستون ویرایش
                    row.add(deleteIcon); // ستون حذف

                    rows.add(row);
                    ids.add(target.getUid());
                } catch (JsonProcessingException e) {
                    e.printStackTrace();
                } catch (Exception ex) {
                    ex.printStackTrace(); // برای گرفتن خطاهای کلی مثل NullPointer
                }
            }

            SwingUtilities.invokeLater(() -> {
                targetsTableModel.setDataVector(rows, columnHeaders);
                targetsTableModel.setIds(ids);
            });
        } catch (Exception ex) {
            ex.printStackTrace();
            if (txtLogs != null) {
                SwingUtilities.invokeLater(() -> {
                    txtLogs.insert(String.format("Error loading targets: %s%n", ex.getMessage()), 0);
                });
            }
        }
    }

    private void addTarget(Target target) {
        try {
            Vector<Object> row = new Vector<>();

            row.add(target.getId());

            String route = "";
            List<Point> path = (List<Point>) target.getPath();
            if (path != null && !path.isEmpty()) {
                route = path.stream()
                        .filter(Objects::nonNull)
                        .map(p -> String.format("(%.6f, %.6f)", p.getLatitude(), p.getLongitude()))
                        .collect(Collectors.joining("\n"));
            }
            row.add(route);

            row.add(target.getSpeed());
            
            String sensorsString = "";
            if (target.getRelatedSensors() != null) {
                sensorsString = target.getRelatedSensors().stream()
                        .filter(Objects::nonNull)
                        .filter(s -> !s.trim().isEmpty())
                        .collect(Collectors.joining("\n"));
            }
            row.add(sensorsString);

            row.add(editIcon);
            row.add(deleteIcon);

            targetsTableModel.addRow(row);
            targetsTableModel.addId(target.getUid());
        } catch (Exception ex) {
            ex.printStackTrace();
            if (txtLogs != null) {
                txtLogs.insert(String.format("Error adding target: %s%n", ex.getMessage()), 0);
            }
        }
    }

private Target getTarget(int row) {
    Target target = new Target();

    // ID
    Object idObj = jtblTargets.getValueAt(row, 0);
    if (idObj != null) {
        target.setId(Integer.parseInt(idObj.toString().trim()));
    }

    // مسیر (Route)
    Object routeObj = jtblTargets.getValueAt(row, 1);
    if (routeObj != null) {
        String routeStr = routeObj.toString().trim();
        List<ir.hakim.classes.Point> path = new ArrayList<>();
        String[] lines = routeStr.split("\n");
        for (String line : lines) {
            line = line.replace("(", "").replace(")", "").trim();
            String[] parts = line.split(",");
            if (parts.length == 2) {
                try {
                    double lat = Double.parseDouble(parts[0].trim());
                    double lon = Double.parseDouble(parts[1].trim());
                    path.add(new ir.hakim.classes.Point(lat, lon));
                } catch (NumberFormatException e) {
                    e.printStackTrace(); // skip invalid line
                }
            }
        }
        target.setPath(path);
    }

    // سرعت (Speed)
    Object speedObj = jtblTargets.getValueAt(row, 2);
    if (speedObj != null) {
        try {
            target.setSpeed(Double.parseDouble(speedObj.toString().trim()));
        } catch (NumberFormatException e) {
            target.setSpeed(0); // پیش‌فرض
        }
    }

    // سنسورها (RelatedSensors)
    Object sensorsObj = jtblTargets.getValueAt(row, 3);
    if (sensorsObj != null) {
        String[] sensorLines = sensorsObj.toString().split("\n");
        List<String> sensorIds = new ArrayList<>();
        for (String s : sensorLines) {
            if (!s.trim().isEmpty()) {
                sensorIds.add(s.trim());
            }
        }
        target.setRelatedSensors(sensorIds);
        target.setRelatedSensorsString(String.join("\n", sensorIds));
    } else {
        target.setRelatedSensors(new ArrayList<>());
        target.setRelatedSensorsString("");
    }

    // UID
    target.setUid(targetsTableModel.getId(row));

    return target;
}
}