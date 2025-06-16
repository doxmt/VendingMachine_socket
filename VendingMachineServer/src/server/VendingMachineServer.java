package server;

import java.io.EOFException;
import java.io.ObjectInputStream;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.Map;

public class VendingMachineServer {
    private static final int PORT = 9999;

    public static void main(String[] args) {
        new VendingMachineServer().startServer();
    }

    public void startServer() {
        try (ServerSocket serverSocket = new ServerSocket(PORT)) {
            System.out.println("[서버] 포트 " + PORT + "에서 대기 중...");

            while (true) {
                Socket clientSocket = serverSocket.accept();
                System.out.println("[서버] 클라이언트 연결됨: " + clientSocket.getInetAddress());

                // 클라이언트 데이터 처리
                new Thread(() -> handleClient(clientSocket)).start();
            }
        } catch (Exception e) {
            System.err.println("[서버] 서버 실행 중 오류 발생:");
            e.printStackTrace();
        }
    }

    private void handleClient(Socket clientSocket) {
        try (ObjectInputStream ois = new ObjectInputStream(clientSocket.getInputStream())) {

            @SuppressWarnings("unchecked")
            Map<String, String> receivedData = (Map<String, String>) ois.readObject();

            System.out.println("[서버] 데이터 수신 완료:");
            receivedData.forEach((key, value) -> System.out.println(key + ": " + value));

            // ✅ disconnect 처리
            if ("disconnect".equals(receivedData.get("type"))) {
                System.out.println("[서버] 자판기 " + receivedData.get("vmNumber") + " 종료됨");
                return;
            }

            //  sale 데이터일 경우
            if ("sale".equals(receivedData.get("type"))) {
                System.out.println("[서버] 매출 데이터 저장 시도");
                db.ServerMongoDBManager.getInstance().insertEncryptedSale(receivedData);
            }
            //  drink 데이터일 경우
            if ("drink".equals(receivedData.get("type"))) {
                System.out.println("[서버] 음료 데이터 저장 시도");
                db.ServerMongoDBManager.getInstance().insertOrUpdateEncryptedDrink(receivedData);
            }
            //  inventory 데이터일 경우
            if ("inventory".equals(receivedData.get("type"))) {
                System.out.println("[서버] 재고 데이터 저장 시도");
                db.ServerMongoDBManager.getInstance().insertOrUpdateEncryptedInventory(receivedData);
            }


        } catch (Exception e) {
            System.err.println("[서버] 클라이언트 처리 중 오류:");
            e.printStackTrace();
        } finally {
            try {
                clientSocket.close();
            } catch (Exception ignore) {}
        }
    }





}
