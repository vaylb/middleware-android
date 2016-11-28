package com.njupt.middleware.media;

/**
 * Created by vaylb on 16-10-12.
 */
public class Media {
    public static final int TYPE_MEDIA_AUDIO = 0x02;
    public static final int TYPE_MEDIA_VIDEO = 0x03;
    public static final int TYPE_MEDIA_PRINTERFILE = 0x04;
    public static final int TYPE_DRIVER = 0x05;
    private int type;
    public Media(int type){
        this.type = type;
    }

    public int getMediaType(){
        return type;
    }

    public String getFileName() {
        return "media";
    }

    @Override
    public String toString() {
        return "class Media";
    }
}
