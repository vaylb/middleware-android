package com.njupt.middleware.media;

/**
 * Created by vaylb on 16-10-12.
 */
public class Media {
    public static final int TYPE_MEDIA_AUDIO = 0x02;
    public static final int TYPE_MEDIA_VIDEO = 0x03;
    public static final int TYPE_MEDIA_PRINTERFILE = 0x04;
    public static final int TYPE_DRIVER = 0x05;
    public static final int TYPE_SCREEN_FRAMES = 0x06;
    private int type;
    private int mediasize = -1;
    public Media(int type){
        this.type = type;
    }

    public int getMediaType(){
        return type;
    }

    public String getFileName() {
        return "media";
    }

    public int getMediaSize(){
        return this.mediasize;
    }

    public  void setMediaSize(int size){
        this.mediasize = size;
    }

    @Override
    public String toString() {
        return "class Media";
    }
}
