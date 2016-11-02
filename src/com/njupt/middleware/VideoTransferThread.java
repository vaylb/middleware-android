
package com.njupt.middleware;

import android.media.MediaExtractor;
import android.media.MediaFormat;
import android.os.Environment;
import android.os.Message;
import android.util.Log;

import java.io.DataOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.SocketTimeoutException;
import java.nio.ByteBuffer;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

public class VideoTransferThread implements Runnable {
    private static final String TAG = "VideoTransferThread";
    private static final int DEFAULT_PORT_TCP = 40005;

    private DeviceManager mDeviceManager;

    public volatile boolean TcpFlag = true;
    private volatile boolean hasConnect = false;

    private static ServerSocket serverSocket = null;
    private ConcurrentHashMap<String, Socket> socketsMap = null;
    private FileInputStream stream_in = null;
    private int mTargetNum = -1;

    public VideoTransferThread(DeviceManager dm,File file,int targetNum) {
        this.mDeviceManager = dm;
        this.socketsMap = new ConcurrentHashMap<String, Socket>();
        mTargetNum = targetNum;
        try {
            //FaceTimeEveryDay.264
            this.stream_in = new FileInputStream(file);
//			this.stream_in = new FileInputStream(new File(Environment.getExternalStorageDirectory()+"/Movies/AppleLifeoniPad-moov.mp4"));
//			this.stream_in = new FileInputStream(new File(Environment.getExternalStorageDirectory()+"/screenrecord.264"));
		} catch (FileNotFoundException e) {
			e.printStackTrace();
		}
    }

    @Override
    public void run() {
    	int size = 4096*2;
    	byte data[] = new byte[size];
    	
        try {
            if(serverSocket ==  null) {
                serverSocket = new ServerSocket(DEFAULT_PORT_TCP);
                serverSocket.setReuseAddress(true);
                serverSocket.setSoTimeout(50000);
            }

            while (TcpFlag) {
				if (!hasConnect) {
					do{
						Log.e(TAG, "TCP listen");
						Socket socket = serverSocket.accept();
						socket.setSoTimeout(50000);
						socketsMap.put(socket.getInetAddress().getHostAddress(),socket);
					}while (socketsMap.size() < mTargetNum);
					Log.d(TAG, "vaylb->Tcp listen, total video device: "+ mTargetNum);
					hasConnect = true;
				} else {
					DataOutputStream outputStream;

					if(stream_in.read(data) > 0){
						for(ConcurrentMap.Entry<String,Socket> e: socketsMap.entrySet() ){
	                    	outputStream = new DataOutputStream(e.getValue().getOutputStream());
	                    	outputStream.write(data);
	                    	outputStream.flush();
	            		}
					}else TcpFlag = false;
				}
				Thread.sleep(2);
            }
        }catch (SocketTimeoutException e) {
            e.printStackTrace();
            Message message=new Message();
            message.what=7;
            mDeviceManager.mHandler.sendMessage(message);
        }
        catch (Exception e) {
            Log.d(TAG, "vaylb->tcp Exception");
            e.printStackTrace();
        } finally {
            Log.d(TAG, "vaylb->Tcp thread end");
            try {
            	for(ConcurrentMap.Entry<String,Socket> e: socketsMap.entrySet())
            		e.getValue().close();
            	if (serverSocket != null) serverSocket.close();
    		} catch (IOException e) {
    			e.printStackTrace();
    		}
            
        }

    }

}
