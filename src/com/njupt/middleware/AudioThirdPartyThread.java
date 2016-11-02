
package com.njupt.middleware;

import android.os.Message;
import android.util.Log;

import java.io.BufferedOutputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.SocketTimeoutException;
import java.nio.ByteBuffer;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

public class AudioThirdPartyThread implements Runnable {
    private static final String TAG = "AudioThirdPartyThread";
    private static final int DEFAULT_PORT_TCP = 40003;
    private final Lock lock = new ReentrantLock();
    private final Condition start = lock.newCondition();
    private final Condition isEmpty = lock.newCondition();

    private DeviceManager mDeviceManager;

    public volatile boolean TcpFlag = true;
    private volatile boolean startFlag = false;
    private volatile boolean stopFlag;
    private volatile boolean hasConnect;
    private volatile boolean signalByNative;

    private ServerSocket serverSocket = null;
    private ConcurrentHashMap<String, Socket> socketsMap = null;
    private ByteBuffer data;
    private int mCount;
    private int bufferSize;
    private int readPos;
    private boolean audio_params_send_flag = false;
    
    private int start_play_count = 0;

    public AudioThirdPartyThread(DeviceManager dm) {
        this.mDeviceManager = dm;
        this.data = dm.mThirdPartyBuffer;
        this.mCount = DeviceManager.mFrameCount;
        this.bufferSize=DeviceManager.DEFAULTCOUNT*DeviceManager.mFrameCount;
        this.socketsMap = new ConcurrentHashMap<String, Socket>();
    }
    
    public void removeSlaveSocket(String ip){
    	socketsMap.remove(ip);
    }

    public void start() {
        lock.lock();
        try {
            startFlag = true;
            stopFlag = false;
            start.signal();
        } finally {
            lock.unlock();
        }
    }

    /*
     * blocked here when first time connect tcp with slave
     */
    private void cheakStart() throws InterruptedException, IOException {
        lock.lock();
        try {
            while (!startFlag) {
                Log.d(TAG, "vaylb->wait in checkatart");
                start.await();
            }
        } finally {
            lock.unlock();
        }

    }
    /*
     * check sendbuffer if can read
     */
    private boolean checkCanRead() throws InterruptedException {
//        if(HostPlay.native_checkreadpos())
//            return true;
        boolean ret = true;
        lock.lock();
        try {
            while (!mDeviceManager.native_checkreadpos()) {
            	mDeviceManager.native_signaleToWrite();
                if (!isEmpty.await(10, TimeUnit.MILLISECONDS)) { // timeout then
                    ret = false;
                    break;
                }
            }
        } finally {
            lock.unlock();
        }
        return ret;
    }
    
    /*
     * called by native HostProcessThread, signal tcp thread to read
     */
    public void signalToRead() {
        lock.lock();
        try {
            isEmpty.signal();
            signalByNative = true;
        } finally {
            lock.unlock();
        }
    }

    /*
     * when standby is true, tcp thread should be stop
     */
    private void checkStop() {
        if (stopFlag)
        {
            try {
            	for(ConcurrentMap.Entry<String,Socket> e: socketsMap.entrySet() ){
                	e.getValue().close();
        		}
            	socketsMap.clear();
                hasConnect = false;
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }
    
    /*
     * called by native HostProcessThread when standby is true
     */
    public void stop() {
        stopFlag = true;
        startFlag = false;
    }

    /*
     * call when exit this app
     */
    public void quit(){
        TcpFlag=false;
        try {
        	for(ConcurrentMap.Entry<String,Socket> e: socketsMap.entrySet() ){
            	e.getValue().close();
    		}
		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
    	socketsMap.clear();
    }
    

    @Override
    public void run() {
        // TODO Auto-generated method stub
        try {
            serverSocket = new ServerSocket(DEFAULT_PORT_TCP);
            serverSocket.setReuseAddress(true);
            serverSocket.setSoTimeout(5000);
            int offset = 0;
            while (TcpFlag) {
                    if (!hasConnect) {
                        do{
    						Log.e(TAG, "TCP listen");
    						Socket socket = serverSocket.accept();

    						socket.setSoTimeout(50000);
    						socketsMap.put(socket.getInetAddress().getHostAddress(),socket);
    					}while (socketsMap.size() < mDeviceManager.getAudioDeviceNum());
                        hasConnect = true;
                        readPos=0;
                        Log.e(TAG, "vaylb->Tcp connected!");
                        cheakStart();
                    } else {
                    	OutputStream outputStream;
                    	if(!audio_params_send_flag){ //传递音频相关参数
    						int channels= 1, frequency = 48000, perframebits = 16;
    	                    for(ConcurrentMap.Entry<String,Socket> e: socketsMap.entrySet() ){
    	                    	outputStream = new DataOutputStream(e.getValue().getOutputStream());
    	                    	outputStream.write(BaseFunction.IntToByteArray(channels));
    	                    	outputStream.write(BaseFunction.IntToByteArray(frequency));
    	                    	outputStream.write(BaseFunction.IntToByteArray(perframebits));
    	                    	outputStream.flush();
    	            		}
    	                    audio_params_send_flag = true;
    					}
                    	
                        if (checkCanRead()) {
                            offset = readPos % bufferSize;
                            for(ConcurrentMap.Entry<String,Socket> e: socketsMap.entrySet() ){
                            	outputStream = new DataOutputStream(e.getValue().getOutputStream());
                            	outputStream.write(data.array(), offset, mCount << 1);
                            	outputStream.flush();
                    		}
                            
                            readPos += (mCount << 1);
                            mDeviceManager.native_setreadpos(readPos);
                            if (signalByNative) {
                                signalByNative = false;
                                mDeviceManager.native_signaleToWrite();
                            }
                            
                            start_play_count++;
                            if(start_play_count == 30) {
                            	Log.e(TAG, "-----------start play-----------");
                            	long time_java = System.currentTimeMillis();
                            	mDeviceManager.native_setplayflag(time_java);
								mDeviceManager.nativeStartPlay = true;
                            }
                           
						// when music player pause or change song, let check the
						// host and slave has written
//                            if (mDevideManager.native_needcheckwrited())
//                            	mDevideManager.getSlaveWrite();
//                            Log.d(TAG, "vaylbvaylbpzhao->Tcp send data success");
                            
                        }
                        checkStop();
                    }
            }
            Log.d(TAG, "vaylbpzhao->Tcp thread end");
        }catch (SocketTimeoutException e) {
            e.printStackTrace();
            Message message=new Message();
            message.what=7;
            mDeviceManager.mHandler.sendMessage(message);
        }
        catch (Exception e) {
            // TODO: handle exception
            Log.d(TAG, "vaylbpzhao->tcp Exception");
            e.printStackTrace();
        } finally {
            Log.d(TAG, "vaylbpzhao->Tcp thread end");
            try {
            	for(ConcurrentMap.Entry<String,Socket> e: socketsMap.entrySet() ){
                	e.getValue().close();
        		}
    		} catch (IOException e) {
    			// TODO Auto-generated catch block
    			e.printStackTrace();
    		}
            if (serverSocket != null)
                try {
                    serverSocket.close();
                } catch (IOException e) {
                    // TODO Auto-generated catch block
                    e.printStackTrace();
                }
        }

    }
    
    

}
