package com.njupt.middleware.media;

/**
 *
 * @Description: 投影放映实体类
 * @author vaylb
 *
 */
public class ScreenFrames extends Media {

    private String fileName;


    public String getFileName() {
        return fileName;
    }

    public void setFileName(String fileName) {
        this.fileName = fileName;
    }

    public ScreenFrames(int mediatype) {
        super(mediatype);
        this.fileName = "投影放映";
    }

    public ScreenFrames(int mediatype, String fileName) {
        super(mediatype);
        this.fileName = fileName;
    }

    @Override
    public String toString() {
        return "ScreenFrames [fileName=" + fileName + "]";
    }
}
