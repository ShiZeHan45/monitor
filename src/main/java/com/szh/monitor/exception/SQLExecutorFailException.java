package com.szh.monitor.exception;

import java.util.List;

public class SQLExecutorFailException extends RuntimeException{
    private List<String> failSQLFiles;

    public SQLExecutorFailException(String message){
        super(message);
    }

    public SQLExecutorFailException(String message,List<String> failSQLFiles){
        super(message);
        this.failSQLFiles = failSQLFiles;
    }

    public List<String> getFailSQLFiles() {
        return failSQLFiles;
    }

    public void setFailSQLFiles(List<String> failSQLFiles) {
        this.failSQLFiles = failSQLFiles;
    }
}
