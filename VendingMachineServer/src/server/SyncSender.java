// server/SyncSender.java
package server;

import java.io.*;
import java.net.Socket;
import java.util.Map;

public class SyncSender {
    public static void send(String targetHost, int port, Map<String, String> data) {
        try (Socket socket = new Socket(targetHost, port);
             ObjectOutputStream oos = new ObjectOutputStream(socket.getOutputStream())) {

            oos.writeObject(data);
            oos.flush();
            System.out.println("[동기화 전송 완료] → " + targetHost + ":" + port);
        } catch (IOException e) {
            System.err.println("[동기화 전송 실패] → " + targetHost + ":" + port);
            e.printStackTrace();
        }
    }
}
