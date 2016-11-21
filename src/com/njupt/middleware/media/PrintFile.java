package com.njupt.middleware.media;

/**
 * Created by vaylb on 16-11-21.
 */
public class PrintFile extends Media {
    private String fileName;
    private String filePath;
    public PrintFile() {
        super(Media.TYPE_MEDIA_PRINTERFILE);
    }
    public PrintFile(String name, String path){
        super(Media.TYPE_MEDIA_PRINTERFILE);
        this.fileName = name;
        this.filePath = path;
    }
    public void setFileName(String name){
        this.fileName = name;
    }
    public void setFilePath(String path){
        this.filePath = path;
    }
    public String getFileName(){
        return this.fileName;
    }
    public String getFilePath(){
        return this.filePath;
    }
    public String toString(){
        return "PrintFile:[name="+fileName+", path="+filePath+"]";
    }
}
