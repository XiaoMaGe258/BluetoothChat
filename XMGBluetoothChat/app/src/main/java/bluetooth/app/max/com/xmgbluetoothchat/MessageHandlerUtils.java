package bluetooth.app.max.com.xmgbluetoothchat;

import java.io.DataOutputStream;
import java.io.FileInputStream;
import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;

/**
 * 发送消息，图片等。
 */
public class MessageHandlerUtils {

    public static void sendImage() {
        try {
            ServerSocket server = new ServerSocket(30000);
            Socket socket = server.accept();
            DataOutputStream dos = new DataOutputStream(socket.getOutputStream());
            FileInputStream fis = new FileInputStream("C:/sunnyTest/picture/cat01.jpg");
            int size = fis.available();

            System.out.println("size = " + size);
            byte[] data = new byte[size];
            fis.read(data);
            dos.writeInt(size);
            dos.write(data);

            dos.flush();
            dos.close();
            fis.close();
            socket.close();
            server.close();
        } catch (IOException e) {
            e.printStackTrace();
        }

    }
}
