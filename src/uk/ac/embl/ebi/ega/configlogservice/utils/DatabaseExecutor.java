/*
 * Copyright 2015 EMBL-EBI.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package uk.ac.embl.ebi.ega.configlogservice.utils;

import java.io.File;
import java.io.IOException;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.logging.Level;
import java.util.logging.Logger;
import javax.sql.DataSource;
import org.ini4j.Ini;
import org.ini4j.Profile.Section;

/**
 *
 * @author asenf
 */
public class DatabaseExecutor {
    // Ini file location default
    private String iniFile = "configlog.ini";
    
    // Database Pool
    private DataSource dbSource;
    
    // Query Strings (populated from ini file)
    private String log_entry = null;
    private String event_entry = null;
    
    public DatabaseExecutor(String iniFile) {
        if (iniFile!=null)
            this.iniFile = iniFile;
        
        // Read Ini File, configure database
        Ini ini = null;
        try {
            ini = new Ini(new File(this.iniFile));
        } catch (IOException ex) {
            Logger.getLogger(DatabaseExecutor.class.getName()).log(Level.SEVERE, null, ex);
        }

        // Read initialization file 
        if (ini != null) {
            // Database Connection ---------------------------------------------
            Section section = ini.get("database");
            
            // Configure database pool with it
            String instance = "";
            if (section.containsKey("instance"))
                instance = section.get("instance");
            String port = "";
            if (section.containsKey("port"))
                    port = section.get("port");
            String database = "";
            if (section.containsKey("database"))
                database = section.get("database");
            String user = "";
            if (section.containsKey("username"))
                user = section.get("username");
            String pass = "";
            if (section.containsKey("password"))
                pass = section.get("password");
            
            this.dbSource = MyDataSourceFactory.getHikariDataSource(instance, port, database, user, pass);
            
            // Populate query strings with it ----------------------------------
            Section queries = ini.get("queries");
            
            if (queries.containsKey("log_entry"))
                this.log_entry = queries.get("log_entry");
            if (queries.containsKey("event_entry"))
                this.event_entry = queries.get("event_entry");
        }
    }
    
    // -------------------------------------------------------------------------
    // --- Getters and Setters -------------------------------------------------
    // -------------------------------------------------------------------------

    // TODO
    
    // -------------------------------------------------------------------------
    // --- Database Execution Functions ----------------------------------------
    // -------------------------------------------------------------------------
    
    public int writeEntry(String user, String ip, String server, String fileid, String dspeed, String dstatus, String dprotocol, String fileformat) {
        int result = -1;
        
        Connection conn = createConnection();
        if (conn != null) {
            PreparedStatement ps = null;
            try {
                ps = conn.prepareStatement(this.log_entry);
                ps.setString(1, ip);
                ps.setString(2, server);
                ps.setString(3, user);
                ps.setString(4, fileid);
                ps.setString(5, dspeed);
                ps.setString(6, dstatus);
                ps.setString(7, dprotocol);
                ps.setString(8, fileformat);
        
                // Execute query
                result = ps.executeUpdate();
                
            } catch (SQLException ex) {
                Logger.getLogger(DatabaseExecutor.class.getName()).log(Level.SEVERE, null, ex);
            } finally {
                closeQuitely(ps);
            }
        }
        
        return result;
    }
    
    public int writeEvent(String user, String ip, String event, String ticket, String session, String type) {
        int result = -1;
        
        Connection conn = createConnection();
        if (conn != null) {
            PreparedStatement ps = null;
            try {
                ps = conn.prepareStatement(this.event_entry);
                ps.setString(1, user);
                ps.setString(2, ip);
                ps.setString(3, event);
                ps.setString(4, ticket);
                ps.setString(5, session);
                ps.setString(6, type);
        
                // Execute query
                result = ps.executeUpdate();
                
            } catch (SQLException ex) {
                Logger.getLogger(DatabaseExecutor.class.getName()).log(Level.SEVERE, null, ex);
            } finally {
                closeQuitely(ps);
            }
        }
        
        return result;
    }
    
    // -------------------------------------------------------------------------
    // --- DB Access Functions -------------------------------------------------
    // -------------------------------------------------------------------------

    private Connection createConnection() {
        Connection connection = null;
        try {
            connection = this.dbSource.getConnection();
            connection.setAutoCommit(true);
        } catch (SQLException ex) {
            Logger.getLogger(DatabaseExecutor.class.getName()).log(Level.SEVERE, null, ex);
        }

        return connection;
    }    
    
    private Connection createConnection(DataSource dataSource) throws SQLException {
        Connection connection = dataSource.getConnection();
        connection.setAutoCommit(true);

        return connection;
    }    
    
    // -------------------------------------------------------------------------
    // --- Close connections without considering error messages ----------------
    // -------------------------------------------------------------------------

    private void closeQuitely(Statement stmt) {
        if(stmt != null) {
	    Connection con = null;
	    try {
		con = stmt.getConnection();
	    } catch (SQLException e) {
	    }
	    try {
                stmt.close();
            } catch (SQLException e) {
                // ignore
            }
	    closeQuitely(con);
        }
    }

    private void closeQuitely(ResultSet rs) {
        if(rs != null) {
            try {
                rs.close();
            } catch (SQLException e) {
                // ignore
            }
        }
    }

    private void closeQuitely(Connection con) {
	if (con != null) {
	    try {
		con.close();
	    } catch (SQLException e) {
		// ignore
	    }
	}
    }

}
