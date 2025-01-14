package com.jsql.model.suspendable;

import java.util.concurrent.CompletionService;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorCompletionService;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.regex.Pattern;

import org.apache.log4j.Logger;

import com.jsql.model.MediatorModel;
import com.jsql.model.exception.StoppedByUserSlidingException;
import com.jsql.model.injection.vendor.Vendor;
import com.jsql.model.suspendable.callable.CallablePageSource;
import com.jsql.model.suspendable.callable.ThreadFactoryCallable;

/**
 * Runnable class, define insertionCharacter that will be used by all futures requests,
 * i.e -1 in "[..].php?id=-1 union select[..]", sometimes it's -1, 0', 0, etc,
 * this class/function tries to find the working one by searching a special error message
 * in the source page.
 */
public class SuspendableGetVendor extends AbstractSuspendable<Vendor> {
	
    /**
     * Log4j logger sent to view.
     */
    private static final Logger LOGGER = Logger.getRootLogger();

    /**
     * 
     */
    @Override
    public Vendor run(Object... args) throws StoppedByUserSlidingException {
        Vendor vendor = null;
        
        if (MediatorModel.model().getVendorByUser() != Vendor.AUTO) {
            return MediatorModel.model().getVendorByUser();
        }
        
        // Parallelize the search and let the user stops the process if needed.
        // SQL: force a wrong ORDER BY clause with an inexistent column, order by 1337,
        // and check if a correct error message is sent back by the server:
        //         Unknown column '1337' in 'order clause'
        // or   supplied argument is not a valid MySQL result resource
        ExecutorService taskExecutor = Executors.newCachedThreadPool(new ThreadFactoryCallable("CallableGetVendor"));
        CompletionService<CallablePageSource> taskCompletionService = new ExecutorCompletionService<>(taskExecutor);
        for (String insertionCharacter : new String[] {"'\"#-)'\""}) {
            taskCompletionService.submit(
                new CallablePageSource(
                    insertionCharacter,
                    insertionCharacter
                )
            );
        }

        int total = 1;
        while (0 < total) {

            if (this.isSuspended()) {
                throw new StoppedByUserSlidingException();
            }
            
            try {
                CallablePageSource currentCallable = taskCompletionService.take().get();
                total--;
                String pageSource = currentCallable.getContent();
                
                if (pageSource.matches("(?si).*("
                        // JDBC + php : same error
                        + "You have an error in your SQL syntax; check the manual that corresponds to your MySQL server version for the right syntax to use near"
                        + "|"
                        + "MySQL"
                + ").*")) {
                    vendor = Vendor.MYSQL;
                }
                
                else if (pageSource.matches("(?si).*("
                        // JDBC + php : same error
                        + "You have an error in your SQL syntax; check the manual that corresponds to your MariaDB server version for the right syntax to use near"
                        + "|"
                        + "MariaDB"
                        + ").*")) {
                    vendor = Vendor.MARIADB;
                }
                
                else if (pageSource.matches("(?si).*("
                        // JDBC + php : same error
                        + "HSQLDB"
                        + ").*")) {
                    vendor = Vendor.HSQLDB;
                }
                
                else if (pageSource.matches("(?si).*("
                        // jdbc
                        + "ERROR: unterminated quoted identifier at or near"
                        + "|"
                        // php
                        + "Query failed: ERROR:  unterminated quoted string at or near"
                        + "|"
                        // php
                        + "function\\.pg"
                        + "|"
                        + "PostgreSQL"
                + ").*")) {
                    vendor = Vendor.POSTGRESQL;
                }
                
                /**
                Warning: oci_parse() [function.oci-parse]: ORA-01756: quoted string not properly terminated in x\oracle_simulate_get.php on line 6
                
                Warning: oci_execute() expects parameter 1 to be resource, boolean given in x\oracle_simulate_get.php on line 7
                
                Warning: oci_fetch_array() expects parameter 1 to be resource, boolean given in x\oracle_simulate_get.php on line 10
                
                jdbc
                Error at line 1:
                ORA-01740: missing double quote in identifier
                select '"'"'
                          ^
                 */
                else if (Pattern.compile(".*function\\.oci.*", Pattern.DOTALL).matcher(pageSource).matches()) {
                    vendor = Vendor.ORACLE;
                }
                
                /**
                 * Fatal error: Uncaught exception 'PDOException' with message 'SQLSTATE[42000]: [Microsoft][SQL Server Native Client 11.0][SQL Server]An object or column name is missing or empty. For SELECT INTO statements, verify each column has a name. For other statements, look for empty alias names. Aliases defined as "" or [] are not allowed. Change the alias to a valid name.'
                 * or
                 * Fatal error: Uncaught exception 'PDOException' with message 'SQLSTATE[42000]: [Microsoft][SQL Server Native Client 11.0][SQL Server]Unclosed quotation mark after the character string
                 * 
                 * jdbc
                 * Unclosed quotation mark after the character string '''. [SQL State=S0001, DB Errorcode=105]
                 */
                else if (Pattern.compile(".*SQL Server.*", Pattern.DOTALL).matcher(pageSource).matches()) {
                    vendor = Vendor.SQLSERVER;
                }
                
                /**
                 * Warning: db2_execute() [function.db2-execute]: Statement Execute Failed in x\db2_simulate_get.php on line 13
                exec errormsg: [IBM][CLI Driver][DB2/NT] SQL0010N La constante commen ant par """ ne comporte pas de d limiteur de fin de cha ne. SQLSTATE=42603
                Warning: db2_fetch_array() [function.db2-fetch-array]: Column information cannot be retrieved in x\db2_simulate_get.php on line 17

                 * jdbc
                 * DB2 SQL Error: SQLCODE=-10, SQLSTATE=42603, SQLERRMC="', DRIVER=3.69.24 [SQL State=42603, DB Errorcode=-10]
                Next: DB2 SQL Error: SQLCODE=-727, SQLSTATE=56098, SQLERRMC=2;-10;42603;"', DRIVER=3.69.24 [SQL State=56098, DB Errorcode=-727]
                 */
                else if (Pattern.compile(".*function\\.db2.*", Pattern.DOTALL).matcher(pageSource).matches()) {
                    vendor = Vendor.DB2;
                }
                
                /**
                 * Query failed: line 1, Non-terminated string
                 * 
                 * jdbc
                 * Unmatched quote, parenthesis, bracket or brace. [SQL State=42000, DB Errorcode=802835]
                 */
                else if (Pattern.compile(".*Non-terminated string.*", Pattern.DOTALL).matcher(pageSource).matches()) {
                    vendor = Vendor.INGRES;
                }
                
                /**
                 * Warning: sybase_connect() [function.sybase-connect]: Sybase: Server message: Changed database context to 'master'. (severity 10, procedure N/A) in x\sybase_simulate_get.php on line 5

                Warning: sybase_query() [function.sybase-query]: Sybase: Server message: Unclosed quote before the character string '\"'. (severity 15, procedure N/A) in x\sybase_simulate_get.php on line 10
                
                Warning: sybase_query() [function.sybase-query]: Sybase: Server message: Incorrect syntax near '\"'. (severity 15, procedure N/A) in x\sybase_simulate_get.php on line 10
                
                Warning: sybase_fetch_row() expects parameter 1 to be resource, boolean given in x\sybase_simulate_get.php on line 14
                
                jdbc
                Invalid SQL statement or JDBC escape, terminating '"' not found. [SQL State=22025]
                 */
                else if (Pattern.compile(".*function\\.sybase.*", Pattern.DOTALL).matcher(pageSource).matches()) {
                    vendor = Vendor.SYBASE;
                }
                
                /**
                 *  Warning: maxdb::query() [maxdb.query]: -3014 POS(40) Invalid end of SQL statement [42000] in x\maxdb_simulate_get.php on line 40
                 * 
                 * jdbc
                 * [-3014] (at 12): Invalid end of SQL statement

select '"'"'
           ^
                 */
                else if (Pattern.compile(".*maxdb\\.query.*", Pattern.DOTALL).matcher(pageSource).matches()) {
                    vendor = Vendor.MAXDB;
                }
                
                /**
                 * SQLSTATE[HY000]: General error: -11060 [Informix][Informix ODBC Driver]General error. (SQLPrepare[-11060] at ext\PDO_INFORMIX-1.3.1\informix_driver.c:131)
                 * 
                 * jdbc
                 * Found a quote for which there is no matching quote. [SQL State=IX000, DB Errorcode=-282]
                 */
                else if (Pattern.compile(".*Informix.*", Pattern.DOTALL).matcher(pageSource).matches()) {
                    vendor = Vendor.INFORMIX;
                }
                
                /**
                 * 
Warning: ibase_query() [function.ibase-query]: Dynamic SQL Error SQL error code = -104 as approximate floating-point values in SQL dialect 1, but as 64-bit in x.php on line 27
Dynamic SQL Error SQL error code = -104 as approximate floating-point values in SQL dialect 1, but as 64-bit

jdbc
GDS Exception. 335544569. Dynamic SQL Error
SQL error code = -104
Unexpected end of command - line 1, column 11

select '"'"'
                 */
                else if (Pattern.compile(".*function\\.ibase-query.*", Pattern.DOTALL).matcher(pageSource).matches()) {
                    vendor = Vendor.FIREBIRD;
                }
                else if (Pattern.compile(".*derby.*", Pattern.DOTALL).matcher(pageSource).matches()) {
                    vendor = Vendor.DERBY;
                }
                else if (Pattern.compile(".*cubrid.*", Pattern.DOTALL).matcher(pageSource).matches()) {
                    vendor = Vendor.CUBRID;
                }
                else if (Pattern.compile(".*teradata.*", Pattern.DOTALL).matcher(pageSource).matches()) {
                    vendor = Vendor.TERADATA;
                }
                else if (Pattern.compile(".*SQLite.*", Pattern.DOTALL).matcher(pageSource).matches()) {
                    vendor = Vendor.SQLITE;
                }
                else if (Pattern.compile(".*h2 database.*", Pattern.DOTALL).matcher(pageSource).matches()) {
                    vendor = Vendor.H2;
                }
            } catch (InterruptedException | ExecutionException e) {
                LOGGER.error("Interruption while determining the type of database", e);
            }
        }
        
        // End the job
        try {
            taskExecutor.shutdown();
            taskExecutor.awaitTermination(15, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            LOGGER.error(e.getMessage(), e);
            Thread.currentThread().interrupt();
        }
        
        // TODO vendor
        return vendor;
    }
    
}