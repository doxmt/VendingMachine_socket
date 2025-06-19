// server/SyncReceiver.java
package server;

import java.io.*;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.Map;
import com.mongodb.client.*;
import db.ServerMongoDBManager;

public class SyncReceiver extends Thread {
    private final int port;

    public SyncReceiver(int port) {
        this.port = port;
    }

    @Override
    public void run() {
        try (ServerSocket serverSocket = new ServerSocket(port)) {
            System.out.println("[동기화 수신] 포트 " + port + " 대기 중...");
            while (true) {
                Socket socket = serverSocket.accept();
                new Thread(() -> handleSync(socket)).start();
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private void handleSync(Socket socket) {
        try (ObjectInputStream ois = new ObjectInputStream(socket.getInputStream())) {
            @SuppressWarnings("unchecked")
            Map<String, String> data = (Map<String, String>) ois.readObject();

            String type = data.get("type");
            System.out.println("[동기화 수신] 타입: " + type);

            // 🔁 받은 데이터 DB에 저장
            switch (type) {
                case "sale":
                    ServerMongoDBManager.getInstance().insertEncryptedSale(data);
                    break;
                case "drink":
                    ServerMongoDBManager.getInstance().insertOrUpdateEncryptedDrink(data);
                    break;
                case "inventory":
                    ServerMongoDBManager.getInstance().insertOrUpdateEncryptedInventory(data);
                    break;
                case "drinkRename":
                    ServerMongoDBManager.getInstance().updateDrinkNameEverywhere(
                            data.get("vmNumber"), data.get("oldName"), data.get("newName"));
                    break;
            }

        } catch (Exception e) {
            System.err.println("[동기화 수신 오류]");
            e.printStackTrace();
        }
    }
}
