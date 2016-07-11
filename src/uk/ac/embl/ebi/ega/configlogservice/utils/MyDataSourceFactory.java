/*
 * Copyright 2014 EMBL-EBI.
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

import javax.sql.DataSource;
import com.mysql.jdbc.jdbc2.optional.MysqlDataSource;
import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import java.sql.SQLException;
import java.util.ArrayList;
import org.postgresql.ds.PGSimpleDataSource;
 
public class MyDataSourceFactory {
 
    public static DataSource getMySQLDataSource(String instance, String port, String database, String user, String pass) {
        MysqlDataSource mysqlDS = null;
        mysqlDS = new MysqlDataSource();
        mysqlDS.setURL("jdbc:mysql://"+instance+":"+port+"/"+database);
        mysqlDS.setUser(user);
        mysqlDS.setPassword(pass);
        return mysqlDS;
    }    
    public static DataSource getMySQLDataSource(ArrayList<String> config) {
        MysqlDataSource mysqlDS = null;
        mysqlDS = new MysqlDataSource();
        mysqlDS.setURL("jdbc:mysql://"+config.get(0)+":"+config.get(1)+"/"+config.get(2));
        mysqlDS.setUser(config.get(3));
        mysqlDS.setPassword(config.get(4));
        return mysqlDS;
    }    

    // Connect to a PostgreSQL Database
    public static DataSource getPostgresDataSource(String instance, String port, String database, String user, String pass) throws SQLException {
        PGSimpleDataSource pgDS = null;
        pgDS = new PGSimpleDataSource();
        pgDS.setUrl("jdbc:postgresql://"+instance+":"+port+"/"+database);
        pgDS.setUser(user);
        pgDS.setPassword(pass);
        return pgDS;
    }
    public static DataSource getPostgresDataSource(ArrayList<String> config) throws SQLException {
        PGSimpleDataSource pgDS = null;
        pgDS = new PGSimpleDataSource();
        pgDS.setUrl("jdbc:postgresql://"+config.get(0)+":"+config.get(1)+"/"+config.get(2));
        pgDS.setUser(config.get(3));
        pgDS.setPassword(config.get(4));
        return pgDS;
    }

    
    // New (22/01/2015) -- Hikari Pooled Data Source
    public static DataSource getHikariDataSource(String instance, String port, String database, String user, String pass) {        
        HikariConfig config = new HikariConfig();
        config.setMinimumIdle(0);
        config.setMaximumPoolSize(10);
        //config.setConnectionTimeout(5000);
        //config.setConnectionTestQuery("VALUES 1");

        config.setDataSourceClassName("org.postgresql.ds.PGSimpleDataSource");
        //config.setDataSourceClassName("com.mysql.jdbc.jdbc2.optional.MysqlDataSource");
        config.addDataSourceProperty("serverName", instance);
        config.addDataSourceProperty("portNumber", port);
        config.addDataSourceProperty("databaseName", database);
        config.addDataSourceProperty("user", user);
        config.addDataSourceProperty("password", pass);
        config.addDataSourceProperty("tcpKeepAlive", true);

        HikariDataSource hikariDataSource = new HikariDataSource(config);
        
        return hikariDataSource;
    }    
    public static DataSource getHikariDataSource(ArrayList<String> config_) {        
        HikariConfig config = new HikariConfig();
        config.setMinimumIdle(0);
        config.setMaximumPoolSize(10);
        //config.setConnectionTimeout(5000);
        //config.setConnectionTestQuery("VALUES 1");

        config.setDataSourceClassName("org.postgresql.ds.PGSimpleDataSource");
        //config.setDataSourceClassName("com.mysql.jdbc.jdbc2.optional.MysqlDataSource");
        config.addDataSourceProperty("serverName", config_.get(0));
        config.addDataSourceProperty("portNumber", config_.get(1));
        config.addDataSourceProperty("databaseName", config_.get(2));
        config.addDataSourceProperty("user", config_.get(3));
        config.addDataSourceProperty("password", config_.get(4));
        config.addDataSourceProperty("tcpKeepAlive", true);

        HikariDataSource hikariDataSource = new HikariDataSource(config);
        
        return hikariDataSource;
    }    

}         
