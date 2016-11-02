package com.njupt.middleware;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.net.wifi.WifiConfiguration;
import android.net.wifi.WifiManager;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.view.MotionEvent;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.Toast;

import java.lang.ref.WeakReference;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;

/**
 * Created by vaylb on 16-10-30.
 */
public class SetUpDeviceActivity extends Activity implements View.OnTouchListener,View.OnClickListener{
    private ImageButton wifiBtn;
    private Button setupBtn,completeBtn;
    private EditText netName,netPsw;
    private WifiManager mWifiManager;
    public volatile boolean wifiFlag;
    private DeviceManager mDeviceManager;
    private Handler mHandler;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_setupdevice);
        wifiBtn = (ImageButton)findViewById(R.id.wifi);
        completeBtn = (Button)findViewById(R.id.complete);
        setupBtn = (Button)findViewById(R.id.setup);
        wifiBtn.setOnTouchListener(this);
        completeBtn.setOnTouchListener(this);
        setupBtn.setOnClickListener(this);
        netName = (EditText)findViewById(R.id.network_name);
        netPsw = (EditText)findViewById(R.id.network_psw);

        mWifiManager = (WifiManager) this.getSystemService(Context.WIFI_SERVICE);
        mHandler = new MyHandler(this);
        mDeviceManager = new DeviceManager(this,mHandler);
    }

    // ------------------------------------------------
    // 打开WIFI
    // ------------------------------------------------
    public void openWifi() {
        if (isWifiApEnabled()) {
            return;
        }
        wifiFlag = !wifiFlag;
        setWifiApEnabled(wifiFlag, "Middleware", "nupt-middleware");
    }

    // 开启wifi
    public Boolean setWifiApEnabled(Boolean enabled, String name, String psw) {
        if (enabled) {
            mWifiManager.setWifiEnabled(false);
        }
        try {
            WifiConfiguration netConfig = new WifiConfiguration();
            netConfig.SSID = name;
            netConfig.preSharedKey = psw;
            netConfig.allowedAuthAlgorithms
                    .set(WifiConfiguration.AuthAlgorithm.OPEN);
            netConfig.allowedProtocols.set(WifiConfiguration.Protocol.RSN);
            netConfig.allowedProtocols.set(WifiConfiguration.Protocol.WPA);
            netConfig.allowedKeyManagement
                    .set(WifiConfiguration.KeyMgmt.WPA_PSK);
            netConfig.allowedPairwiseCiphers
                    .set(WifiConfiguration.PairwiseCipher.CCMP);
            netConfig.allowedPairwiseCiphers
                    .set(WifiConfiguration.PairwiseCipher.TKIP);
            netConfig.allowedGroupCiphers
                    .set(WifiConfiguration.GroupCipher.CCMP);
            netConfig.allowedGroupCiphers
                    .set(WifiConfiguration.GroupCipher.TKIP);
            Method method = mWifiManager.getClass().getMethod(
                    "setWifiApEnabled", WifiConfiguration.class, Boolean.TYPE);
            return (Boolean) method.invoke(mWifiManager, netConfig, enabled);
        } catch (Exception e) {
            e.printStackTrace();
            return false;
        }

    }

    public boolean isWifiApEnabled() {
        boolean isEnabled = false;
        try {
            Method method = mWifiManager.getClass().getMethod("isWifiApEnabled");
            isEnabled = (Boolean) method.invoke(mWifiManager);
        } catch (IllegalAccessException e) {
            e.printStackTrace();
        } catch (IllegalArgumentException e) {
            e.printStackTrace();
        } catch (InvocationTargetException e) {
            e.printStackTrace();
        } catch (NoSuchMethodException e) {
            e.printStackTrace();
        }
        return isEnabled;
    }

    @Override
    public boolean onTouch(View v, MotionEvent event) {
        switch (v.getId()){
            case R.id.wifi:
                if (event.getAction() == MotionEvent.ACTION_DOWN) {
                    wifiBtn.setBackgroundResource(R.drawable.wifi_press);
                } else if (event.getAction() == MotionEvent.ACTION_UP) {
                    wifiBtn.setBackgroundResource(R.drawable.wifi);
                    openWifi();
                }
                break;
            case R.id.complete:
                finish();
                break;
        }
        return true;
    }

    @Override
    public void onClick(View v) {
        switch (v.getId()){
            case R.id.setup:
                if(netName.getText().toString().length()==0){
                    Toast.makeText(getApplication(),"请输入局域网名称", Toast.LENGTH_SHORT).show();
                    return;
                }
                if(netPsw.getText().toString().length()==0){
                    Toast.makeText(getApplication(),"请输入局域网密码", Toast.LENGTH_SHORT).show();
                    return;
                }
                mDeviceManager.executeRunnable(new SetUpThread(mDeviceManager,netName.getText().toString(),netPsw.getText().toString()));
                mDeviceManager.doRepeatBroadCast(UdpOrder.SETUP,36);
                break;
        }
    }

    private static class MyHandler extends Handler {
        private final WeakReference<SetUpDeviceActivity> mActivity;

        public MyHandler(SetUpDeviceActivity activity) {
            mActivity = new WeakReference<SetUpDeviceActivity>(activity);
        }

        @Override
        public void handleMessage(Message msg) {
            SetUpDeviceActivity activity = mActivity.get();
            if (msg.what == 1) {
                Toast.makeText(activity, "配置成功",
                        Toast.LENGTH_SHORT).show();
                if(activity != null){
                    activity.setWifiApEnabled(false,"","");
                    activity.mWifiManager.setWifiEnabled(true);
                    activity.finish();
                }

            }

        }

    }
}
