
package com.njupt.middleware;

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

public class AudioTransferThread implements Runnable {
    private static final String TAG = "DataTransferThread";
    private static final int DEFAULT_PORT_TCP = 40003;
    private final Lock lock = new ReentrantLock();

    private DeviceManager mDeviceManager;

    public volatile boolean TcpFlag = true;
    private volatile boolean hasConnect = false;

    private static ServerSocket serverSocket = null;
    private ConcurrentHashMap<String, Socket> socketsMap = null;

    private FileInputStream stream_in = null;
    private int mTargetNum = -1;

    public AudioTransferThread(DeviceManager dm, File file,int targetNum) {
        this.mDeviceManager = dm;
        this.socketsMap = new ConcurrentHashMap<String, Socket>();
        mTargetNum = targetNum;
        try {
            this.stream_in = new FileInputStream(file);
		} catch (FileNotFoundException e) {
			e.printStackTrace();
		}
    }

    @Override
    public void run() {
    	int size = 4096,read_tmp = 0;
    	byte data[] = new byte[size];
    	
        try {
            if(serverSocket ==  null){
                serverSocket = new ServerSocket(DEFAULT_PORT_TCP);
                serverSocket.setReuseAddress(true);
                serverSocket.setSoTimeout(50000);
            }
            
            while (TcpFlag) {
				if (!hasConnect) {
					do{
						Log.e(TAG, "TCP listen, current num "+socketsMap.size()+" total "+mTargetNum);
						Socket socket = serverSocket.accept();
						socket.setSoTimeout(50000);
						socketsMap.put(socket.getInetAddress().getHostAddress(),socket);
					}while (socketsMap.size() < mTargetNum);
					Log.d(TAG, "vaylb->Tcp listen, total audio device: "+ mTargetNum);
					hasConnect = true;
				} else { 
					DataOutputStream outputStream;
//					if(!audio_params_send_flag){ //传递音频相关参数
//						int channels= 2, frequency = 44100, perframebits = 16;
//	                    for(ConcurrentMap.Entry<String,Socket> e: socketsMap.entrySet() ){
//	                    	outputStream = new DataOutputStream(e.getValue().getOutputStream());
//	                    	outputStream.write(BaseFunction.IntToByteArray(channels));
//	                    	outputStream.write(BaseFunction.IntToByteArray(frequency));
//	                    	outputStream.write(BaseFunction.IntToByteArray(perframebits));
//	                    	outputStream.flush();
//	            		}
//	                    audio_params_send_flag = true;
//					}
					
					if((read_tmp = stream_in.read(data)) > 0){
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
    			// TODO Auto-generated catch block
    			e.printStackTrace();
    		}
            
        }

    }

}
