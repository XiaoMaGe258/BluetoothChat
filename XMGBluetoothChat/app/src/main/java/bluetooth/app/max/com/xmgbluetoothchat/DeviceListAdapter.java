package bluetooth.app.max.com.xmgbluetoothchat;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.TextView;

import java.util.ArrayList;
import bluetooth.app.max.com.xmgbluetoothchat.ChatActivity.MessageListItem;

public class DeviceListAdapter extends BaseAdapter {
    private ArrayList<MessageListItem> list;
    private LayoutInflater mInflater;
  
    public DeviceListAdapter(Context context, ArrayList<MessageListItem> l) {
    	list = l;
		mInflater = LayoutInflater.from(context);
    }

    public int getCount() {
        return list.size();
    }

    public Object getItem(int position) {
        return list.get(position);
    }

    public long getItemId(int position) {
        return position;
    }

    public int getItemViewType(int position) {
        return list.get(position).whoSay;
    }

    @Override
    public int getViewTypeCount() {
        return 3;
    }

    public View getView(int position, View convertView, ViewGroup parent) {
    	ViewHolder viewHolder = null;
    	MessageListItem item=list.get(position);
        if(convertView == null){
            switch (getItemViewType(position)) {
                case ChatActivity.mWhatISay:
                    convertView = mInflater.inflate(R.layout.list_isay_item, parent, false);
                    break;
                case ChatActivity.mWhatOtherSay:
                    convertView = mInflater.inflate(R.layout.list_osay_item, parent, false);
                    break;
                default:
                    convertView = mInflater.inflate(R.layout.list_ssay_item, parent, false);
                    break;
            }
            viewHolder=new ViewHolder(
                    (View) convertView.findViewById(R.id.list_child),
                    (TextView) convertView.findViewById(R.id.chat_msg)
            );
        	convertView.setTag(viewHolder);
        }
        else{
        	viewHolder = (ViewHolder)convertView.getTag();
        }

        viewHolder.msg.setText(item.message);
        
        return convertView;
    }
    
    class ViewHolder {
    	  protected View child;
          protected TextView msg;
  
          public ViewHolder(View child, TextView msg){
              this.child = child;
              this.msg = msg;
              
          }
    }
}
