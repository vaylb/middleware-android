package com.njupt.middleware;

import java.util.HashMap;

public class UdpOrder {
    public static final String DEVIDE_SCANE = "a"; // 定义命令字
    public static final String DEVIDE_PREPARE_AUDIO = "b"; //aac
    public static final String DEVIDE_PREPARE_VIDEO = "c"; //jpeg
    public static final String DEVIDE_PREPARE_VIDEO_COMPRESSED = "d"; //h264
    public static final String DEVIDE_PREPARE_AUDIO_PCM = "e"; //pcm
    public static final String EXIT = "f";
    public static final String SETUP = "g";
    public static final String DEVICE_PREPARE_FILE_PRINT = "h"; //file
    public static final String DEVICE_PREPARE_DRIVER = "i"; //driver

    public static HashMap<String, String> request=new HashMap<String, String>();
    static{
        request.put("a", "DEVIDE_SCANE");
        request.put("b", "DEVIDE_PREPARE_AUDIO");
        request.put("c", "DEVIDE_PREPARE_VIDEO");
        request.put("d", "DEVIDE_PREPARE_VIDEO_COMPRESSED");
        request.put("e", "DEVIDE_PREPARE_AUDIO_PCM");
        request.put("f", "EXIT");
        request.put("g", "SETUP");
        request.put("h", "DEVICE_PREPARE_FILE_PRINT");
        request.put("i", "DEVICE_PREPARE_DRIVER");
    }
}
