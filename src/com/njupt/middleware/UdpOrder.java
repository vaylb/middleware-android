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

    public static final String WAKE_UP = "g";
    public static final String START_PLAY = "h";
    public static final String SLVAE_CALL_COME = "i";
    public static final String SLAVE_CALL_GO = "j";
    public static final String SCREEN_ON = "k";
    public static final String SCREEN_OFF = "l";
    public static final String SLAVE_EXIT="m";
    public static final String START_RETURN="n";
    public static final String GET_WRITED="o";
    public static final String INIT="p";
    public static final String SLAVE_GET_WRITE="q";
    public static final String GET_FRAME_COUNT="r";
    public static final String MODE_SYNC="s"; //同步播放模式
    public static final String MODE_REVERB="t"; //轻度混响
    public static final String MODE_KARA="u"; //卡拉OK
    
    public static HashMap<String, String> request=new HashMap<String, String>();
    static{
        request.put("a", "DEVIDE_SCANE");
        request.put("b", "DEVIDE_PREPARE_AUDIO");
        request.put("c", "DEVIDE_PREPARE_VIDEO");
        request.put("d", "DEVIDE_PREPARE_VIDEO_COMPRESSED");
        request.put("e", "DEVIDE_PREPARE_AUDIO_PCM");
        request.put("f", "EXIT");
        request.put("g", "SETUP");

        request.put("h", "START_PLAY");
        request.put("i", "SLVAE_CALL_COME");
        request.put("j", "SLAVE_CALL_GO");
        request.put("k", "SCREEN_ON");
        request.put("l", "SCREEN_OFF");
        request.put("m", "SLAVE_EXIT");
        request.put("n", "START_RETURN");
        request.put("o", "GET_WRITED");
        request.put("p", "INIT");
        request.put("q", "SLAVE_GET_WRITE");
        request.put("r", "GET_FRAME_COUNT");
        request.put("s", "MODE_SYNC");
        request.put("t", "MODE_REVERB");
        request.put("u", "MODE_KARA");

    }
}
