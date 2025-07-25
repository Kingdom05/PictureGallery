package ir.hakim.forms;

import ir.hakim.classes.Constants;
import ir.hakim.classes.DBHelper;
import org.bson.Document;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import javax.swing.table.DefaultTableModel;
import javax.swing.table.TableCellRenderer;
import java.awt.*;
import java.awt.event.*;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Vector;

public class SelectSensorsDialog extends JDialog {
    private boolean isForSelect;
    List<String> lstSelectedSensorIDs = new ArrayList<>();
    List<String> lstSelectedSensorNames = new ArrayList<>();
    private JPanel contentPane;
    private JButton buttonOK;
    private JButton buttonCancel;
    private JTable jTableSensors;
    private JLabel lblTitle;
    SelectableTableModel tableModel;

    public SelectSensorsDialog(List<String> lstIncludedSensorIds) {
        this(lstIncludedSensorIds, null, false);
    }

    public SelectSensorsDialog(List<String> lstSelectedSensorNames, boolean isForSelect) {
        this(null, lstSelectedSensorNames, isForSelect);
        if (isForSelect)
            lblTitle.setText("Select which sensors you want to influence.");
    }

    public SelectSensorsDialog(List<String> lstIncludedSensorIds, List<String> lstSelectedSensorNames, boolean isForSelect) {
        this.lstSelectedSensorNames = lstSelectedSensorNames != null ? new ArrayList<>(lstSelectedSensorNames) : new ArrayList<>();
        this.isForSelect = isForSelect;

        setTitle("Select Sensors");
        setContentPane(contentPane);
        setModal(true);
        getRootPane().setDefaultButton(buttonOK);

        try {
            tableModel = loadSensors(lstIncludedSensorIds);
            jTableSensors.setModel(tableModel);

            // check selected rows before
            if (isForSelect && lstSelectedSensorNames != null && !lstSelectedSensorNames.isEmpty()) {
                for (int i = 0; i < tableModel.getRowCount(); i++) {
                    Object nameValue = tableModel.getValueAt(i, 1);
                    if (nameValue != null && lstSelectedSensorNames.contains(nameValue.toString())) {
                        tableModel.setValueAt(true, i, 0);
                    }
                }
            }

            jTableSensors.getColumnModel().getColumn(0).setCellRenderer(new CheckBoxRenderer());
            jTableSensors.getColumnModel().getColumn(0).setCellEditor(new CheckBoxEditor(new JCheckBox()));
            
            jTableSensors.addMouseListener(new MouseAdapter() {
                @Override
                public void mouseClicked(MouseEvent e) {
                    int row = jTableSensors.rowAtPoint(e.getPoint());
                    int col = jTableSensors.columnAtPoint(e.getPoint());

                    if (row >= 0 && col == 0) { // Checkbox column
                        boolean currentValue = (boolean) tableModel.getValueAt(row, 0);
                        tableModel.setValueAt(!currentValue, row, 0); // Toggle the checkbox state
                        updateOKButtonState();
                    }
                }
            });
            
            jTableSensors.getColumnModel().getColumn(0).setMaxWidth(28);
            jTableSensors.getColumnModel().getColumn(0).setMinWidth(28);

            buttonOK.addActionListener(e -> onOK());
            buttonCancel.addActionListener(e -> onCancel());

            // call onCancel() when cross is clicked
            setDefaultCloseOperation(DO_NOTHING_ON_CLOSE);
            addWindowListener(new WindowAdapter() {
                public void windowClosing(WindowEvent e) {
                    onCancel();
                }
            });

            // call onCancel() on ESCAPE
            contentPane.registerKeyboardAction(e -> onCancel(), 
                KeyStroke.getKeyStroke(KeyEvent.VK_ESCAPE, 0), 
                JComponent.WHEN_ANCESTOR_OF_FOCUSED_COMPONENT);
                
            updateOKButtonState();
            
        } catch (Exception ex) {
            ex.printStackTrace();
            JOptionPane.showMessageDialog(this, "Error loading sensors: " + ex.getMessage(), "Error", JOptionPane.ERROR_MESSAGE);
        }
    }

    private void updateOKButtonState() {
        boolean hasSelection = false;
        if (tableModel != null) {
            for (int i = 0; i < tableModel.getRowCount(); i++) {
                Boolean selected = (Boolean) tableModel.getValueAt(i, 0);
                if (selected != null && selected) {
                    hasSelection = true;
                    break;
                }
            }
        }
        buttonOK.setEnabled(hasSelection);
    }

    public List<String> showDialog(Component parent) {
        setMinimumSize(new Dimension(640, 480));
        pack();
        setLocationRelativeTo(parent);
        setVisible(true);
        if (isForSelect)
            return lstSelectedSensorNames;
        else
            return lstSelectedSensorIDs;
    }

    private void onOK() {
        try {
            if (isForSelect) {
                lstSelectedSensorNames = new ArrayList<>();
                for (int row = 0; row < tableModel.getRowCount(); row++) {
                    Boolean selected = (Boolean) tableModel.getValueAt(row, 0);
                    if (selected != null && selected) {
                        Object nameValue = tableModel.getValueAt(row, 1);
                        if (nameValue != null) {
                            lstSelectedSensorNames.add(nameValue.toString());
                        }
                    }
                }
            } else {
                lstSelectedSensorIDs = new ArrayList<>();
                for (int row = 0; row < tableModel.getRowCount(); row++) {
                    Boolean selected = (Boolean) tableModel.getValueAt(row, 0);
                    if (selected != null && selected) {
                        String id = tableModel.getRowId(row);
                        if (id != null) {
                            lstSelectedSensorIDs.add(id);
                        }
                    }
                }
            }
            setVisible(false);
            dispose();
        } catch (Exception ex) {
            ex.printStackTrace();
            JOptionPane.showMessageDialog(this, "Error processing selection: " + ex.getMessage(), "Error", JOptionPane.ERROR_MESSAGE);
        }
    }

    private void onCancel() {
        lstSelectedSensorIDs.clear();
        lstSelectedSensorNames.clear();
        setVisible(false);
        dispose();
    }

    private @NotNull SelectableTableModel loadSensors(List<String> prevIncludedSensorIds) {
        try {
            List<Document> docsList = DBHelper.loadOtherSensors(prevIncludedSensorIds);

            if (docsList == null) {
                docsList = new ArrayList<>();
            }

            // Column headers
            ArrayList<String> lstColumns = new ArrayList<>(Arrays.asList(Constants.sensorsColumnNames));
            int columnCount = lstColumns.size();

            // list of Ids
            List<String> ids = new ArrayList<>(docsList.size());

            // Data for the table
            Vector<Vector<Object>> data = new Vector<>();
            for (Document doc : docsList) {
                Vector<Object> vector = new Vector<>();

                // add false for unchecked row status at first column
                vector.add(false);

                // start from index 1 (to exclude IDs column data)
                for (int columnIndex = 1; columnIndex < columnCount; columnIndex++) {
                    Object value = doc.get(lstColumns.get(columnIndex));
                    vector.add(value != null ? value : "");
                }

                // hold ID of record
                Object idValue = doc.get(lstColumns.get(0));
                ids.add(idValue != null ? idValue.toString() : "");

                data.add(vector);
            }

            // headers
            ArrayList<String> lstHeaders = new ArrayList<>(Arrays.asList(Constants.sensorsColumnHeaders));
            lstHeaders.remove(0); // remove 'ID' column from headers
            lstHeaders.add(0, ""); // set first column (check) title to empty string
            Vector<String> columnNames = new Vector<>(lstHeaders);

            SelectableTableModel tableModel = new SelectableTableModel(data.size(), columnNames.size(), ids, jTableSensors);
            tableModel.setDataVector(data, columnNames);
            return tableModel;
        } catch (Exception ex) {
            ex.printStackTrace();
            JOptionPane.showMessageDialog(this, "Error loading sensors from database: " + ex.getMessage(), "Database Error", JOptionPane.ERROR_MESSAGE);
            // Return empty table model
            Vector<String> columnNames = new Vector<>();
            columnNames.add("");
            for (String header : Constants.sensorsColumnHeaders) {
                if (!header.equals("Id")) {
                    columnNames.add(header);
                }
            }
            return new SelectableTableModel(0, columnNames.size(), new ArrayList<>(), jTableSensors);
        }
    }

    // Custom table model to handle checkbox and selection state together
    static class SelectableTableModel extends DefaultTableModel {
        private final JTable table;
        private final List<String> rowIds;

        public SelectableTableModel(int rowCount, int columnCount, List<String> rowIds, JTable jTable) {
            super(rowCount, columnCount);
            this.rowIds = rowIds != null ? new ArrayList<>(rowIds) : new ArrayList<>();
            this.table = jTable;
        }

        @Override
        public void setValueAt(Object value, int row, int column) {
            if (column == 0 && value instanceof Boolean) {
                super.setValueAt(value, row, column); // Update the checkbox state
                // No need to select/deselect rows - just handle checkbox state
                table.repaint();
            } else {
                super.setValueAt(value, row, column);
            }
        }

        public String getRowId(int row) {
            if (row >= 0 && row < rowIds.size()) {
                return rowIds.get(row);
            }
            return null;
        }

        @Override
        public boolean isCellEditable(int row, int column) {
            return column == 0; // Only checkbox column is editable
        }

        @Override
        public Class<?> getColumnClass(int columnIndex) {
            if (columnIndex == 0) {
                return Boolean.class;
            }
            return super.getColumnClass(columnIndex);
        }
    }

    // Custom cell editor for the checkbox column
    static class CheckBoxEditor extends DefaultCellEditor {
        public CheckBoxEditor(JCheckBox checkBox) {
            super(checkBox);
            checkBox.setHorizontalAlignment(SwingConstants.CENTER);
        }

        @Override
        public Component getTableCellEditorComponent(JTable table, Object value, boolean isSelected, int row, int column) {
            JCheckBox checkBox = (JCheckBox) super.getTableCellEditorComponent(table, value, isSelected, row, column);
            checkBox.setSelected(value != null && (Boolean) value);
            return checkBox;
        }
    }

    // Custom cell renderer for the checkbox column
    static class CheckBoxRenderer extends JCheckBox implements TableCellRenderer {
        public CheckBoxRenderer() {
            setHorizontalAlignment(SwingConstants.CENTER);
            setOpaque(true);
        }

        @Override
        public Component getTableCellRendererComponent(JTable table, Object value, boolean isSelected, boolean hasFocus, int row, int column) {
            setSelected(value != null && (Boolean) value);
            
            if (isSelected) {
                setBackground(table.getSelectionBackground());
                setForeground(table.getSelectionForeground());
            } else {
                setBackground(table.getBackground());
                setForeground(table.getForeground());
            }
            
            return this;
        }
    }
}