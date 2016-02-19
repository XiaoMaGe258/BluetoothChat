package bluetooth.app.max.com.xmgbluetoothchat;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.UUID;

import android.app.Activity;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothServerSocket;
import android.bluetooth.BluetoothSocket;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.Toolbar;
import android.text.TextUtils;
import android.util.Log;
import android.view.MenuItem;
import android.view.inputmethod.InputMethodManager;
import android.widget.EditText;
import android.widget.ListView;
import android.widget.Toast;

import butterknife.Bind;
import butterknife.ButterKnife;
import butterknife.OnClick;

public class ChatActivity extends AppCompatActivity {

	@Bind(R.id.MessageText)
	EditText mEditMsgView;
	@Bind(R.id.list)
	ListView mMessageListView;

	private Context mContext;
	private DeviceListAdapter mAdapter;
	private ArrayList<MessageListItem> mMessageList = new ArrayList<>();

	/* 一些常量，代表服务器的名称 */
	public static final String PROTOCOL_SCHEME_L2CAP = "btl2cap";
	public static final String PROTOCOL_SCHEME_RFCOMM = "btspp";
	public static final String PROTOCOL_SCHEME_BT_OBEX = "btgoep";
	public static final String PROTOCOL_SCHEME_TCP_OBEX = "tcpobex";

	private BluetoothServerSocket mServerSocket = null;
	private ServerThread startServerThread = null;
	private clientThread clientConnectThread = null;
	private BluetoothSocket socket = null;
	private BluetoothDevice device = null;
	private readThread mReadThread = null;;
	private BluetoothAdapter mBluetoothAdapter = BluetoothAdapter.getDefaultAdapter();

	private String mFromUserName = "";
	private String mFromUserAddress = "";

	private static boolean mIsClient = false;

	public final static int mWhatSystemSay = 0;
	public final static int mWhatISay = 1;
	public final static int mWhatOtherSay = 2;
	public final static int mUpdateTitle = 3;

	private Handler mLinkDetectedHandler = new Handler() {
		@Override
		public void handleMessage(Message msg) {
			switch (msg.what) {
				case mWhatISay:
					mMessageList.add(new MessageListItem((String) msg.obj, mWhatISay));
					break;
				case mWhatOtherSay:
					mMessageList.add(new MessageListItem((String) msg.obj, mWhatOtherSay));
					break;
				case mUpdateTitle:
					String name = (mFromUserName.startsWith("(绑)") ? mFromUserName.substring(3) : mFromUserName);
					setTitle("正在与 " + name + " 聊天");
					break;
				default:
					mMessageList.add(new MessageListItem((String) msg.obj, mWhatSystemSay));
					break;
			}
			mAdapter.notifyDataSetChanged();
			mMessageListView.setSelection(mMessageList.size() - 1);
		}

	};

	public static void actionCharActivity(Activity fromActivity){
		mIsClient = false;
		Intent intent = new Intent(fromActivity, ChatActivity.class);
		fromActivity.startActivity(intent);
	}

	public static void actionCharActivity(Activity fromActivity,
				String fromUserName, String fromUserAddress){
		mIsClient = true;
		Intent intent = new Intent(fromActivity, ChatActivity.class);
		intent.putExtra("fromUserName", fromUserName);
		intent.putExtra("fromUserAddress", fromUserAddress);
		fromActivity.startActivity(intent);
	}

	@Override
	public void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.chat_activity);
		ButterKnife.bind(this);
		mContext = this;
		mFromUserName = getIntent().getStringExtra("fromUserName");
		mFromUserAddress = getIntent().getStringExtra("fromUserAddress");
		initToolbar();
		initView();
		initBTChatThread();
	}

	private void initToolbar(){
		Toolbar toolbar = (Toolbar) findViewById(R.id.toolbar);
		setSupportActionBar(toolbar);
		if(mIsClient){
			setTitle("加入聊天");
		}else {
			setTitle("创建聊天");
		}
	}

	private void initView() {
		mAdapter = new DeviceListAdapter(this, mMessageList);
		mMessageListView.setAdapter(mAdapter);
		mMessageListView.setFastScrollEnabled(true);
		mEditMsgView.clearFocus();
	}

	private void initBTChatThread(){
		if(mIsClient){
			String address = mFromUserAddress;
			if(!TextUtils.isEmpty(address) && !"null".equals(address))
			{
				device = mBluetoothAdapter.getRemoteDevice(address);
				clientConnectThread = new clientThread();
				clientConnectThread.start();
			}else{
				Toast.makeText(mContext, "address is null !", Toast.LENGTH_SHORT).show();
			}
		}else{
			startServerThread = new ServerThread();
			startServerThread.start();
		}
	}

	@Override
	public boolean onOptionsItemSelected(MenuItem item) {
		if (item.getItemId() == android.R.id.home){
			shutdownAndExit();
			return true ;
		}
		return super.onOptionsItemSelected(item);
	}

	//发送消息
	@OnClick(R.id.btn_msg_send)
	void btnSendClick() {
		String msgText = mEditMsgView.getText().toString();
		if (msgText.length()>0) {
			sendMessageHandle(msgText);
			mEditMsgView.setText("");
			mEditMsgView.clearFocus();
			//close InputMethodManager
			InputMethodManager imm = (InputMethodManager)getSystemService(Context.INPUT_METHOD_SERVICE);
			imm.hideSoftInputFromWindow(mEditMsgView.getWindowToken(), 0);
		}else
			Toast.makeText(mContext, "发送内容不能为空！", Toast.LENGTH_SHORT).show();
	}

	private void shutdownAndExit(){
		shutdownChat();
		finish();
	}

	private void shutdownChat(){
		if(mIsClient){
			shutdownClient();
		}else {
			shutdownServer();
		}
	}

	//开启客户端
	private class clientThread extends Thread {
		public void run() {
			try {
				//创建一个Socket连接：只需要服务器在注册时的UUID号
				socket = device.createRfcommSocketToServiceRecord(UUID.fromString(Constant.SPP_UUID));
				//连接
				Message msg2 = new Message();
				msg2.obj = "请稍候，正在连接服务器:"+
						(mFromUserName.startsWith("(绑)") ? mFromUserName.substring(3) : mFromUserName);
				msg2.what = mWhatSystemSay;
				mLinkDetectedHandler.sendMessage(msg2);

				socket.connect();

				Message msg = new Message();
				msg.obj = "已经连接上服务端！可以发送信息。";
				msg.what = mWhatSystemSay;
				mLinkDetectedHandler.sendMessage(msg);
				mLinkDetectedHandler.sendEmptyMessage(mUpdateTitle);

				//启动接受数据
				mReadThread = new readThread();
				mReadThread.start();
			}
			catch (IOException e)
			{
				Log.e("connect", "", e);
				Message msg = new Message();
				msg.obj = "连接服务端异常！退出重新试一试。";
				msg.what = mWhatSystemSay;
				mLinkDetectedHandler.sendMessage(msg);
			}
		}
	}

	//开启服务器
	private class ServerThread extends Thread {
		public void run() {

			try {
				/* 创建一个蓝牙服务器
				 * 参数分别：服务器名称、UUID	 */
				mServerSocket = mBluetoothAdapter.listenUsingRfcommWithServiceRecord(PROTOCOL_SCHEME_RFCOMM,
						UUID.fromString(Constant.SPP_UUID));

				Log.d("server", "wait cilent connect...");

				Message msg = new Message();
				msg.obj = "请稍候，正在等待客户端的连接...";
				msg.what = mWhatSystemSay;
				mLinkDetectedHandler.sendMessage(msg);

				/* 接受客户端的连接请求 */
				socket = mServerSocket.accept();
				Log.d("server", "accept success !");

				mFromUserName = socket.getRemoteDevice().getName();
				Message msg2 = new Message();
				msg2.obj = "客户端已经连接上！可以发送信息。";
				msg.what = mWhatSystemSay;
				mLinkDetectedHandler.sendMessage(msg2);
				mLinkDetectedHandler.sendEmptyMessage(mUpdateTitle);

				//启动接受数据
				mReadThread = new readThread();
				mReadThread.start();
			} catch (IOException e) {
				e.printStackTrace();
			}
		}
	}

	// 停止服务器
	private void shutdownServer() {
		new Thread() {
			public void run() {
				if(startServerThread != null)
				{
					startServerThread.interrupt();
					startServerThread = null;
				}
				if(mReadThread != null)
				{
					mReadThread.interrupt();
					mReadThread = null;
				}
				try {
					if(socket != null)
					{
						socket.close();
						socket = null;
					}
					if (mServerSocket != null)
					{
						mServerSocket.close();/* 关闭服务器 */
						mServerSocket = null;
					}
				} catch (IOException e) {
					Log.e("server", "mServerSocket.close()", e);
				}
			};
		}.start();
	}

	// 停止客户端连接
	private void shutdownClient() {
		new Thread() {
			public void run() {
				if(clientConnectThread!=null)
				{
					clientConnectThread.interrupt();
					clientConnectThread= null;
				}
				if(mReadThread != null)
				{
					mReadThread.interrupt();
					mReadThread = null;
				}
				if (socket != null) {
					try {
						socket.close();
					} catch (IOException e) {
						e.printStackTrace();
					}
					socket = null;
				}
			};
		}.start();
	}

	//发送数据
	private void sendMessageHandle(String msg)
	{
		if (socket == null)
		{
			Toast.makeText(mContext, "没有连接", Toast.LENGTH_SHORT).show();
			return;
		}
		try {
			OutputStream os = socket.getOutputStream();
			os.write(msg.getBytes());
		} catch (IOException e) {
			e.printStackTrace();
		}
		mMessageList.add(new MessageListItem(msg, mWhatISay));
		mAdapter.notifyDataSetChanged();
		mMessageListView.setSelection(mMessageList.size() - 1);
	}

	//读取数据
	private class readThread extends Thread {
		public void run() {

			byte[] buffer = new byte[1024];
			int bytes;
			InputStream mmInStream = null;

			try {
				mmInStream = socket.getInputStream();
			} catch (IOException e1) {
				e1.printStackTrace();
			}
			while (true) {
				try {
					// Read from the InputStream
					if( (bytes = mmInStream.read(buffer)) > 0 )
					{
						byte[] buf_data = new byte[bytes];
						for(int i=0; i<bytes; i++)
						{
							buf_data[i] = buffer[i];
						}
						String s = new String(buf_data);
						Message msg = new Message();
						msg.obj = s;
						msg.what = mWhatOtherSay;
						mLinkDetectedHandler.sendMessage(msg);
					}
				} catch (IOException e) {
					try {
						mmInStream.close();
					} catch (IOException e1) {
						e1.printStackTrace();
					}
					break;
				}
			}
		}
	}

	@Override
	protected void onDestroy() {
		shutdownChat();
		super.onDestroy();
	}

	public class MessageListItem {
		String message;
		int whoSay;
		public MessageListItem(String msg, int whoSay) {
			message = msg;
			this.whoSay = whoSay;
		}
	}
}