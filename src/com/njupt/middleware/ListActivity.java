package com.njupt.middleware;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.BaseAdapter;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.ListAdapter;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;

import com.njupt.middleware.media.AudioUtils;
import com.njupt.middleware.media.Media;
import com.njupt.middleware.media.Movie;
import com.njupt.middleware.media.Song;
import com.njupt.middleware.media.VideoUtils;
import com.njupt.middleware.struct.Device;

import java.io.File;
import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Created by vaylb on 16-10-11.
 */
public class ListActivity extends Activity {
    private String TAG = "ListActivity";
    private static final int TYPE_DEVICE = 0x01;
    private static final int TYPE_MEDIA = 0x02;
    private static final int SHOW_MODE_DEVICE_SINGLE = 0x03;
    private static final int SHOW_MODE_MEDIA_SINGLE = 0x04;
    private static final int SHOW_MODE_MULTI = 0x05;

    private Handler mHandler;
    private boolean mShowDeviceList, mShowMediaList, mShowPlaybackList;
    private ListView mDeviceListview, mMediaListview, mPlaybackListview;
    private TextView mTitle;
    private Button mStartplayBtn;
    private ImageView mDivideLine;
    private String mTitleStr = "";
    private List<Device> mDeviceData; //存储所有连接设备
    private List<Media> mMediaData = new ArrayList<Media>(); //存储所有媒体信息
    private MyListAdapter mDeviceMyListAdapter, mMediaMyListAdapter;
    private PlaybackListAdapter mPlaybackAdapter;
    private DeviceManager mDeviceManager;

    private int mCurrentSelectMediaPosition = -1;
    private Map<Integer, Device> mCurrentSelectDevice = new HashMap<Integer, Device>();
    private int mCurrentMediaType = -1;


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_list);
        mDeviceListview = (ListView) findViewById(R.id.list_device);
        mMediaListview = (ListView) findViewById(R.id.list_media);
        mPlaybackListview = (ListView)findViewById(R.id.list_playback);
        mTitle = (TextView) findViewById(R.id.title);
        mDivideLine = (ImageView)findViewById(R.id.divide_line);
        mStartplayBtn = (Button) findViewById(R.id.start_play);
        mStartplayBtn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (mCurrentSelectMediaPosition == -1) {
                    Toast.makeText(getApplication(), "请选择音乐", Toast.LENGTH_SHORT).show();
                    return;
                }
                if (mCurrentSelectDevice.size() == 0) {
                    Toast.makeText(getApplication(), "请选择播放设备", Toast.LENGTH_SHORT).show();
                    return;
                }
                playbackMedia(mCurrentSelectMediaPosition, mCurrentSelectDevice);
            }
        });


        mHandler = new ListViewHandler(this);
        mDeviceManager = new DeviceManager(getApplicationContext(), mHandler);

        initFlagStat();
        mTitle.setText(mTitleStr);
        if (mShowDeviceList && mShowMediaList) {
            mStartplayBtn.setVisibility(View.VISIBLE);
        } else {
            mStartplayBtn.setVisibility(View.GONE);
            mDivideLine.setVisibility(View.GONE);
        }

        if (mShowDeviceList) {
            if(mShowMediaList){
                if(mCurrentMediaType == Media.TYPE_MEDIA_AUDIO){
                    mDeviceData = getNotPlaybackDevice(mDeviceManager.getDeviceList(Device.TYPE_AUDIO));
                }else if(mCurrentMediaType == Media.TYPE_MEDIA_VIDEO){
                    mDeviceData = getNotPlaybackDevice(mDeviceManager.getDeviceList(Device.TYPE_VIDEO));
                }
            }else{
                mDeviceData = getNotPlaybackDevice(mDeviceManager.getDeviceList(-1));
            }

            mDeviceMyListAdapter = new MyListAdapter(this, TYPE_DEVICE);
            mDeviceListview.setAdapter(mDeviceMyListAdapter);
            if (mShowMediaList) { // 两个都显示是才可点击
                mDeviceListview.setOnItemClickListener(new AdapterView.OnItemClickListener() {
                    @Override
                    public void onItemClick(AdapterView<?> parent, View view, final int position, long id) {
                        if (mCurrentSelectDevice.containsKey(position)) {
                            view.setBackgroundResource(R.drawable.linearlayout_style);
                            ((TextView) view.findViewById(R.id.name)).setTextColor(getResources().getColor(R.color.list_item_textview_unpress));
                            ((TextView) view.findViewById(R.id.type)).setTextColor(getResources().getColor(R.color.list_item_textview_unpress));
                            unselectDevice(position);
                        } else {
                            view.setBackgroundResource(R.drawable.linearlayout_style_pressed);
                            ((TextView) view.findViewById(R.id.name)).setTextColor(getResources().getColor(R.color.list_item_textview_press));
                            ((TextView) view.findViewById(R.id.type)).setTextColor(getResources().getColor(R.color.list_item_textview_press));
                            selectDevice(position);
                        }
                    }
                });
            }

        } else {
            mDeviceListview.setVisibility(View.GONE);
        }

        if (mShowMediaList) {
            if(mCurrentMediaType == Media.TYPE_MEDIA_AUDIO){
                mMediaData = getNotPlaybackMedia(AudioUtils.getAllSongs(this));
            }else if(mCurrentMediaType == Media.TYPE_MEDIA_VIDEO){
                mMediaData = VideoUtils.getAllMovies(this);
            }

            mMediaMyListAdapter = new MyListAdapter(this, TYPE_MEDIA);
            mMediaListview.setAdapter(mMediaMyListAdapter);
            mMediaListview.setOnItemClickListener(new AdapterView.OnItemClickListener() {
                @Override
                public void onItemClick(AdapterView<?> parent, View view, final int position, long id) {
                    Log.d(TAG, mMediaData.get(position).toString());
                    if (mCurrentSelectMediaPosition != -1) {
                        View currentview = mMediaListview.getChildAt(mCurrentSelectMediaPosition);
                        currentview.setBackgroundResource(R.drawable.linearlayout_style);
                        ((TextView) currentview.findViewById(R.id.name)).setTextColor(getResources().getColor(R.color.list_item_textview_unpress));
                        ((TextView) currentview.findViewById(R.id.type)).setTextColor(getResources().getColor(R.color.list_item_textview_unpress));
                    }
                    view.setBackgroundResource(R.drawable.linearlayout_style_pressed);
                    ((TextView) view.findViewById(R.id.name)).setTextColor(getResources().getColor(R.color.list_item_textview_press));
                    ((TextView) view.findViewById(R.id.type)).setTextColor(getResources().getColor(R.color.list_item_textview_press));
                    selectMediaPosition(position);
                }
            });
        } else {
            mMediaListview.setVisibility(View.GONE);
        }

        if(mShowPlaybackList){
            mStartplayBtn.setVisibility(View.GONE);
            mDeviceListview.setVisibility(View.GONE);
            mMediaListview.setVisibility(View.GONE);
            mDivideLine.setVisibility(View.GONE);
            mPlaybackListview.setVisibility(View.VISIBLE);
            mPlaybackAdapter = new PlaybackListAdapter(this,getPlaybackMedia(mDeviceManager.currentPlaybackMap),mDeviceManager.currentPlaybackMap);
            mPlaybackListview.setAdapter(mPlaybackAdapter);
        }
    }

    public List<Device> getNotPlaybackDevice(List<Device> alldevices){
        List<Device> res = new ArrayList<Device>();
        for (Device device : alldevices) {
            boolean contain = false;
            for(HashMap.Entry<Media,List<Device>> e:mDeviceManager.currentPlaybackMap.entrySet()){
                if(e.getValue().contains(device)){
                    contain = true;
                    break;
                }
            }
            if(!contain){
                res.add(device);
            }
        }
        return res;
    }

    public List<Media> getNotPlaybackMedia(List<Media> allmedia){
        List<Media> res = new ArrayList<Media>();

        Set<Media> playbacksongs = mDeviceManager.currentPlaybackMap.keySet();
        for (Media media : allmedia) {
            boolean contain = false;
            for (Media playbacksong : playbacksongs) {
                if(playbacksong.getFileName().equals(media.getFileName())){
                    contain = true;
                }
            }
            if (!contain) {
                res.add(media);
            }
        }
        return res;
    }

    public List<Media> getPlaybackMedia(Map<Media,List<Device>> allmedia){
        List<Media> res = new ArrayList<Media>();
        Set<Media> medias = allmedia.keySet();
        for (Media media : medias) {
            res.add(media);
        }
        return res;
    }


    public void initFlagStat() {
        Intent intent = getIntent();
        mShowDeviceList = intent.getBooleanExtra("SHOW_DEVICE_LIST", false);
        mShowMediaList = intent.getBooleanExtra("SHOW_MEDIA_LIST", false);
        if(mShowMediaList){
            mCurrentMediaType = intent.getIntExtra("SHOW_MEDIA_TYPE",Media.TYPE_MEDIA_AUDIO);
        }
        mShowPlaybackList = intent.getBooleanExtra("SHOW_PLAYBACK_LIST", false);
        mTitleStr = intent.getStringExtra("TITLE_STRING");
    }

    public void selectMediaPosition(int pos) {
        mCurrentSelectMediaPosition = pos;
    }

    public void selectDevice(int pos) {
        mCurrentSelectDevice.put(pos, mDeviceData.get(pos));
    }

    public void unselectDevice(int pos) {
        if (mCurrentSelectDevice.containsKey(pos)) {
            mCurrentSelectDevice.remove(pos);
        }
    }


    public void playbackMedia(int position, final Map<Integer, Device> devices) {
        Media media = mMediaData.get(position);
        ArrayList<Device> playbackdevices = new ArrayList<Device>();
        for (HashMap.Entry<Integer,Device> e:devices.entrySet()){
            playbackdevices.add(e.getValue());
        }
        mDeviceManager.currentPlaybackMap.put(media,playbackdevices);

        Toast.makeText(getApplicationContext(), "即将播放\n" + mMediaData.get(position).getFileName(), Toast.LENGTH_SHORT).show();
        if(media.getMediaType() == Media.TYPE_MEDIA_AUDIO){
            mDeviceManager.startAudioPlayBack(new File(((Song) media).getFileUrl()), devices.size());
            new Thread() {
                @Override
                public void run() {
                    super.run();
                    try {
                        sleep(500);
                        mDeviceManager.doDevicesPrepare(UdpOrder.DEVIDE_PREPARE_AUDIO, devices);

                        Message message = new Message();
                        message.what = 0;
                        mDeviceManager.mHandler.sendMessage(message);
                    } catch (InterruptedException e) {
                        e.printStackTrace();
                    }
                }
            }.start();
        }else if(media.getMediaType() == Media.TYPE_MEDIA_VIDEO){
            mDeviceManager.startVideoPlayBack(new File(((Movie)media).getPath()),devices.size());
            new Thread() {
                @Override
                public void run() {
                    super.run();
                    try {
                        sleep(500);
                        mDeviceManager.doDevicesPrepare(UdpOrder.DEVIDE_PREPARE_VIDEO_COMPRESSED, devices);

                        Message message = new Message();
                        message.what = 0;
                        mDeviceManager.mHandler.sendMessage(message);
                    } catch (InterruptedException e) {
                        e.printStackTrace();
                    }
                }
            }.start();
        }

    }


    public class MyListAdapter extends BaseAdapter {

        private LayoutInflater mInflater;
        private int type;


        public MyListAdapter(Context context, int type) {
            this.mInflater = LayoutInflater.from(context);
            if (type != TYPE_DEVICE && type != TYPE_MEDIA) {
                try {
                    throw new Exception("invalide type error");
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }
            this.type = type;
        }

        @Override
        public int getCount() {
            if (type == TYPE_DEVICE) {
                return mDeviceData.size();
            } else if (type == TYPE_MEDIA) {
                return mMediaData.size();
            } else return 0;
        }

        @Override
        public Object getItem(int arg0) {
            return null;
        }

        @Override
        public long getItemId(int arg0) {
            return 0;
        }

        @Override
        public View getView(int position, View convertView, ViewGroup parent) {

            ViewHolder holder = null;
            if (convertView == null) {
                holder = new ViewHolder();
                convertView = mInflater.inflate(R.layout.device_list_item, null);
                holder.title = (TextView) convertView.findViewById(R.id.name);
                holder.info = (TextView) convertView.findViewById(R.id.type);
                convertView.setTag(holder);
            } else {
                holder = (ViewHolder) convertView.getTag();
            }
            if (type == TYPE_DEVICE) {
                holder.title.setText(mDeviceData.get(position).name);
                if (mDeviceData.get(position).type == Device.TYPE_AUDIO) {
                    holder.info.setText("音频设备");
                } else if (mDeviceData.get(position).type == Device.TYPE_VIDEO) {
                    holder.info.setText("视频设备");
                }
            } else if (type == TYPE_MEDIA) {
                holder.title.setText(mMediaData.get(position).getFileName());
                if (mMediaData.get(position).getMediaType() == Media.TYPE_MEDIA_AUDIO) {
                    holder.info.setText("音乐");
                } else if (mMediaData.get(position).getMediaType() == Media.TYPE_MEDIA_VIDEO) {
                    holder.info.setText("视频");
                }
            }


            return convertView;
        }

        public class ViewHolder {
            public TextView title;
            public TextView info;
        }
    }

    public class PlaybackListAdapter extends BaseAdapter {
        private Context context;
        private List<Media> dataKey;
        private Map<Media,List<Device>> dataMap;
        public PlaybackListAdapter(Context context, List<Media> key,Map<Media,List<Device>> map) {
            this.dataKey = key;
            this.dataMap = map;
            this.context = context;
        }

        @Override
        public int getCount() {
            return dataKey.size();
        }

        @Override
        public Object getItem(int i) {
            return dataMap.get(dataKey.get(i));
        }

        @Override
        public long getItemId(int i) {
            return i;
        }

        @Override
        public View getView(int i, View view, ViewGroup viewGroup) {
            ViewHolder viewHolder;
            if (view != null){
                viewHolder = (ViewHolder)view.getTag();
            }else {
                view = LayoutInflater.from(context).inflate(R.layout.playback_list_item, viewGroup, false);
                viewHolder = new ViewHolder();
                viewHolder.media = (TextView)view.findViewById(R.id.media_name);
                viewHolder.devices = (ListView)view.findViewById(R.id.device_list);
                view.setTag(viewHolder);
            }

            viewHolder.media.setText(dataKey.get(i).getFileName());
            Log.e("PlaybackListAdapter", "map get deives num = "+dataMap.get(dataKey.get(i)).size());
            PlaybackDeviceAdapter adapter = new PlaybackDeviceAdapter(dataMap.get(dataKey.get(i)), context);
            viewHolder.devices.setAdapter(adapter);
            //根据innerlistview的高度机损parentlistview item的高度
            setListViewHeightBasedOnChildren(viewHolder.devices);

            return view;
        }

        class ViewHolder {
            public TextView media;
            public ListView devices;
        }

        /**
         * 此方法是本次listview嵌套listview的核心方法：计算parentlistview item的高度。
         * 如果不使用此方法，无论innerlistview有多少个item，则只会显示一个item。
         **/
        public void setListViewHeightBasedOnChildren(ListView listView) {
            // 获取ListView对应的Adapter
            ListAdapter listAdapter = listView.getAdapter();
            if (listView == null) {
                return;
            }

            int totalHeight = 0;
            for (int i = 0, len = listAdapter.getCount(); i < len; i++) {

                View listItem = listAdapter.getView(i, null, listView);
//                View listItem = listView.getChildAt(i);
                // 计算子项View 的宽高
                listItem.measure(0, 0);
                // 统计所有子项的总高度
                totalHeight += listItem.getMeasuredHeight();
            }
            ViewGroup.LayoutParams params = listView.getLayoutParams();
            params.height = totalHeight+ (listView.getDividerHeight() * (listAdapter.getCount() - 1));
            listView.setLayoutParams(params);
        }

    }

    public class PlaybackDeviceAdapter extends BaseAdapter {
        private Context context;
        private List<Device> devices = new ArrayList<Device>();
        public PlaybackDeviceAdapter(List<Device> devices, Context context) {
            this.devices= devices;
            this.context = context;
        }

        @Override
        public int getCount() {
            return devices.size();
        }

        @Override
        public Object getItem(int i) {
            return devices.get(i);
        }

        @Override
        public long getItemId(int i) {
            return i;
        }

        @Override
        public View getView(int i, View view, ViewGroup viewGroup) {
            ViewHolder viewHolder;
            if (view != null){
                viewHolder = (ViewHolder)view.getTag();
            }else {
                view = LayoutInflater.from(context).inflate(R.layout.playback_list_device_item, viewGroup, false);
                viewHolder = new ViewHolder();
                viewHolder.name = (TextView)view.findViewById(R.id.name);
                view.setTag(viewHolder);
            }
            viewHolder.name.setText(devices.get(i).name);
            return view;
        }

        class ViewHolder {
            TextView name;
        }
    }

    private static class ListViewHandler extends Handler {
        private final WeakReference<ListActivity> mActivity;

        public ListViewHandler(ListActivity activity) {
            mActivity = new WeakReference<ListActivity>(activity);
        }

        @Override
        public void handleMessage(Message msg) {
            ListActivity activity = mActivity.get();
            if (msg.what == 0) {
                mActivity.get().finish();
            }
        }

    }
}