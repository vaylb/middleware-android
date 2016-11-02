package com.njupt.middleware;


import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.view.MotionEvent;
import android.view.View;
import android.view.View.OnTouchListener;
import android.widget.ImageButton;
import android.widget.Toast;

import com.njupt.middleware.media.Media;

import java.lang.ref.WeakReference;

public class MainActivity extends Activity implements OnTouchListener {
	private Handler mHandler;
	private DeviceManager mDeviceManager;
	private ImageButton scanBtn,setupBtn,deviceListBtn,playbackListBtn,playAudioBtn,playAudioOnlineBtn,playAudioThirdpartyBtn;
	private ImageButton playVideoBtn,playVideoOnlineBtn,playVideoThirdpartyBtn,playScreenRecord;

    @Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.activity_main);
		
		mHandler = new MyViewHandler(this);
		mDeviceManager = new DeviceManager(getApplicationContext(), mHandler);
		scanBtn = (ImageButton)findViewById(R.id.scan);
		setupBtn = (ImageButton)findViewById(R.id.setup);
		scanBtn.setOnTouchListener(this);
		setupBtn.setOnTouchListener(this);


		//audio button
		playAudioBtn = (ImageButton)findViewById(R.id.play_audio);
		playAudioThirdpartyBtn = (ImageButton)findViewById(R.id.play_audio_thirdparty);
		playAudioBtn.setOnTouchListener(this);
		playAudioThirdpartyBtn.setOnTouchListener(this);
		playAudioOnlineBtn = (ImageButton)findViewById(R.id.play_audio_online);
		playAudioOnlineBtn.setOnTouchListener(this);

		//video button
		playVideoBtn = (ImageButton)findViewById(R.id.play_video);
		playVideoOnlineBtn = (ImageButton)findViewById(R.id.play_video_online);
		playVideoThirdpartyBtn = (ImageButton)findViewById(R.id.play_video_thirdparty);
		playScreenRecord = (ImageButton)findViewById(R.id.play_screenrecord);
		playVideoBtn.setOnTouchListener(this);
		playVideoThirdpartyBtn.setOnTouchListener(this);
		playVideoOnlineBtn.setOnTouchListener(this);
		playScreenRecord.setOnTouchListener(this);

		//list btn
		deviceListBtn = (ImageButton)findViewById(R.id.device_list);
		playbackListBtn = (ImageButton)findViewById(R.id.playback_list);
		deviceListBtn.setOnTouchListener(this);
		playbackListBtn.setOnTouchListener(this);
	}

	@Override
	public boolean onTouch(View v, MotionEvent event) {
		switch (v.getId()) {
		case R.id.scan:
			if (event.getAction() == MotionEvent.ACTION_DOWN) {
				scanBtn.setBackgroundResource(R.drawable.init_press);
			} else if (event.getAction() == MotionEvent.ACTION_UP) {
				scanBtn.setBackgroundResource(R.drawable.init);
				mDeviceManager.startDeviceSacnListener();
//				mDeviceManager.startAudioPlayBack();
				mDeviceManager.doDeviceSacn();
			}
			break;
		case R.id.setup:
			if (event.getAction() == MotionEvent.ACTION_DOWN) {
				setupBtn.setBackgroundResource(R.drawable.wifi_press);
			} else if (event.getAction() == MotionEvent.ACTION_UP) {
				setupBtn.setBackgroundResource(R.drawable.wifi);
				Intent intent = new Intent(MainActivity.this, SetUpDeviceActivity.class);
				startActivity(intent);
			}
			break;
		case R.id.play_audio:
//			if (event.getAction() == MotionEvent.ACTION_DOWN) {
//				playAudioBtn.setBackgroundResource(R.drawable.play_press);
//				mDeviceManager.startAudioPlayBack();
//			} else if (event.getAction() == MotionEvent.ACTION_UP) {
//				playAudioBtn.setBackgroundResource(R.drawable.play);
//				mDeviceManager.doDevicesPrepare(UdpOrder.DEVIDE_PREPARE_AUDIO);
//			}
			if (event.getAction() == MotionEvent.ACTION_DOWN) {
				playAudioBtn.setBackgroundResource(R.drawable.play_press);
			} else if (event.getAction() == MotionEvent.ACTION_UP) {
				playAudioBtn.setBackgroundResource(R.drawable.play);
				Intent intent = new Intent(MainActivity.this,ListActivity.class);
				intent.putExtra("SHOW_MEDIA_LIST",true);
				intent.putExtra("SHOW_MEDIA_TYPE", Media.TYPE_MEDIA_AUDIO);
				intent.putExtra("SHOW_DEVICE_LIST",true);
				intent.putExtra("TITLE_STRING","选取音乐和设备");
				startActivity(intent);
			}
			break;

		case R.id.play_audio_online:
			if (event.getAction() == MotionEvent.ACTION_DOWN) {
				playAudioOnlineBtn.setBackgroundResource(R.drawable.play_onine_press);
				mDeviceManager.startAudioOnlinePlayBack();
			} else if (event.getAction() == MotionEvent.ACTION_UP) {
				playAudioOnlineBtn.setBackgroundResource(R.drawable.play_onine);
				mDeviceManager.doDevicesPrepare(UdpOrder.DEVIDE_PREPARE_AUDIO);
				Toast.makeText(this,"online",Toast.LENGTH_SHORT).show();
			}
			break;
		case R.id.play_audio_thirdparty:
			if (event.getAction() == MotionEvent.ACTION_DOWN) {
				playAudioThirdpartyBtn.setBackgroundResource(R.drawable.play_system_press);
				mDeviceManager.loadAudioNativeLib();
				mDeviceManager.setThirdPartyBuffer();
				mDeviceManager.startAudioThirdParty();
			} else if (event.getAction() == MotionEvent.ACTION_UP) {
				playAudioThirdpartyBtn.setBackgroundResource(R.drawable.play_system);
				mDeviceManager.doDevicesPrepare(UdpOrder.DEVIDE_PREPARE_AUDIO_PCM);
				mDeviceManager.audioThirdPartyStart();
			}
			break;
		case R.id.play_video:
//			if (event.getAction() == MotionEvent.ACTION_DOWN) {
//				playVideoBtn.setBackgroundResource(R.drawable.play_video_press);
//				mDeviceManager.startVideoPlayBack();
//			} else if (event.getAction() == MotionEvent.ACTION_UP) {
//				playVideoBtn.setBackgroundResource(R.drawable.play_video);
//				mDeviceManager.doDevicesPrepare(UdpOrder.DEVIDE_PREPARE_VIDEO_COMPRESSED);
//			}

			if (event.getAction() == MotionEvent.ACTION_DOWN) {
				playVideoBtn.setBackgroundResource(R.drawable.play_video_press);
			} else if (event.getAction() == MotionEvent.ACTION_UP) {
				playVideoBtn.setBackgroundResource(R.drawable.play_video);
				Intent intent = new Intent(MainActivity.this,ListActivity.class);
				intent.putExtra("SHOW_MEDIA_LIST",true);
				intent.putExtra("SHOW_MEDIA_TYPE", Media.TYPE_MEDIA_VIDEO);
				intent.putExtra("SHOW_DEVICE_LIST",true);
				intent.putExtra("TITLE_STRING","选取视频和设备");
				startActivity(intent);
			}
			break;

		case R.id.play_video_online:
			if (event.getAction() == MotionEvent.ACTION_DOWN) {
				playVideoOnlineBtn.setBackgroundResource(R.drawable.play_video_online_press);
				mDeviceManager.startVideoOnlinePlayBack();
			} else if (event.getAction() == MotionEvent.ACTION_UP) {
				playVideoOnlineBtn.setBackgroundResource(R.drawable.play_video_online);
				mDeviceManager.doDevicesPrepare(UdpOrder.DEVIDE_PREPARE_VIDEO_COMPRESSED);
				Toast.makeText(this,"在线视频",Toast.LENGTH_SHORT).show();
			}
			break;

		case R.id.play_video_thirdparty:
			if (event.getAction() == MotionEvent.ACTION_DOWN) {
				playVideoThirdpartyBtn.setBackgroundResource(R.drawable.play_video_movie_press);
				mDeviceManager.loadVideoNativeLib();
			} else if (event.getAction() == MotionEvent.ACTION_UP) {
				playVideoThirdpartyBtn.setBackgroundResource(R.drawable.play_video_movie);
				new Thread() {
					@Override
					public void run() {
						super.run();
						try {
							//FixMe use this time to switch from MiddleWare App to video player App, remove this hard code
							sleep(2500);
							mDeviceManager.doDevicesPrepare(UdpOrder.DEVIDE_PREPARE_VIDEO);
						} catch (InterruptedException e) {
							e.printStackTrace();
						}
					}
				}.start();
				mDeviceManager.setVideoNativeSlaveNum();
				mDeviceManager.videoNativeStart();

			}
			break;
		case R.id.play_screenrecord:
			if (event.getAction() == MotionEvent.ACTION_DOWN) {
				playScreenRecord.setBackgroundResource(R.drawable.play_video_system_press);
				mDeviceManager.setupScreenRecord();
			} else if (event.getAction() == MotionEvent.ACTION_UP) {
				playScreenRecord.setBackgroundResource(R.drawable.play_video_system);
				new Thread() {
					@Override
					public void run() {
						super.run();
						try {
							sleep(500);
							mDeviceManager.doDevicesPrepare(UdpOrder.DEVIDE_PREPARE_VIDEO);
//							mDeviceManager.doDevicesPrepare(UdpOrder.DEVIDE_PREPARE_VIDEO_COMPRESSED);
						} catch (InterruptedException e) {
							e.printStackTrace();
						}
					}
				}.start();

				new Thread() {
					@Override
					public void run() {
						super.run();
						mDeviceManager.setScreenRecordSlaveNum();
					}
				}.start();
//				mDeviceManager.startScreenRecord();
//				mDeviceManager.doDevicesPrepare(UdpOrder.DEVIDE_PREPARE_VIDEO_COMPRESSED);
				Toast.makeText(this,"截屏播放",Toast.LENGTH_SHORT).show();
			}
			break;

		case R.id.device_list:
			if (event.getAction() == MotionEvent.ACTION_DOWN) {
				deviceListBtn.setBackgroundResource(R.drawable.list_press);
			} else if (event.getAction() == MotionEvent.ACTION_UP) {
				deviceListBtn.setBackgroundResource(R.drawable.list);
				Intent intent = new Intent(MainActivity.this,ListActivity.class);
				intent.putExtra("SHOW_DEVICE_LIST",true);
				intent.putExtra("TITLE_STRING","设备列表");
				startActivity(intent);
			}
			break;

		case R.id.playback_list:
			if (event.getAction() == MotionEvent.ACTION_DOWN) {
				playbackListBtn.setBackgroundResource(R.drawable.playback_press);
			} else if (event.getAction() == MotionEvent.ACTION_UP) {
				playbackListBtn.setBackgroundResource(R.drawable.playback);
				Intent intent = new Intent(MainActivity.this,ListActivity.class);
				intent.putExtra("SHOW_PLAYBACK_LIST",true);
				intent.putExtra("TITLE_STRING","播放列表");
				startActivity(intent);
			}
			break;

		default:
			break;
		}

		return false;
	}
	
	private static class MyViewHandler extends Handler {
        private final WeakReference<MainActivity> mActivity;

        public MyViewHandler(MainActivity activity) {
            mActivity = new WeakReference<MainActivity>(activity);
        }

        @Override
        public void handleMessage(Message msg) {
            MainActivity activity = mActivity.get();
            if (msg.what == 0) {
                Toast.makeText(activity, "初始化完成",
                        Toast.LENGTH_SHORT).show();
            }
            else if (msg.what == 1) {
                Toast.makeText(activity, "从机来电，设置静音",
                        Toast.LENGTH_SHORT).show();
            }
            else if (msg.what == 2) {

                Toast.makeText(activity, "从机通话完成，音量恢复",
                        Toast.LENGTH_SHORT).show();
            }
            else if (msg.what == 3) {
//				if (activity.mhp.slaveAddressMap.size() == 0) {
//					Toast.makeText(activity, "所有从机已退出,恢复单独播放",
//							Toast.LENGTH_SHORT).show();
//					activity.mhp.nativeStartPlay = false;
//					//activity.mhp.getWriteUdp.stop();
//					activity.mhp.native_setstartflag(0);
//				}else{
//					Toast.makeText(activity, msg.obj + "已退出",
//							Toast.LENGTH_SHORT).show();
//				}

            } else if (msg.what == 5) {
                Toast.makeText(activity, "抱歉，主机出现错误",
                        Toast.LENGTH_SHORT).show();
            } else if (msg.what == 6) {
                Toast.makeText(activity, "从机出现错误，主机恢复单独播放",
                        Toast.LENGTH_SHORT).show();
//                activity.mhp.nativeStartPlay = false;
//                activity.mhp.getWriteUdp.stop();
//                activity.mhp.native_setstartflag(0);
            } else if (msg.what == 7) {
                Toast.makeText(activity, "从机已经退出，恢复单独播放",
                        Toast.LENGTH_SHORT).show();
//                activity.mhp.nativeStartPlay = false;
//                activity.mhp.getWriteUdp.stop();
//                activity.mhp.native_setstartflag(0);
            }
            else if (msg.what == 8) {
                Toast.makeText(activity, "Wifi热点被关闭，恢复单独播放",
                        Toast.LENGTH_SHORT).show();
//                activity.mhp.nativeStartPlay = false;
//                activity.mhp.getWriteUdp.stop();
//                activity.mhp.native_setstartflag(0);
            }
            else if (msg.what == 4) {
                Toast.makeText(activity, "正在退出..",
                        Toast.LENGTH_SHORT).show();
                new Thread() {

                    @Override
                    public void run() {
                        super.run();
                        try {
                            sleep(500);
                        } catch (InterruptedException e) {
                            e.printStackTrace();
                        }
                        Message msg = new Message();
                        msg.what = 88;
                        sendMessage(msg);
                    }

                }.start();
            }
            if (msg.what == 88) {
//                activity.mhp.exitingState = true;
//                activity.mhp.exit();
                android.os.Process.killProcess(android.os.Process.myPid());
                System.exit(0);

            }
            if (msg.what == 9) {
            	Toast.makeText(activity, "从机"+msg.obj+"已加入",
                        Toast.LENGTH_SHORT).show();
            }
        }

    }

	@Override
	public void onBackPressed() {
		super.onBackPressed();
		mDeviceManager.audioThirdPartyStop();
		mDeviceManager.doBroadCast(UdpOrder.EXIT);
		if(mDeviceManager.mAudioNativeLibLoadFlag)mDeviceManager.native_exit();
		if(mDeviceManager.mVideoNativeStartFlag)mDeviceManager.native_setvideohook(0);
		if(mDeviceManager.mScreenRecordStartFlag)mDeviceManager.stopScreenRecord();
	}

}
