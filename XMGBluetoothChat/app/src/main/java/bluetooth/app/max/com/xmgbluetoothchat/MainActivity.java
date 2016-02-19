package bluetooth.app.max.com.xmgbluetoothchat;

import android.app.Activity;
import android.app.ProgressDialog;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothSocket;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.support.v7.app.AlertDialog;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.Toolbar;
import android.text.TextUtils;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.BaseAdapter;
import android.widget.Button;
import android.widget.ListView;
import android.widget.TextView;

import java.io.IOException;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Set;
import java.util.UUID;

import butterknife.Bind;
import butterknife.ButterKnife;
import butterknife.OnClick;
import fr.castorflex.android.smoothprogressbar.SmoothProgressBar;

public class MainActivity extends AppCompatActivity implements AdapterView.OnItemClickListener,
        AdapterView.OnItemLongClickListener{


    @Bind(R.id.tv_status)
    TextView mStatus;
    @Bind(R.id.btnSwitch)
    Button mBlueToothSwitch;
    @Bind(R.id.lv_bt_list)
    ListView mBlueToothList;
    @Bind(R.id.smoothProgressBar)
    SmoothProgressBar mSmoothProgressBar;

    Activity mContext;
    BTAdapter mBTAdapter;
    ProgressDialog mProgressDialog;
    ArrayList<BTItem> mBTDataList = new ArrayList<BTItem>();
    BluetoothAdapter mBluetoothAdapter = BluetoothAdapter.getDefaultAdapter();

    public static BluetoothSocket btSocket;
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        mContext = this;
        ButterKnife.bind(this);

        initToolbar();
        initView();
        initBluetooth();

        searchLocalBTList();
        searchRemoteBTList();
    }

    @Override
    protected void onResume() {
        super.onResume();
        refreshTitleStatus();
        if(isBluetoothOpened()){
            mBlueToothSwitch.setVisibility(View.INVISIBLE);
        }else{
            mBlueToothSwitch.setVisibility(View.VISIBLE);
        }
    }

    @OnClick(R.id.btnSwitch)
    void btnOpenClick() {
        if(!isBluetoothOpened()) {
            openBluetooth();
        }
    }

    @OnClick(R.id.btnCreateChat)
    void btnCreateChatClick(){
        ChatActivity.actionCharActivity(mContext);
    }

    @OnClick(R.id.btnSearch)
    void btnSearchClick() {
        searchLocalBTList();
        searchRemoteBTList();
    }

    private void initToolbar(){
        Toolbar toolbar = (Toolbar) findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);
        toolbar.setNavigationIcon(null);
        setTitle("蓝牙通信");
    }

    private void initView(){
        mBTAdapter = new BTAdapter(this, mBTDataList);
        mBlueToothList.setAdapter(mBTAdapter);
        mBlueToothList.setOnItemClickListener(this);
        mBlueToothList.setOnItemLongClickListener(this);

        mProgressDialog = new ProgressDialog(this);
        mProgressDialog.setMessage("正在读取设备...");
    }

    private void initBluetooth(){
        mBluetoothAdapter = BluetoothAdapter.getDefaultAdapter();

        IntentFilter intentFilter = new IntentFilter();
        // 注册用以接收到已搜索到的蓝牙设备的receiver
        intentFilter.addAction(BluetoothDevice.ACTION_FOUND);
        // 注册搜索完时的receiver
        intentFilter.addAction(BluetoothAdapter.ACTION_DISCOVERY_FINISHED);
        // 系统配对对话框关闭时的广播拦截
        intentFilter.addAction(BluetoothDevice.ACTION_ACL_CONNECTED);
        // 配对成功或失败状态改变时的广播拦截
        intentFilter.addAction(BluetoothDevice.ACTION_BOND_STATE_CHANGED);
        registerReceiver(mReceiver, intentFilter);

    }

    //刷新当前蓝牙状态。
    private void refreshTitleStatus(){
        mStatus.setText("当前蓝牙已" + (isBluetoothOpened() ? "打开":"关闭"));
//        searchLocalBTList();
    }

    //蓝牙是否已打开
    private boolean isBluetoothOpened(){
        if(mBluetoothAdapter == null)
            mBluetoothAdapter = BluetoothAdapter.getDefaultAdapter();
        return mBluetoothAdapter.isEnabled();
    }

    //打开蓝牙
    private void openBluetooth(){
        if(mBluetoothAdapter == null)
            mBluetoothAdapter = BluetoothAdapter.getDefaultAdapter();
        if (mBluetoothAdapter.isEnabled()) {
            return;
        }
        mBluetoothAdapter.enable();
        Intent enable = new Intent(BluetoothAdapter.ACTION_REQUEST_DISCOVERABLE);
        enable.putExtra(BluetoothAdapter.EXTRA_DISCOVERABLE_DURATION, 0); //蓝牙设备可见时间
        startActivity(enable);
    }

    //获取所有已经绑定配对的蓝牙设备
    private void searchLocalBTList(){
        if (!mBluetoothAdapter.isEnabled()) {
            return;
        }
        Set<BluetoothDevice> devices = mBluetoothAdapter.getBondedDevices();
        mBTDataList.clear();
        if (devices.size() > 0) {
            for (BluetoothDevice bluetoothDevice : devices) {
                BTItem item = new BTItem();
                item.name = "(绑)"+bluetoothDevice.getName();
                item.address = bluetoothDevice.getAddress();
                item.isBonded = true;
                mBTDataList.add(item);
            }
        }else{
            mBTDataList.add(new BTItem("(没有已配对的设备)", null, true));
        }
        mBTAdapter.notifyDataSetChanged();
    }

    //获取搜索所有附近可见的蓝牙设备
    private void searchRemoteBTList(){
        setTitle("正在扫描...");
        mSmoothProgressBar.setVisibility(View.VISIBLE);
        // 如果正在搜索，就先取消搜索
        if (mBluetoothAdapter.isDiscovering()) {
            mBluetoothAdapter.cancelDiscovery();
        }
        // 开始搜索蓝牙设备,搜索到的蓝牙设备通过广播返回
        mBluetoothAdapter.startDiscovery();
    }

    private BroadcastReceiver mReceiver = new BroadcastReceiver() {

        @Override
        public void onReceive(Context context, Intent intent) {

            String action = intent.getAction();
            BluetoothDevice device = intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE);
            // 获得已经搜索到的蓝牙设备
            if (BluetoothDevice.ACTION_FOUND.equals(action)) {
                // 搜索到的不是已经绑定的蓝牙设备
                if (device.getBondState() != BluetoothDevice.BOND_BONDED) {
                    for(BTItem bi : mBTDataList){
                        if(bi.address.equals(device.getAddress())){
                            return;
                        }
                    }
                    // 显示在TextView上
                    BTItem item = new BTItem();
                    item.name = device.getName();
                    item.address = device.getAddress();
                    item.isBonded = false;
                    mBTDataList.add(item);
                    mBTAdapter.notifyDataSetChanged();
                }
                // 搜索完成
            } else if (BluetoothAdapter.ACTION_DISCOVERY_FINISHED.equals(action)) {
                setTitle("扫描完毕");
                mSmoothProgressBar.setVisibility(View.INVISIBLE);
            } else if (BluetoothDevice.ACTION_ACL_CONNECTED.equals(action)){
                if(mProgressDialog != null)
                    mProgressDialog.dismiss();
            } else if (BluetoothDevice.ACTION_BOND_STATE_CHANGED.equals(action)){
                switch (device.getBondState()) {
                    case BluetoothDevice.BOND_BONDING:
                        Log.d("BlueToothTestActivity", "正在配对......");
                        if(mProgressDialog != null)
                            mProgressDialog.show();
                        break;
                    case BluetoothDevice.BOND_BONDED:
                        Log.d("BlueToothTestActivity", "完成配对");
                        searchLocalBTList();
                        searchRemoteBTList();
                        if(mProgressDialog != null)
                            mProgressDialog.dismiss();
                        break;
                    case BluetoothDevice.BOND_NONE:
                        Log.d("BlueToothTestActivity", "取消配对");
                    default:
                        break;
                }
            }
        }
    };

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        // Inflate the menu; this adds items to the action bar if it is present.
        getMenuInflater().inflate(R.menu.menu_main, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        // Handle action bar item clicks here. The action bar will
        // automatically handle clicks on the Home/Up button, so long
        // as you specify a parent activity in AndroidManifest.xml.
        int id = item.getItemId();

        //noinspection SimplifiableIfStatement
        if (id == R.id.action_settings) {
            return true;
        }

        return super.onOptionsItemSelected(item);
    }

    @Override
    public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
        if(mBluetoothAdapter.isDiscovering()){
            mBluetoothAdapter.cancelDiscovery();
        }
        BTItem btItem = mBTDataList.get(position);
        if(TextUtils.isEmpty(btItem.address)){
            return;
        }
        if(btItem.isBonded){
            ChatActivity.actionCharActivity(mContext, btItem.name, btItem.address);
            return;
        }
        String address = btItem.address;
        Log.e("address", address);
        if(TextUtils.isEmpty(address)){
            return;
        }
        BluetoothDevice btDev = mBluetoothAdapter.getRemoteDevice(address);
        try {
            Boolean returnValue = false;
            if (btDev.getBondState() == BluetoothDevice.BOND_NONE) {
                //利用反射方法调用BluetoothDevice.createBond(BluetoothDevice remoteDevice);
                returnValue = createBond(BluetoothDevice.class, btDev);
            }else if(btDev.getBondState() == BluetoothDevice.BOND_BONDED){
                connect(btDev);
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    @Override
    public boolean onItemLongClick(AdapterView<?> parent, View view, int position, long id) {
        if(mBluetoothAdapter.isDiscovering()){
            mBluetoothAdapter.cancelDiscovery();
        }
        final BTItem btItem = mBTDataList.get(position);
        if(TextUtils.isEmpty(btItem.address)){
            return false;
        }
        final BluetoothDevice btDev = mBluetoothAdapter.getRemoteDevice(btItem.address);
        if(btItem.isBonded){
            new AlertDialog.Builder(mContext).setMessage("是否解除该设备配对？")
                    .setNegativeButton("取消", new DialogInterface.OnClickListener() {
                        @Override
                        public void onClick(DialogInterface dialog, int which) {

                        }
                    })
                    .setPositiveButton("解除", new DialogInterface.OnClickListener() {
                        @Override
                        public void onClick(DialogInterface dialog, int which) {
                            try {
                                removeBond(BluetoothDevice.class, btDev);
                                new Handler(Looper.getMainLooper()).postDelayed(new Runnable() {
                                    @Override
                                    public void run() {
                                        searchLocalBTList();
                                    }
                                }, 1000);
                            } catch (Exception e) {
                                e.printStackTrace();
                            }
                        }
                    }).show();
            return true;
        }
        return false;
    }

    /**
     * 与设备配对 参考源码：platform/packages/apps/Settings.git
     * /Settings/src/com/android/settings/bluetooth/CachedBluetoothDevice.java
     */
    static public boolean createBond(Class btClass, BluetoothDevice btDevice)
            throws Exception {
        Method createBondMethod = btClass.getMethod("createBond");
        Boolean returnValue = (Boolean) createBondMethod.invoke(btDevice);
        return returnValue.booleanValue();
    }

    /**
     * 与设备解除配对 参考源码：platform/packages/apps/Settings.git
     * /Settings/src/com/android/settings/bluetooth/CachedBluetoothDevice.java
     */
    static public boolean removeBond(Class btClass, BluetoothDevice btDevice)
            throws Exception {
        Method removeBondMethod = btClass.getMethod("removeBond");
        Boolean returnValue = (Boolean) removeBondMethod.invoke(btDevice);
        return returnValue.booleanValue();
    }

    private void connect(BluetoothDevice btDev) {
        UUID uuid = UUID.fromString(Constant.SPP_UUID);
        try {
            btSocket = btDev.createRfcommSocketToServiceRecord(uuid);
            btSocket.connect();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    class BTItem {
        public BTItem(){}
        public BTItem(String name, String address, boolean isBonded){
            this.name = name;
            this.address = address;
            this.isBonded = isBonded;
        }
        String name;
        String address;
        boolean isBonded;
    }

    class BTAdapter extends BaseAdapter{

        Context mContext;
        ArrayList<BTItem> mDataList;
        LayoutInflater layoutInflater;

        public BTAdapter(Context context, ArrayList<BTItem> list){
            mContext = context;
            mDataList = list;
            layoutInflater = LayoutInflater.from(context);
        }

        @Override
        public int getCount() {
            return mDataList.size();
        }

        @Override
        public BTItem getItem(int position) {
            return mDataList.get(position);
        }

        @Override
        public long getItemId(int position) {
            return position;
        }

        @Override
        public View getView(int position, View convertView, ViewGroup parent) {
            if(convertView == null){
                convertView = layoutInflater.inflate(R.layout.bluetooth_list_item, null);
            }
            TextView name = (TextView) convertView.findViewById(R.id.item_tv_name);
            BTItem item = getItem(position);
            if(TextUtils.isEmpty(item.address)){
                name.setText(item.name);
            }else {
                name.setText(item.name + "(" + item.address + ")");
            }
            return convertView;
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        try {
            if (btSocket != null)
                btSocket.close();
        } catch (IOException e) {
            e.printStackTrace();
        }
        //解除注册
        unregisterReceiver(mReceiver);
    }

}
