package com.njupt.middleware;

import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;

import org.json.JSONException;
import org.json.JSONObject;

import com.njupt.middleware.struct.Device;

import android.os.Message;
import android.util.Log;

public class DeviceScanListener implements Runnable{
	private final static int PORT = 40002;// local port
    private final static String TAG = "DeviceScanListener";
    private static DatagramSocket socket = null;
    public volatile boolean runFlag = true;
    private DeviceManager mDeviceManager;

    public DeviceScanListener(DeviceManager manager) {
        this.mDeviceManager = manager;
    }

    public void stop() {
        runFlag = false;
        if (socket != null) socket.close();
    }

    @Override
    public void run() {
        try {
            if(socket==null)socket = new DatagramSocket(PORT);
            socket.setReuseAddress(true);
            byte[] data = new byte[512];
            DatagramPacket receive = new DatagramPacket(data, data.length);
            while (runFlag) {
                socket.receive(receive);

                String result = new String(receive.getData(), 0, receive.getLength());
                
                //mDeviceManager.hostExecutor.execute(new AckUdp(mDeviceManager,result, receive.getAddress(), receive.getPort()));
                Log.d(TAG, "vaylb->DeviceScanListening receive device info: " + result);
				JSONObject object = new JSONObject(result);
                if(object.has("stat")){
                    if(object.getInt("stat")==0){
                        mDeviceManager.setDeviceJobDone(receive.getAddress().getHostAddress());
                    }
                }else{
                    Device device = new Device(object.optString("name"), object.optInt("type"), receive.getAddress());
                    mDeviceManager.addDevice(device);
                    Message message = new Message();
                    message.what = 9;
                    message.obj = object;
                    mDeviceManager.mHandler.sendMessage(message);
                }

            }
        } catch (IOException exception) {
            exception.printStackTrace();
        } catch (JSONException e) {
			e.printStackTrace();
		} finally {
            if (socket != null) socket.close();
        }

    }
}
