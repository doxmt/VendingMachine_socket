package client;

import java.io.ObjectOutputStream;
import java.net.Socket;
import java.util.HashMap;
import java.util.Map;

public class ClientSender {

    public static void sendDataToServer(Map<String, String> data) {
        String serverIp = "127.0.0.1";  // 서버 주소
        int serverPort = 9999;

        Socket socket = null;
        ObjectOutputStream oos = null;

        try {
            socket = new Socket(serverIp, serverPort);
            oos = new ObjectOutputStream(socket.getOutputStream());

            oos.writeObject(data);
            oos.flush();
            System.out.println("[클라이언트] 데이터 전송 완료");

            // 🔽 추가된 부분
            Thread.sleep(100);  // 서버가 스트림 열기 전에 클라이언트 소켓이 닫히는 문제 예방

        } catch (Exception e) {
            System.err.println("[클라이언트] 서버 전송 중 오류 발생:");
            e.printStackTrace();
        } finally {
            try {
                if (socket != null && !socket.isClosed()) {
                    System.out.println("[클라이언트] 연결 종료됨: " + socket.getInetAddress());
                    socket.close();
                }
            } catch (Exception ignore) {}
        }
    }


    public static void sendDisconnectNotice(String vmNumber) {
        Map<String, String> disconnectData = new HashMap<>();
        disconnectData.put("type", "disconnect");
        disconnectData.put("vmNumber", String.valueOf(vmNumber));
        sendDataToServer(disconnectData);
    }

    public static void main(String[] args) {

    }
}
