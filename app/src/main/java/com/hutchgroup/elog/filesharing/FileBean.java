package com.hutchgroup.elog.filesharing;

public class FileBean {

    String fileExtension,fileType,fileName,path,createdBy,createdDate;

    int fileContentLength,id,Syncfg;

    public int getSyncfg() {
        return Syncfg;
    }

    public void setSyncfg(int syncfg) {
        Syncfg = syncfg;
    }

    public String getFileExtension() {
        return fileExtension;

    }

    public void setFileExtension(String fileExtension) {
        this.fileExtension = fileExtension;
    }

    public String getFileType() {
        return fileType;
    }

    public void setFileType(String fileType) {
        this.fileType = fileType;
    }

    public String getFileName() {
        return fileName;
    }

    public void setFileName(String fileName) {
        this.fileName = fileName;
    }

    public String getPath() {
        return path;
    }

    public void setPath(String path) {
        this.path = path;
    }

    public String getCreatedBy() {
        return createdBy;
    }

    public void setCreatedBy(String createdBy) {
        this.createdBy = createdBy;
    }

    public String getCreatedDate() {
        return createdDate;
    }

    public void setCreatedDate(String createdDate) {
        this.createdDate = createdDate;
    }

    public int getFileContentLength() {
        return fileContentLength;
    }

    @Override
    public String toString() {
        return getFileName().toLowerCase();
    }

    public void setFileContentLength(int fileContentLength) {
        this.fileContentLength = fileContentLength;
    }

    public int getId() {
        return id;
    }

    public void setId(int id) {
        this.id = id;
    }
}
