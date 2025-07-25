package ir.hakim.forms;

import com.fasterxml.jackson.databind.ObjectMapper;
import ir.hakim.classes.Constants;
import ir.hakim.classes.DBHelper;
import ir.hakim.classes.Point;
import ir.hakim.classes.Target;
import ir.hakim.classes.Utils;

import javax.swing.*;
import javax.swing.border.Border;
import java.awt.*;
import java.awt.event.*;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

public class TargetPropertiesDialog extends JDialog {
    private JPanel contentPane;
    private JButton buttonOK;
    private JButton buttonCancel;
    private JTextField txtId;
    private JList jlstSensors;
    private JTextField txtStartLat;
    private JButton selectSensorsButton;
    private JTextField txtStartLon;
    private JTextField txtEndLat;
    private JTextField txtEndLon;
    private JSpinner spSpeed;
    private JButton addPoint;
    private JTextArea textAreaPoints;
    private Target target;
    private Target origTarget;

    private boolean isForEdit;

    private final DefaultListModel<String> sensorsListModel = new DefaultListModel<>();
    private final List<Point> pathPoints = new ArrayList<>();

    public TargetPropertiesDialog() {
        setTitle("Target Properties");
        setContentPane(contentPane);
        setModal(true);
        setResizable(false);
        getRootPane().setDefaultButton(buttonOK);
        
        try {
            jlstSensors.setModel(sensorsListModel);
            spSpeed.setModel(new SpinnerNumberModel(0.0, 0.0, 12000.0, 0.1));

            addPoint.addActionListener(e -> addPoint());

            selectSensorsButton.addActionListener(e -> {
                try {
                    List<String> selectedBefore = new ArrayList<>();
                    for (int i = 0; i < sensorsListModel.size(); i++) {
                        selectedBefore.add(sensorsListModel.get(i));
                    }

                    SelectSensorsDialog dialog = new SelectSensorsDialog(selectedBefore, true);
                    List<String> selected = dialog.showDialog(this);

                    if (selected != null) {
                        sensorsListModel.clear();
                        for (String sensor : selected) {
                            if (sensor != null && !sensor.trim().isEmpty()) {
                                sensorsListModel.addElement(sensor);
                            }
                        }
                    }
                } catch (Exception ex) {
                    ex.printStackTrace();
                    JOptionPane.showMessageDialog(this, "Error selecting sensors: " + ex.getMessage(), "Error", JOptionPane.ERROR_MESSAGE);
                }
            });

            buttonOK.addActionListener(e -> onOK());
            buttonCancel.addActionListener(e -> onCancel());

            setDefaultCloseOperation(DO_NOTHING_ON_CLOSE);
            addWindowListener(new WindowAdapter() {
                public void windowClosing(WindowEvent e) {
                    onCancel();
                }
            });

            contentPane.registerKeyboardAction(e -> onCancel(),
                    KeyStroke.getKeyStroke(KeyEvent.VK_ESCAPE, 0),
                    JComponent.WHEN_ANCESTOR_OF_FOCUSED_COMPONENT);
        } catch (Exception ex) {
            ex.printStackTrace();
            JOptionPane.showMessageDialog(this, "Error initializing dialog: " + ex.getMessage(), "Initialization Error", JOptionPane.ERROR_MESSAGE);
        }
    }

    private void addPoint() {
        try {
            String latText = txtEndLat.getText().trim();
            String lonText = txtEndLon.getText().trim();
            
            if (latText.isEmpty() || lonText.isEmpty()) {
                JOptionPane.showMessageDialog(this, "Please enter both latitude and longitude values.", "Input Required", JOptionPane.WARNING_MESSAGE);
                return;
            }

            double lat = Double.parseDouble(latText);
            double lon = Double.parseDouble(lonText);
            
            // Basic validation for latitude and longitude ranges
            if (lat < -90 || lat > 90) {
                JOptionPane.showMessageDialog(this, "Latitude must be between -90 and 90 degrees.", "Invalid Latitude", JOptionPane.ERROR_MESSAGE);
                return;
            }
            
            if (lon < -180 || lon > 180) {
                JOptionPane.showMessageDialog(this, "Longitude must be between -180 and 180 degrees.", "Invalid Longitude", JOptionPane.ERROR_MESSAGE);
                return;
            }

            Point point = new Point(lat, lon);
            pathPoints.add(point);
            updatePointList();

            txtEndLat.setText("");
            txtEndLon.setText("");
            txtEndLat.requestFocus();

        } catch (NumberFormatException e) {
            JOptionPane.showMessageDialog(this, "Latitude and Longitude must be valid numbers.", "Invalid Input", JOptionPane.ERROR_MESSAGE);
        } catch (Exception e) {
            e.printStackTrace();
            JOptionPane.showMessageDialog(this, "Error adding point: " + e.getMessage(), "Error", JOptionPane.ERROR_MESSAGE);
        }
    }

    private void updatePointList() {
        try {
            StringBuilder sb = new StringBuilder();
            for (int i = 0; i < pathPoints.size(); i++) {
                Point p = pathPoints.get(i);
                if (p != null) {
                    sb.append(String.format("%d. Lat: %.6f, Lon: %.6f%n", i + 1, p.getLatitude(), p.getLongitude()));
                }
            }
            textAreaPoints.setText(sb.toString());
            textAreaPoints.setCaretPosition(0); // Scroll to top
        } catch (Exception ex) {
            ex.printStackTrace();
        }
    }

    public Target showDialog(Component parent, Target originalTarget) {
        try {
            this.origTarget = originalTarget;
            this.isForEdit = originalTarget != null;

            if (isForEdit) {
                txtId.setText(String.valueOf(originalTarget.getId()));
                spSpeed.setValue(originalTarget.getSpeed());

                // Load related sensors
                sensorsListModel.clear();
                List<String> relatedSensors = originalTarget.getRelatedSensors();
                if (relatedSensors != null) {
                    for (String s : relatedSensors) {
                        if (s != null && !s.trim().isEmpty()) {
                            sensorsListModel.addElement(s);
                        }
                    }
                }

                // Load path points
                pathPoints.clear();
                if (originalTarget.getPath() != null) {
                    for (Point p : (List<Point>) originalTarget.getPath()) {
                        if (p != null) {
                            pathPoints.add(new Point(p.getLatitude(), p.getLongitude()));
                        }
                    }
                }
                updatePointList();

                txtId.setEditable(false);
            } else {
                // Clear all fields for new target
                txtId.setText("");
                txtId.setEditable(true);
                spSpeed.setValue(0.0);
                sensorsListModel.clear();
                pathPoints.clear();
                textAreaPoints.setText("");
                txtEndLat.setText("");
                txtEndLon.setText("");
            }

            pack();
            setLocationRelativeTo(parent);
            setVisible(true);

            return target;
        } catch (Exception ex) {
            ex.printStackTrace();
            JOptionPane.showMessageDialog(parent, "Error showing dialog: " + ex.getMessage(), "Error", JOptionPane.ERROR_MESSAGE);
            return null;
        }
    }

    private void onOK() {
        try {
            // Validate input
            String idText = txtId.getText().trim();
            if (idText.isEmpty()) {
                JOptionPane.showMessageDialog(this, "Please enter a target ID.", "Input Required", JOptionPane.WARNING_MESSAGE);
                txtId.requestFocus();
                return;
            }

            int id;
            try {
                id = Integer.parseInt(idText);
                if (id < 0) {
                    JOptionPane.showMessageDialog(this, "Target ID must be a positive number.", "Invalid ID", JOptionPane.ERROR_MESSAGE);
                    txtId.requestFocus();
                    return;
                }
            } catch (NumberFormatException e) {
                JOptionPane.showMessageDialog(this, "Target ID must be a valid number.", "Invalid ID", JOptionPane.ERROR_MESSAGE);
                txtId.requestFocus();
                return;
            }

            double speed;
            try {
                speed = Double.parseDouble(spSpeed.getValue().toString());
                if (speed < 0) {
                    JOptionPane.showMessageDialog(this, "Speed must be a positive number.", "Invalid Speed", JOptionPane.ERROR_MESSAGE);
                    spSpeed.requestFocus();
                    return;
                }
            } catch (NumberFormatException e) {
                JOptionPane.showMessageDialog(this, "Speed must be a valid number.", "Invalid Speed", JOptionPane.ERROR_MESSAGE);
                spSpeed.requestFocus();
                return;
            }

            ArrayList<String> selectedSensors = new ArrayList<>();
            for (int i = 0; i < sensorsListModel.size(); i++) {
                String sensor = sensorsListModel.get(i);
                if (sensor != null && !sensor.trim().isEmpty()) {
                    selectedSensors.add(sensor);
                }
            }

            if (selectedSensors.isEmpty()) {
                JOptionPane.showMessageDialog(this, "Please select at least one sensor.", "No Sensors Selected", JOptionPane.WARNING_MESSAGE);
                return;
            }

            if (pathPoints.isEmpty()) {
                JOptionPane.showMessageDialog(this, "Please add at least one point to the path.", "No Path Points", JOptionPane.WARNING_MESSAGE);
                return;
            }

            if (pathPoints.size() < 2) {
                JOptionPane.showMessageDialog(this, "Please add at least two points to create a valid path.", "Insufficient Path Points", JOptionPane.WARNING_MESSAGE);
                return;
            }

            // Create target
            target = new Target(id, new ArrayList<>(pathPoints), speed, selectedSensors);

            if (origTarget != null) {
                target.setUid(origTarget.getUid());
            }

            // Save to database
            ObjectMapper mapper = new ObjectMapper();
            String json = mapper.writeValueAsString(target);

            if (isForEdit) {
                HashMap<String, Object> filters = new HashMap<>();
                filters.put("uid", target.getUid());
                DBHelper.updateDoc(Constants.TARGETS_COLLECTION_NAME, json, filters);
            } else {
                DBHelper.saveDoc(Constants.TARGETS_COLLECTION_NAME, json);
            }

            dispose();
        } catch (Exception ex) {
            ex.printStackTrace();
            JOptionPane.showMessageDialog(this, "Error saving target: " + ex.getMessage(), "Save Error", JOptionPane.ERROR_MESSAGE);
        }
    }

    private void onCancel() {
        target = null;
        dispose();
    }
}