
package com.njupt.middleware;

import android.media.AudioManager;
import android.os.Message;
import android.util.Log;

import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.util.concurrent.ConcurrentMap;

import org.json.JSONObject;

import com.njupt.middleware.struct.Device;

public class ReceiveUdp implements Runnable {
    private final static int PORT = 40001;// local port
    private final static String TAG = "ReceiveUdp";
    private DatagramSocket socket = null;
    private int preVolume = 14;
    public volatile boolean runFlag = true;
    private DeviceManager mDeviceManager;
    public volatile boolean hasInit = false;
    private int slave_ready_count = 0;

    public ReceiveUdp(DeviceManager manager) {
        this.mDeviceManager = manager;
    }

    public void stop() {
        runFlag = false;
        if (socket != null)
            socket.close();
    }

    @Override
    public void run() {
        // TODO Auto-generated method stub
        try {
            socket = new DatagramSocket(PORT);
            socket.setReuseAddress(true);
            byte[] data = new byte[512];
            DatagramPacket receive = new DatagramPacket(data, data.length);
            while (runFlag) {
                socket.receive(receive);
                String result = new String(receive.getData(), 0, receive.getLength());
                
                //mDeviceManager.hostExecutor.execute(new AckUdp(mDeviceManager,result, receive.getAddress(), receive.getPort()));
                Log.d(TAG, "vaylb->receive udp " + UdpOrder.request.get(result));
                // 判断信令
                if (result.equals(UdpOrder.DEVIDE_SCANE)) {
                	JSONObject object = new JSONObject();
					//mDeviceManager.addSlaveIp(receive.getAddress());
					Message message = new Message();
					message.what = 9;
					message.obj = receive.getAddress().getHostName();
					mDeviceManager.mHandler.sendMessage(message);
                }
//                else if (result.equals(UdpOrder.START_PLAY)) {
//                	//统计接收到的START_PLAY数目，从机全部可以播放时，通知各从机开始播放。
//                	slave_ready_count++;
//                	if(slave_ready_count==mDeviceManager.slaveAddressMap.size()){
//                		mDeviceManager.commandCast(UdpOrder.START_RETURN);
//                		slave_ready_count = 0;
//                		mDeviceManager.slave_init_stat = true; //置为true后可通过spinner进行模式调节
//                	}
//                    long time_java = System.currentTimeMillis();
//                    HostPlay.native_setplayflag(time_java);
//                    mDeviceManager.nativeStartPlay = true;
//                    mDeviceManager.playback_stat = true;
//                }
//                else if (result.equals(UdpOrder.SLVAE_CALL_COME)) {
//                    Message message = new Message();
//                    message.what = 1;
//                    mDeviceManager.mHandler.sendMessage(message);
//                    Log.d(TAG, "receive slave call coming");
//                    preVolume = mDeviceManager.mAudioManager
//                            .getStreamVolume(AudioManager.STREAM_MUSIC);
//                    mDeviceManager.mAudioManager.setStreamMute(AudioManager.STREAM_MUSIC, true);
//                }
//                else if (result.equals(UdpOrder.SLAVE_CALL_GO)) {
//                    Log.d(TAG, "receive slave call going");
//                    if (mDeviceManager.nativeStartPlay) {
//                        Message message = new Message();
//                        message.what = 2;
//                        mDeviceManager.mHandler.sendMessage(message);
//                        mDeviceManager.mAudioManager.setStreamMute(AudioManager.STREAM_MUSIC, false);
//                        mDeviceManager.mAudioManager.setStreamVolume(AudioManager.STREAM_MUSIC,
//                                preVolume,
//                                AudioManager.FLAG_SHOW_UI);
//                    }
//
//                }
//                else if (result.equals(UdpOrder.SLAVE_EXIT)) {
//                	Log.d(TAG, "vaylbzhao->receive slave exit");
//                	mDeviceManager.removeTCPSlave(receive.getAddress().getHostAddress());
//                    Message message = new Message();
//                    message.what = 3;
//                    message.obj = receive.getAddress().getHostName();
//                    mDeviceManager.mHandler.sendMessage(message);
//                }
            }
        } catch (IOException exception) {
            exception.printStackTrace();
        } finally {
            if (socket != null)
                socket.close();
        }

    }

}
