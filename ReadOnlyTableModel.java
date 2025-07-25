package ir.hakim.classes;

import javax.swing.*;
import javax.swing.event.TableModelEvent;
import javax.swing.table.DefaultTableModel;
import java.util.ArrayList;
import java.util.List;
import java.util.Vector;

public class ReadOnlyTableModel extends DefaultTableModel {
    private List<String> ids;
    private int iconColumnsCount = 0;

    public ReadOnlyTableModel() {
        super();
        this.ids = new ArrayList<>();
    }

    @Override
    public boolean isCellEditable(int row, int column) {
        return false;
    }

    @Override
    public Class<?> getColumnClass(int columnIndex) {
        // if last column header is empty and within icon columns range
        if (columnIndex >= getColumnCount() - iconColumnsCount && 
            columnIndex < getColumnCount() && 
            getColumnName(columnIndex).isEmpty()) {
            return Icon.class;
        } else {
            return super.getColumnClass(columnIndex);
        }
    }

    @Override
    public void removeRow(int row) {
        if (row >= 0 && row < getRowCount()) {
            super.removeRow(row);
            // Synchronize IDs list
            if (ids != null && row < ids.size()) {
                ids.remove(row);
            }
        }
    }

    @Override
    public void addRow(Vector<?> rowData) {
        super.addRow(rowData);
        // Add placeholder ID if not managed externally
        if (ids != null && ids.size() < getRowCount()) {
            ids.add(null);
        }
    }

    @Override
    public void addRow(Object[] rowData) {
        super.addRow(rowData);
        // Add placeholder ID if not managed externally
        if (ids != null && ids.size() < getRowCount()) {
            ids.add(null);
        }
    }

    @Override
    public void insertRow(int row, Vector<?> rowData) {
        super.insertRow(row, rowData);
        // Insert placeholder ID
        if (ids != null) {
            if (row >= 0 && row <= ids.size()) {
                ids.add(row, null);
            }
        }
    }

    @Override
    public void insertRow(int row, Object[] rowData) {
        super.insertRow(row, rowData);
        // Insert placeholder ID
        if (ids != null) {
            if (row >= 0 && row <= ids.size()) {
                ids.add(row, null);
            }
        }
    }

    @Override
    public void setDataVector(Vector<? extends Vector> dataVector, Vector<?> columnIdentifiers) {
        super.setDataVector(dataVector, columnIdentifiers);
        // Reset IDs list to match new data
        if (ids == null) {
            ids = new ArrayList<>();
        } else {
            ids.clear();
        }
        // Initialize with nulls - IDs should be set separately
        for (int i = 0; i < getRowCount(); i++) {
            ids.add(null);
        }
    }

    @Override
    public void setDataVector(Object[][] dataArray, Object[] columnNames) {
        super.setDataVector(dataArray, columnNames);
        // Reset IDs list to match new data
        if (ids == null) {
            ids = new ArrayList<>();
        } else {
            ids.clear();
        }
        // Initialize with nulls - IDs should be set separately
        for (int i = 0; i < getRowCount(); i++) {
            ids.add(null);
        }
    }

    @Override
    public void setRowCount(int rowCount) {
        int oldRowCount = getRowCount();
        super.setRowCount(rowCount);
        
        // Adjust IDs list size
        if (ids == null) {
            ids = new ArrayList<>();
        }
        
        if (rowCount > oldRowCount) {
            // Add nulls for new rows
            for (int i = oldRowCount; i < rowCount; i++) {
                ids.add(null);
            }
        } else if (rowCount < oldRowCount) {
            // Remove excess IDs
            while (ids.size() > rowCount) {
                ids.remove(ids.size() - 1);
            }
        }
    }

    public void setIconColumnsCount(int iconColumnsCount) {
        this.iconColumnsCount = Math.max(0, iconColumnsCount);
    }

    public int getIconColumnsCount() {
        return iconColumnsCount;
    }

    public void setIds(List<String> ids) {
        if (ids == null) {
            this.ids = new ArrayList<>();
        } else {
            this.ids = new ArrayList<>(ids);
        }
        
        // Ensure IDs list size matches row count
        while (this.ids.size() < getRowCount()) {
            this.ids.add(null);
        }
        while (this.ids.size() > getRowCount()) {
            this.ids.remove(this.ids.size() - 1);
        }
    }

    public List<String> getIds() {
        return ids != null ? new ArrayList<>(ids) : new ArrayList<>();
    }

    public String getId(int row) {
        if (ids == null || row < 0 || row >= ids.size()) {
            return null;
        }
        return ids.get(row);
    }

    public void setId(int row, String id) {
        if (ids == null) {
            ids = new ArrayList<>();
        }
        
        // Ensure list is large enough
        while (ids.size() <= row) {
            ids.add(null);
        }
        
        if (row >= 0 && row < getRowCount()) {
            ids.set(row, id);
        }
    }

    public void addId(String id) {
        if (this.ids == null) {
            this.ids = new ArrayList<>();
        }
        this.ids.add(id);
    }

    public boolean removeId(String id) {
        if (ids != null) {
            return ids.remove(id);
        }
        return false;
    }

    public void clearIds() {
        if (ids != null) {
            ids.clear();
        }
    }

    /**
     * Find the row index for a given ID
     * @param id The ID to search for
     * @return The row index, or -1 if not found
     */
    public int findRowById(String id) {
        if (ids == null || id == null) {
            return -1;
        }
        return ids.indexOf(id);
    }

    /**
     * Check if the model contains a specific ID
     * @param id The ID to check for
     * @return true if the ID exists, false otherwise
     */
    public boolean containsId(String id) {
        return ids != null && ids.contains(id);
    }

    /**
     * Get the number of non-null IDs
     * @return The count of valid IDs
     */
    public int getValidIdCount() {
        if (ids == null) {
            return 0;
        }
        return (int) ids.stream().filter(id -> id != null && !id.trim().isEmpty()).count();
    }
}