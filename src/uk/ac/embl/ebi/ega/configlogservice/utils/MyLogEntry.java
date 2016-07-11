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

public class MyLogEntry {
    private final String user;
    private final String fileID;
    private final String[] filePath; // abs.path1/abs.path2/size/rel.path
    private final String reKey;
    private final String reFormat;
    
    public MyLogEntry(String user, String fileID, String[] filePath, String reKey, String reFormat) {
        this.user = user;
        this.fileID = fileID;
        this.filePath = new String[filePath.length];
        System.arraycopy(filePath, 0, this.filePath, 0, filePath.length);
        this.reKey = reKey;
        this.reFormat = reFormat;
    }
    
    public String getUser() {
        return this.user;
    }
    
    public String getFileID() {
        return this.fileID;
    }
    
    public String[] getFilePath() {
        return this.filePath;
    }
    
    public String getReKey() {
        return this.reKey;
    }
    
    public String getReFormat() {
        return this.reFormat;
    }
}
