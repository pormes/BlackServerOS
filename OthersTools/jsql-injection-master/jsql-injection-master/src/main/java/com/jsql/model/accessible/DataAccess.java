/*******************************************************************************
 * Copyhacked (H) 2012-2016.
 * This program and the accompanying materials
 * are made available under no term at all, use it like
 * you want, but share and discuss about it
 * every time possible with every body.
 * 
 * Contributors:
 *      ron190 at ymail dot com - initial implementation
 ******************************************************************************/
package com.jsql.model.accessible;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.apache.log4j.Logger;

import com.jsql.model.MediatorModel;
import com.jsql.model.bean.database.AbstractElementDatabase;
import com.jsql.model.bean.database.Column;
import com.jsql.model.bean.database.Database;
import com.jsql.model.bean.database.Table;
import com.jsql.model.bean.util.Interaction;
import com.jsql.model.bean.util.Request;
import com.jsql.model.exception.IgnoreMessageException;
import com.jsql.model.exception.InjectionFailureException;
import com.jsql.model.exception.JSqlException;
import com.jsql.model.exception.SlidingException;
import com.jsql.model.injection.vendor.Vendor;
import com.jsql.model.suspendable.SuspendableGetRows;

/**
 * Database ressource object to read name of databases, tables, columns and values
 * using most suited injection strategy.
 */
public class DataAccess {
	
    /**
     * Log4j logger sent to view.
     */
    private static final Logger LOGGER = Logger.getRootLogger();
    
    /**
     * SQL characters marking the end of the result of an injection.
     * Process stops when this schema is encountered: SQLix01x03x03x07
     */
    public static final String TRAIL_SQL = "%01%03%03%07";
    
    /**
     * Regex characters marking the end of the result of an injection.
     * Process stops when this schema is encountered: SQLix01x03x03x07
     */
    public static final String TRAIL_RGX = "\\x01\\x03\\x03\\x07";
    
    public static final String SEPARATOR_FIELD_HEX = "0x7f";
    public static final String SEPARATOR_FIELD_SQL = "%7f";
    
    /**
     * Regex character used between each table cells.
     * Expected schema of multiple table cells :
     * x04[table cell]x05[number of occurences]x04x06x04[table cell]x05[number of occurences]x04
     */
    public static final String SEPARATOR_CELL_RGX = "\\x06";
    
    /**
     * SQL character used between each table cells.
     * Expected schema of multiple table cells :
     * %04[table cell]%05[number of occurences]%04%06%04[table cell]%05[number of occurences]%04
     */
    public static final String SEPARATOR_CELL_SQL = "%06";
    
    /**
     * SQL character used between the table cell and the number of occurence of the cell text.
     * Expected schema of a table cell data is %04[table cell]%05[number of occurences]%04
     */
    public static final String SEPARATOR_QTE_SQL = "%05";
    
    /**
     * Regex character used between the table cell and the number of occurence of the cell text.
     * Expected schema of a table cell data is x04[table cell]x05[number of occurences]x04
     */
    public static final String SEPARATOR_QTE_RGX = "\\x05";
    
    /**
     * Regex character enclosing a table cell returned by injection.
     * It allows to detect the correct end of a table cell data during parsing.
     * Expected schema of a table cell data is x04[table cell]x05[number of occurences]x04
     */
    public static final String ENCLOSE_VALUE_RGX = "\\x04";
    
    /**
     * SQL character enclosing a table cell returned by injection.
     * It allows to detect the correct end of a table cell data during parsing.
     * Expected schema of a table cell data is %04[table cell]%05[number of occurences]%04
     */
    public static final String ENCLOSE_VALUE_SQL = "%04";
    
    public static final String CALIBRATOR_SQL = "%23";
    public static final String CALIBRATOR_HEX = "0x23";
    public static final String ENCLOSE_VALUE_HEX = "0x04";
    public static final String SEPARATOR_QTE_HEX = "0x05";
    public static final String SEPARATOR_CELL_HEX = "0x06";
    public static final String LEAD_HEX = "0x53514c69";
    public static final String LEAD = "SQLi";
    public static final String TRAIL_HEX = "0x01030307";
    public static final String TRAIL = "iLQS";
    
    /**
     * Regex keywords corresponding to multiline and case insensitive match.
     */
    public static final String MODE = "(?si)";
    
    /**
     * Regex schema describing a table cell with firstly the cell content and secondly the number of occurences
     * of the cell text, separated by the reserved character x05 in hexadecimal.
     * The range of characters from x01 to x1F are not printable ASCII characters used to parse the data and exclude
     * printable characters during parsing.
     * Expected schema of a table cell data is x04[table cell]x05[number of occurences]x04
     */
    public static final String CELL_TABLE = "([^\\x01-\\x09\\x0B-\\x0C\\x0E-\\x1F]*)"+ SEPARATOR_QTE_RGX +"([^\\x01-\\x09\\x0B-\\x0C\\x0E-\\x1F]*)(\\x08)?";
    
    // Utility class
    private DataAccess() {
        // not used
    }
    
    /**
     * Get general database informations.<br>
     * => version{%}database{%}user{%}CURRENT_USER
     * @throws JSqlException
     */
    public static void getDatabaseInfos() throws JSqlException {
        LOGGER.trace("Fetching informations...");
        
        String[] sourcePage = {""};

        String resultToParse;
        resultToParse = new SuspendableGetRows().run(
            MediatorModel.model().getVendor().instance().sqlInfos(),
            sourcePage,
            false,
            0,
            null
        );

        if ("".equals(resultToParse)) {
            MediatorModel.model().sendResponseFromSite("Incorrect database informations", sourcePage[0].trim());
        }

        // Check if parsing is failing
        try {
            MediatorModel.model().setDatabaseInfos(
                resultToParse.split(ENCLOSE_VALUE_RGX)[0].replaceAll("\\s+"," "),
                resultToParse.split(ENCLOSE_VALUE_RGX)[1],
                resultToParse.split(ENCLOSE_VALUE_RGX)[2]
            );
        } catch (ArrayIndexOutOfBoundsException e) {
            LOGGER.warn("Incorrect or incomplete data: "+ resultToParse, e);
            LOGGER.info("Processing but expecting failure...");
        }
        
        LOGGER.debug(MediatorModel.model().getDatabaseInfos());
    }
    
    /**
     * Get database names and table counts and send them to the view.<br>
     * Use readable text (not hexa) and parse this pattern:<br>
     * => hh[database name 1]jj[table count]hhgghh[database name 2]jj[table count]hhggh...hi<br>
     * Data window can be cut before the end of the request but the process helps to obtain
     * the rest of the unreachable data. The process can be interrupted by the user (stop/pause).
     * @return list of databases found
     * @throws JSqlException when injection failure or stopped by user
     */
    public static List<Database> listDatabases() throws JSqlException {
        LOGGER.trace("Fetching databases...");
        
        List<Database> databases = new ArrayList<>();
        
        String resultToParse = "";
        try {
            String[] sourcePage = {""};
            resultToParse = new SuspendableGetRows().run(
                MediatorModel.model().getVendor().instance().sqlDatabases(),
                sourcePage,
                true,
                0,
                null
            );
        } catch (SlidingException e) {
            LOGGER.warn(e.getMessage(), e);
            // Get pieces of data already retreive instead of losing them
            if (!"".equals(e.getSlidingWindowAllRows())) {
                resultToParse = e.getSlidingWindowAllRows();
            } else if (!"".equals(e.getSlidingWindowCurrentRows())) {
                resultToParse = e.getSlidingWindowCurrentRows();
            }
        } catch (Exception e) {
            LOGGER.warn(e.getMessage(), e);
        }

        // Parse all data we have retrieved
        Matcher regexSearch =
            Pattern
                .compile(
            		MODE
            		+ ENCLOSE_VALUE_RGX
            		+ CELL_TABLE
            		+ ENCLOSE_VALUE_RGX
        		)
                .matcher(resultToParse);

        if (!regexSearch.find()) {
            throw new InjectionFailureException("Name of databases not found");
        }
        
        regexSearch.reset();

        // Build an array of Database objects from the data we have parsed
        while (regexSearch.find()) {
            String databaseName = regexSearch.group(1);
            String tableCount = regexSearch.group(2);

            Database newDatabase = new Database(databaseName, tableCount);
            databases.add(newDatabase);
        }

        Request request = new Request();
        request.setMessage(Interaction.ADD_DATABASES);
        request.setParameters(databases);
        MediatorModel.model().sendToViews(request);
        
        return databases;
    }

    /**
     * Get tables name and row count and send them to the view.<br>
     * Use readable text (not hexa) and parse this pattern:<br>
     * => hh[table name 1]jj[rows count]hhgghh[table name 2]jj[rows count]hhggh...hi<br>
     * Data window can be cut before the end of the request but the process helps to obtain
     * the rest of the unreachable data. The process can be interrupted by the user (stop/pause).
     * @param database which contains tables to find
     * @return list of tables found
     * @throws JSqlException when injection failure or stopped by user
     */
    public static List<Table> listTables(Database database) throws JSqlException {
        // Reset stoppedByUser if list of Databases is partial
        // and some Tables are still reachable
        MediatorModel.model().setIsStoppedByUser(false);
        
        List<Table> tables = new ArrayList<>();
        
        // Inform the view that database has just been used
        Request requestStartProgress = new Request();
        requestStartProgress.setMessage(Interaction.START_PROGRESS);
        requestStartProgress.setParameters(database);
        MediatorModel.model().sendToViews(requestStartProgress);

        String tableCount = Integer.toString(database.getChildCount());
        
        String resultToParse = "";
        try {
            String[] pageSource = {""};
            resultToParse = new SuspendableGetRows().run(
                MediatorModel.model().getVendor().instance().sqlTables(database),
                pageSource,
                true,
                Integer.parseInt(tableCount),
                database
            );
        } catch (SlidingException e) {
            LOGGER.warn(e.getMessage(), e);
            // Get pieces of data already retreive instead of losing them
            if (!"".equals(e.getSlidingWindowAllRows())) {
                resultToParse = e.getSlidingWindowAllRows();
            } else if (!"".equals(e.getSlidingWindowCurrentRows())) {
                resultToParse = e.getSlidingWindowCurrentRows();
            }
        } catch (Exception e) {
            LOGGER.warn(e.getMessage(), e);
        }

        // Parse all the data we have retrieved
        Matcher regexSearch =
            Pattern
                .compile(
            		MODE
            		+ ENCLOSE_VALUE_RGX
            		+ CELL_TABLE
            		+ ENCLOSE_VALUE_RGX
        		)
                .matcher(resultToParse);
        
        Request requestEndProgress = new Request();
        requestEndProgress.setMessage(Interaction.END_PROGRESS);
        requestEndProgress.setParameters(database);
        MediatorModel.model().sendToViews(requestEndProgress);
        
        if (!regexSearch.find()) {
            throw new InjectionFailureException("Name of tables not found");
        }
        
        regexSearch.reset();
        
        // Build an array of Table objects from the data we have parsed
        while (regexSearch.find()) {
            String tableName = regexSearch.group(1);
            String rowCount  = regexSearch.group(2);
            
            Table newTable = new Table(tableName, rowCount, database);
            tables.add(newTable);
        }
        
        Request requestAddTables = new Request();
        requestAddTables.setMessage(Interaction.ADD_TABLES);
        requestAddTables.setParameters(tables);
        MediatorModel.model().sendToViews(requestAddTables);
        
        return tables;
    }

    /**
     * Get column names and send them to the view.<br>
     * Use readable text (not hexa) and parse this pattern with 2nd member forced to 31 (1 in ascii):<br>
     * => hh[column name 1]jj[31]hhgghh[column name 2]jj[31]hhggh...hi<br>
     * Data window can be cut before the end of the request but the process helps to obtain
     * the rest of the unreachable data. The process can be interrupted by the user (stop/pause).
     * @param table which contains columns to find
     * @return list of columns found
     * @throws JSqlException when injection failure or stopped by user
     */
    public static List<Column> listColumns(Table table) throws JSqlException {
        List<Column> columns = new ArrayList<>();
        
        // Inform the view that table has just been used
        Request requestStartProgress = new Request();
        requestStartProgress.setMessage(Interaction.START_INDETERMINATE_PROGRESS);
        requestStartProgress.setParameters(table);
        MediatorModel.model().sendToViews(requestStartProgress);

        String resultToParse = "";
        try {
            String[] pageSource = {""};
            resultToParse = new SuspendableGetRows().run(
                MediatorModel.model().getVendor().instance().sqlColumns(table),
                pageSource,
                true,
                0,
                table
            );
        } catch (SlidingException e) {
            LOGGER.warn(e.getMessage(), e);
            // Get pieces of data already retreive instead of losing them
            if (!"".equals(e.getSlidingWindowAllRows())) {
                resultToParse = e.getSlidingWindowAllRows();
            } else if (!"".equals(e.getSlidingWindowCurrentRows())) {
                resultToParse = e.getSlidingWindowCurrentRows();
            }
        } catch (Exception e) {
            LOGGER.warn(e.getMessage(), e);
        }

        // TODO send to SQLite
        // Build SQLite columns
        if (MediatorModel.model().getVendor() == Vendor.SQLITE) {
            StringBuilder resultSQLite = new StringBuilder();
            String resultTmp = resultToParse.replaceFirst(".+?\\(", "").trim().replaceAll("\\)$", "");
            resultTmp = resultTmp.replaceAll("\\(.+?\\)", "");
            for (String columnNameAndType: resultTmp.split(",")) {
                String columnName = columnNameAndType.trim().split(" ")[0];
                if (!"CONSTRAINT".equals(columnName) && !"UNIQUE".equals(columnName)) {
                    resultSQLite.append((char) 4 + columnName + (char) 5 + "0" + (char) 4 + (char) 6);
                }
            }
            resultToParse = resultSQLite.toString();
        }
        
        // Parse all the data we have retrieved
        Matcher regexSearch =
            Pattern
                .compile(
            		MODE
            		+ ENCLOSE_VALUE_RGX
            		+ CELL_TABLE
            		+ ENCLOSE_VALUE_RGX
        		)
                .matcher(resultToParse);

        Request requestEndProgress = new Request();
        requestEndProgress.setMessage(Interaction.END_INDETERMINATE_PROGRESS);
        requestEndProgress.setParameters(table);
        MediatorModel.model().sendToViews(requestEndProgress);

        if (!regexSearch.find()) {
            throw new InjectionFailureException("Name of columns not found");
        }

        regexSearch.reset();

        // Build an array of Column objects from the data we have parsed
        while (regexSearch.find()) {
            String nameColumn = regexSearch.group(1);

            Column column = new Column(nameColumn, table);
            columns.add(column);
        }

        Request requestAddColumns = new Request();
        requestAddColumns.setMessage(Interaction.ADD_COLUMNS);
        requestAddColumns.setParameters(columns);
        MediatorModel.model().sendToViews(requestAddColumns);
        
        return columns;
    }

    /**
     * Get table values and count each occurrences and send them to the view.<br>
     * Values are on clear text (not hexa) and follows this window pattern<br>
     * => hh[value 1]jj[count]hhgghh[value 2]jj[count]hhggh...hi<br>
     * Data window can be cut before the end of the request but the process helps to obtain
     * the rest of the unreachable data. The process can be interrupted by the user (stop/pause).
     * @param columns choosed by the user
     * @return a 2x2 table containing values by columns
     * @throws JSqlException when injection failure or stopped by user
     */
    public static String[][] listValues(List<Column> columns) throws JSqlException {
        Database database = (Database) columns.get(0).getParent().getParent();
        Table table = (Table) columns.get(0).getParent();
        int rowCount = columns.get(0).getParent().getChildCount();

        // Inform the view that table has just been used
        Request request = new Request();
        request.setMessage(Interaction.START_PROGRESS);
        request.setParameters(table);
        MediatorModel.model().sendToViews(request);

        // Build an array of column names
        List<String> columnsName = new ArrayList<>();
        for (AbstractElementDatabase e: columns) {
            columnsName.add(e.toString());
        }

        /*
         * From that array, build the SQL fields nicely
         * =>  col1{%}col2...
         * ==> trim(ifnull(`col1`,0x00)),0x7f,trim(ifnull(`Col2`,0x00))...
         */
        String[] arrayColumns = columnsName.toArray(new String[columnsName.size()]);

        String resultToParse = "";
        try {
            String[] pageSource = {""};
            resultToParse = new SuspendableGetRows().run(
                MediatorModel.model().getVendor().instance().sqlRows(arrayColumns, database, table),
                pageSource,
                true,
                rowCount,
                table
            );
        } catch (SlidingException e) {
            LOGGER.warn(e.getMessage(), e);
            // Get pieces of data already retreive instead of losing them
            if (!"".equals(e.getSlidingWindowAllRows())) {
                resultToParse = e.getSlidingWindowAllRows();
            } else if (!"".equals(e.getSlidingWindowCurrentRows())) {
                resultToParse = e.getSlidingWindowCurrentRows();
            }
        } catch (Exception e) {
            LOGGER.warn(e.getMessage(), e);
        }

        // Parse all the data we have retrieved
        Matcher regexSearch =
            Pattern
            	// TODO requete differente
                .compile(
            		MODE
            		+ ENCLOSE_VALUE_RGX
            		+ "([^\\x01-\\x09\\x0B-\\x0C\\x0E-\\x1F]*?)"
            		+ SEPARATOR_QTE_RGX
            		+ "([^\\x01-\\x09\\x0B-\\x0C\\x0E-\\x1F]*?)(\\x08)?"
            		+ ENCLOSE_VALUE_RGX
        		)
                .matcher(resultToParse);

        if (!regexSearch.find()) {
            throw new InjectionFailureException("Fetching values fails");
        }
        
        regexSearch.reset();

        int rowsFound = 0;
        List<List<String>> listValues = new ArrayList<>();

        // Build a 2D array of strings from the data we have parsed
        // => row number, occurrence, value1, value2...
        while (regexSearch.find()) {
            String values = regexSearch.group(1);
            int instances = Integer.parseInt(regexSearch.group(2));

            listValues.add(new ArrayList<String>());
            listValues.get(rowsFound).add(Integer.toString(rowsFound + 1));
            listValues.get(rowsFound).add("x"+ instances);
            for (String cellValue: values.split("\\x7F", -1)) {
                listValues.get(rowsFound).add(cellValue);
            }

            rowsFound++;
        }

        // Add the default title to the columns: row number, occurrence
        columnsName.add(0, "");
        columnsName.add(0, "");

        // Build a proper 2D array from the data
        String[][] tableDatas = new String[listValues.size()][columnsName.size()];
        for (int indexRow = 0 ; indexRow < listValues.size() ; indexRow++) {
            boolean isIncomplete = false;
            for (int indexColumn = 0 ; indexColumn < columnsName.size() ; indexColumn++) {
                try {
                    tableDatas[indexRow][indexColumn] = listValues.get(indexRow).get(indexColumn);
                } catch (IndexOutOfBoundsException e) {
                    isIncomplete = true;
                    LOGGER.trace("Incomplete line found");
                    
                    // Ignore
                    IgnoreMessageException exceptionIgnored = new IgnoreMessageException(e);
                    LOGGER.trace(exceptionIgnored, exceptionIgnored);
                }
            }
            if (isIncomplete) {
                LOGGER.warn("String is too long, row #"+ (indexRow + 1) +" is incomplete:");
                LOGGER.warn(String.join(", ", listValues.get(indexRow).toArray(new String[listValues.get(indexRow).size()])));
            }
        }

        arrayColumns = columnsName.toArray(new String[columnsName.size()]);
        
        // Group the columns names, values and Table object in one array
        Object[] objectData = {arrayColumns, tableDatas, table};

        Request requestCreateValuesTab = new Request();
        requestCreateValuesTab.setMessage(Interaction.CREATE_VALUES_TAB);
        requestCreateValuesTab.setParameters(objectData);
        MediatorModel.model().sendToViews(requestCreateValuesTab);

        Request requestEndProgress = new Request();
        requestEndProgress.setMessage(Interaction.END_PROGRESS);
        requestEndProgress.setParameters(table);
        MediatorModel.model().sendToViews(requestEndProgress);
        
        return tableDatas;
    }
    
}
