package bluetooth.app.max.com.xmgbluetoothchat;

import java.io.BufferedReader;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.UUID;

import android.app.Activity;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothServerSocket;
import android.bluetooth.BluetoothSocket;
import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.os.Bundle;
import android.os.Environment;
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
	private ServerThread mStartServerThread = null;
	private clientThread mClientConnectThread = null;
	private BluetoothSocket mBtSocket = null;
	private BluetoothDevice mBtDevice = null;
	private ReadThread1 mReadThread = null;;
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

		PickImageUtil.getInstance(ChatActivity.this).setCallback(new PickImageUtil.IGetSelectBitmap() {
			@Override
			public void onFinish(Bitmap bitmap, int id) {
				if(bitmap != null){
					//TODO
					Toast.makeText(mContext, "图片加载成功 "
							+bitmap.getWidth() + "x" + bitmap.getHeight(), Toast.LENGTH_SHORT).show();
					sendImageHandle(bitmap, "");
				}else{
					Toast.makeText(mContext, "图片加载失败", Toast.LENGTH_SHORT).show();
				}
			}
		});
	}

	private void initBTChatThread(){
		if(mIsClient){
			String address = mFromUserAddress;
			if(!TextUtils.isEmpty(address) && !"null".equals(address))
			{
				mBtDevice = mBluetoothAdapter.getRemoteDevice(address);
				mClientConnectThread = new clientThread();
				mClientConnectThread.start();
			}else{
				Toast.makeText(mContext, "address is null !", Toast.LENGTH_SHORT).show();
			}
		}else{
			mStartServerThread = new ServerThread();
			mStartServerThread.start();
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

	@OnClick(R.id.btn_msg_add)
	void btnAddClick() {
		PickImageUtil.getInstance(ChatActivity.this).showPickImageDialog();
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
				mBtSocket = mBtDevice.createRfcommSocketToServiceRecord(UUID.fromString(Constant.SPP_UUID));
				//连接
				Message msg2 = new Message();
				msg2.obj = "请稍候，正在连接服务器:"+
						(mFromUserName.startsWith("(绑)") ? mFromUserName.substring(3) : mFromUserName);
				msg2.what = mWhatSystemSay;
				mLinkDetectedHandler.sendMessage(msg2);

				mBtSocket.connect();

				Message msg = new Message();
				msg.obj = "已经连接上服务端！可以发送信息。";
				msg.what = mWhatSystemSay;
				mLinkDetectedHandler.sendMessage(msg);
				mLinkDetectedHandler.sendEmptyMessage(mUpdateTitle);

				//启动接受数据
				mReadThread = new ReadThread1();
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
				mBtSocket = mServerSocket.accept();
				Log.d("server", "accept success !");

				mFromUserName = mBtSocket.getRemoteDevice().getName();
				Message msg2 = new Message();
				msg2.obj = "客户端已经连接上！可以发送信息。";
				msg.what = mWhatSystemSay;
				mLinkDetectedHandler.sendMessage(msg2);
				mLinkDetectedHandler.sendEmptyMessage(mUpdateTitle);

				//启动接受数据
				mReadThread = new ReadThread1();
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
				if(mStartServerThread != null)
				{
					mStartServerThread.interrupt();
					mStartServerThread = null;
				}
				if(mReadThread != null)
				{
					mReadThread.interrupt();
					mReadThread = null;
				}
				try {
					if(mBtSocket != null)
					{
						mBtSocket.close();
						mBtSocket = null;
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
				if(mClientConnectThread !=null)
				{
					mClientConnectThread.interrupt();
					mClientConnectThread = null;
				}
				if(mReadThread != null)
				{
					mReadThread.interrupt();
					mReadThread = null;
				}
				if (mBtSocket != null) {
					try {
						mBtSocket.close();
					} catch (IOException e) {
						e.printStackTrace();
					}
					mBtSocket = null;
				}
			};
		}.start();
	}

	//发送数据
	private void sendMessageHandle(String msg) {
		if (mBtSocket == null)
		{
			Toast.makeText(mContext, "没有连接", Toast.LENGTH_SHORT).show();
			return;
		}
		try {
			OutputStream os = mBtSocket.getOutputStream();
			os.write(msg.getBytes());
		} catch (IOException e) {
			e.printStackTrace();
		}
		mMessageList.add(new MessageListItem(msg, mWhatISay));
		mAdapter.notifyDataSetChanged();
		mMessageListView.setSelection(mMessageList.size() - 1);
	}

	//发送图片
	private void sendImageHandle(Bitmap bmp, String msg) {
		if (mBtSocket == null)
		{
			Toast.makeText(mContext, "没有连接", Toast.LENGTH_SHORT).show();
			return;
		}

		try {
			if(TextUtils.isEmpty(msg)){
				ByteArrayOutputStream baos = new ByteArrayOutputStream();
				bmp.compress(Bitmap.CompressFormat.JPEG, 100, baos);
				OutputStream os = mBtSocket.getOutputStream();

				ByteBuffer bb = ByteBuffer.allocate(baos.toByteArray().length + 8);
				bb.putInt(1);
				bb.putInt(baos.toByteArray().length);
				bb.put(baos.toByteArray());
				os.write(bb.array());
			}else{
				OutputStream os = mBtSocket.getOutputStream();
				os.write(msg.getBytes());
			}
		} catch (IOException e) {
			e.printStackTrace();
		}

		mMessageList.add(new MessageListItem(msg, mWhatISay));
		mAdapter.notifyDataSetChanged();
		mMessageListView.setSelection(mMessageList.size() - 1);
	}

	//读取数据
	private class ReadThread0 extends Thread {
		public void run() {

			byte[] buffer = new byte[1024];
			int bytes;
			InputStream mmInStream = null;

			try {
				mmInStream = mBtSocket.getInputStream();
			} catch (IOException e1) {
				e1.printStackTrace();
			}
			StringBuffer sb = new StringBuffer();
			while (true) {
				try {
					// Read from the InputStream
					Log.d("xmg", "in ReadThread0  0  available="+mmInStream.available());
					if( (bytes = mmInStream.read(buffer)) > 0 )
					{
						Log.d("xmg", "in ReadThread0 1");
						byte[] buf_data = new byte[bytes];
						for(int i=0; i<bytes; i++)
						{
							buf_data[i] = buffer[i];
							Log.d("xmg", "in ReadThread0 2");
						}
						String s = new String(buf_data);
						Log.d("xmg", "in ReadThread0 s="+s +"  available="+mmInStream.available());
						sb.append(s);
						if(mmInStream.available() == 0){
							Message msg = new Message();
							msg.obj = sb.toString();
							msg.what = mWhatOtherSay;
							mLinkDetectedHandler.sendMessage(msg);
							sb.delete(0, sb.length());
						}
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

	int fileRestSize = -1;
	private class ReadThread1 extends Thread {
		public void run() {

			byte[] buffer = new byte[1024];
			int bytes;
			InputStream mmInStream = null;

			try {
				mmInStream = mBtSocket.getInputStream();
			} catch (IOException e1) {
				e1.printStackTrace();
			}
			Log.d("xmg", "in ReadThread1 run");
			StringBuffer sb = new StringBuffer();
			byte[] bufferOrig = new byte[0];

			while (true) {
				Log.d("xmg", "in while");
				try {
					// Read from the InputStream
					Log.d("xmg", "in ReadThread1  0  available="+mmInStream.available());
//					ByteBuffer buffer11=ByteBuffer.wrap(buffer);
					if( (bytes = mmInStream.read(buffer)) > 0 )
					{
						Log.d("xmg", "in ReadThread1 1");
						byte[] buf_data = new byte[bytes];
						for(int i=0; i<bytes; i++)
						{
							buf_data[i] = buffer[i];
						}

						Log.d("xmg", "in ReadThread1 1 buf_data.length="+buf_data.length);
						ByteBuffer byteBuffer = ByteBuffer.wrap(buf_data);
						int type = byteBuffer.getInt();
						int length = byteBuffer.getInt();
						Log.d("xmg", "type="+type+"  length="+length+"  buf_data.length="+buf_data.length);
						byte[] b;
						if(length > 0 && fileRestSize < 0) {
							fileRestSize = length - buf_data.length;
						}else if(fileRestSize > buf_data.length){
							fileRestSize = fileRestSize - buf_data.length;
						}else{
							fileRestSize = 0;
						}
						b = new byte[buf_data.length];
						byteBuffer.get(b);
//						if(fileRestSize > 0){
//						}else {
//						}
						Log.d("xmg", "fileRestSize="+fileRestSize);


						bufferOrig = byteMerger(bufferOrig, b);
						Log.d("xmg", "in ReadThread1 bufferOrig.length="+bufferOrig.length +"  available="+mmInStream.available());

						if(mmInStream.available() == 0 && fileRestSize == 0){

//							Log.d("xmg", "buf_data1.length="+buf_data1.length);
							File f = new File(Environment.getExternalStorageDirectory().getAbsolutePath()+"/temp/temp.jpg");
							if (f.exists()) {
								f.delete();
							}
							try {
								f.createNewFile();
							} catch (IOException e1) {
								e1.printStackTrace();
							}
							//创建输出流
							FileOutputStream outStream = new FileOutputStream(f);
							//写入数据
							outStream.write(bufferOrig);
							//关闭输出流
							outStream.close();


							Message msg = new Message();
							msg.obj = "receive ok";
							msg.what = mWhatOtherSay;
							mLinkDetectedHandler.sendMessage(msg);
						}
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
	public static byte[] byteMerger(byte[] byte_1, byte[] byte_2){
		byte[] byte_3 = new byte[byte_1.length+byte_2.length];
		System.arraycopy(byte_1, 0, byte_3, 0, byte_1.length);
		System.arraycopy(byte_2, 0, byte_3, byte_1.length, byte_2.length);
		return byte_3;
	}

	public class MessageListItem {
		String message;
		int whoSay;
		public MessageListItem(String msg, int whoSay) {
			message = msg;
			this.whoSay = whoSay;
		}
	}

	@Override
	protected void onActivityResult(int requestCode, int resultCode, Intent data) {
		PickImageUtil.getInstance(ChatActivity.this).onActivityResult(requestCode, resultCode, data);
	}

	@Override
	protected void onDestroy() {
		shutdownChat();
		super.onDestroy();
	}

}