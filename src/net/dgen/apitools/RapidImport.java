/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package net.dgen.apitools;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.IOException;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Map;
import net.dgen.apiexamples.Main;

/**
 * TO DO NEXT: implement the following
 * create new DI in AMEE
 * create new category (full structure) for DI def in AMEE
 * put new data items in that category
 * @author nalu
 */
public class RapidImport {

    private String delimiter = ",";
    private boolean isToRemoveQuotes = true;
    private ItemDefinition itemDef;
    private File dataFile;
    private int[] ignoreColumns,  ignoreRows;
    private int[] drillColumns;
    private Map<Integer, String> columnNameMap = new LinkedHashMap();
    private Map<Integer, Map<Integer, String>> rowMap = new LinkedHashMap();

    public RapidImport(File dataCSVFile, int[] drillColumns) {
        this.dataFile = dataCSVFile;
        this.drillColumns = drillColumns;
    }

    public void setIgnoreColumns(int[] ignoreColumns) {
        this.ignoreColumns = ignoreColumns;
    }

    public void setIgnoreRows(int[] ignoreRows) {
        this.ignoreRows = ignoreRows;
    }

    /** 
     * Default delimiter is a comma.
     * @param regexp As using by java's String.split method.
     */
    public void setDelimiter(String regexp) {
        delimiter = regexp;
    }

    /**
     * @param b if true, any quotes surrounding fields will be removed. true by
     * default.
     */
    public void setRemoveQuotes(boolean b) {
        isToRemoveQuotes = b;
    }

    public void checkForUnmatchedQuotes(String line, int row) {
        int pos, count = 0;//number of quotes
        pos = line.indexOf('"', 0);
        while (pos >= 0) {
            count++;
            pos = line.indexOf('"', pos + 1);
        }
        if (count % 2 == 1) {
            System.err.println("WARNING: there are unmatch quotes on row " + row + ":\n" + line);
        }
    }

    private boolean load() {
        itemDef = new ItemDefinition();
        itemDef.algorithm = "//Generated by RapidImport";
        boolean success = true;
        BufferedReader br = ApiTools.getBufferedReader(dataFile);
        try {
            String line;
            String[] ss = dataFile.getName().split("\\.");
            itemDef.name = ss[0].trim();
            boolean isHeaderRow = true;//First non-ignored row is the header
            int row = 0;
            while ((line = br.readLine()) != null) {
                checkForUnmatchedQuotes(line, row);
                //System.err.print("row=" + row);
                if (isIndexInArray(row, ignoreRows)) {
                    //System.err.println(" ... ignoring");
                } else {
                    if (!isHeaderRow) {
                        createNewRow(row);
                    }
                    //System.err.println(" ... reading");
                    String[] fields = line.split(delimiter);
                    for (int col = 0; col < fields.length; col++) {
                        if (!isIndexInArray(col, ignoreColumns)) {
                            String field = cleanField(fields[col]);
                            processField(field, col, row, isHeaderRow);
                        }
                    }
                    isHeaderRow = false;
                }
                row++;
            }
            br.close();
        } catch (IOException e) {
            e.printStackTrace();
            success = false;
        }
        return success;
    }

    private boolean save(File dir) {
        boolean success = true;
        itemDef.save(dir);
        BufferedWriter bw = ApiTools.getBufferedWriter(new File(dir, "data.csv"));
        try {
            //write header line
            String headerLine = "";
            Iterator<Integer> iter = columnNameMap.keySet().iterator();
            while (iter.hasNext()) {
                String field = columnNameMap.get(iter.next());
                headerLine += field;
                if (iter.hasNext()) {
                    headerLine += ",";
                }
            }
            bw.write(headerLine + "\n");
            //now write data
            Iterator<Map<Integer, String>> rowIter = rowMap.values().iterator();
            while (rowIter.hasNext()) {
                String line = "";
                Map<Integer, String> colMap = rowIter.next();
                Iterator<Integer> colIter = columnNameMap.keySet().iterator();
                while (colIter.hasNext()) {
                    Integer col = colIter.next();
                    String field = colMap.get(col);
                    if (field == null) {
                        field = "";
                    }
                    line += field;
                    if (colIter.hasNext()) {
                        line += ",";
                    }
                }
                bw.write(line + "\n");
            }
            bw.close();
        } catch (IOException ex) {
            ex.printStackTrace();
            success = false;
        }
        return success;
    }

    private void createNewRow(int row) {
        Map<Integer, String> colMap = new LinkedHashMap();
        rowMap.put(row, colMap);
    }

    private void processField(String field, int col, int row, boolean isHeaderRow) {
        if (isHeaderRow) {
            processHeaderField(field, col, row);
        } else {
            processDataField(field, col, row);
        }
    }

    private void processHeaderField(String field, int col, int row) {
        boolean isDrill = isIndexInArray(col, drillColumns);
        if (field.length() == 0) {
            System.err.println("WARNING: Heading is blank for col= " + col);
        } else if (itemDef.valueMap.containsKey(field)) {
            System.err.println("WARNING: Two columns have the same heading: " + field);
        }
        itemDef.addValue(field, field, ItemDefinitionSync.UNKNOWN, "true", isDrill);
        columnNameMap.put(col, field);
    //System.err.println("col " + col + " is " + field + ", isDrill=" + isDrill);
    }

    /**
     * This method takes the field and then alters the value definition type
     * associated with this column. Initially the column type is UNKNOWN and
     * the first field encountered for that column is used to set the type.
     * Once the type is set to TEXT it cannot be changed.
     * If the column type is a number (INTEGER or DECIMAL) and the current field
     * is TEXT then the column type is set to TEXT. If the column type is
     * INTEGER and the current field is a decimal, then the column type is set
     * to DECIMAL.
     * the type
     * @param field
     * @param col
     * @param row
     */
    private void processDataField(String field, int col, int row) {
        if (col >= columnNameMap.size()) {
            System.err.println("WARNING: This field is outside heading columns on row " + row + ", col " + col + ": " + field);
        } else {
            String colName = columnNameMap.get(col);
            ItemDefinition.ValueDefinition vd = itemDef.valueMap.get(colName);
            if (vd.type.equals(ItemDefinitionSync.UNKNOWN)) {
                vd.type = getType(field);
            } else if (vd.type.equals(ItemDefinitionSync.INTEGER)) {
                String fieldType = getType(field);
                if (fieldType.equals(ItemDefinitionSync.TEXT)) {
                    vd.type = ItemDefinitionSync.TEXT;
                } else if (fieldType.equals(ItemDefinitionSync.DECIMAL)) {
                    vd.type = ItemDefinitionSync.DECIMAL;
                }
            } else if (vd.type.equals(ItemDefinitionSync.DECIMAL)) {
                String fieldType = getType(field);
                if (fieldType.equals(ItemDefinitionSync.TEXT)) {
                    vd.type = ItemDefinitionSync.TEXT;
                }
            }
        }
        Map<Integer, String> colMap = rowMap.get(row);
        colMap.put(col, field);
    }

    private static String getType(String field) {
        try {
            Integer.parseInt(field);
            return ItemDefinitionSync.INTEGER;
        } catch (NumberFormatException ex) {
        }
        try {
            Double.parseDouble(field);
            return ItemDefinitionSync.DECIMAL;
        } catch (NumberFormatException ex) {
        }
        return ItemDefinitionSync.TEXT;

    }

    private String cleanField(String field) {
        field = field.trim();
        if (isToRemoveQuotes && field.length() >= 2) {
            if (field.charAt(0) == '"' && field.charAt(field.length() - 1) == '"') {
                field = field.substring(1, field.length() - 1);
            }
        }
        return field;
    }

    private static boolean isIndexInArray(int index, int[] array) {
        if (array != null) {
            for (int i = 0; i < array.length; i++) {
                if (index == array[i]) {
                    return true;
                }
            }
        }

        return false;
    }

    public static void main(String[] args) {
        File dir = new File("/home/nalu/Desktop/carma/");
        File file = new File(dir, "carma_countries.csv");
        RapidImport ri = new RapidImport(file, new int[]{1});
        ri.setIgnoreRows(new int[]{0, 1, 2, 3, 4});
        //ri.setIgnoreColumns(new int[]{3, 4});
        ri.load();
        System.err.println("item def:" + ri.itemDef);
        ri.save(dir);
    }
}
