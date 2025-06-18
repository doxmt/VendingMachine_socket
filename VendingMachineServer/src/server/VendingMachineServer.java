package server;

import db.ServerMongoDBManager;
import util.EncryptionUtil_Server;

import java.io.ObjectInputStream;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.Base64;
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

            String type = receivedData.get("type");

            if ("disconnect".equals(type)) {
                System.out.println("[서버] 자판기 " + receivedData.get("vmNumber") + " 종료됨");
                return;
            }

            // vmNumber 암호화 (이미 암호화되어 있지 않은 경우만)
            String vmNumber = receivedData.get("vmNumber");
            if (!isBase64(vmNumber)) {
                try {
                    String encryptedVm = EncryptionUtil_Server.encrypt(vmNumber);
                    receivedData.put("vmNumber", encryptedVm);
                } catch (Exception e) {
                    System.err.println("[서버] vmNumber 암호화 실패");
                    e.printStackTrace();
                }
            }

            // 데이터 타입별 저장 처리
            switch (type) {
                case "sale":
                    System.out.println("[서버] 매출 데이터 저장 시도");
                    ServerMongoDBManager.getInstance().insertEncryptedSale(receivedData);
                    break;
                case "drink":
                    System.out.println("[서버] 음료 데이터 저장 시도");
                    ServerMongoDBManager.getInstance().insertOrUpdateEncryptedDrink(receivedData);
                    break;
                case "inventory":
                    System.out.println("[서버] 재고 데이터 저장 시도");
                    ServerMongoDBManager.getInstance().insertOrUpdateEncryptedInventory(receivedData);
                    break;
                case "drinkRename":
                    String oldName = receivedData.get("oldName");
                    String newName = receivedData.get("newName");
                    try {
                        ServerMongoDBManager.getInstance().updateDrinkNameEverywhere(vmNumber, oldName, newName);
                        System.out.println("[서버] 음료 이름 변경 처리 완료");
                    } catch (Exception e) {
                        System.err.println("[서버] 음료 이름 변경 처리 중 오류 발생:");
                        e.printStackTrace();
                    }
                    break;
                default:
                    System.out.println("[서버] 알 수 없는 데이터 타입: " + type);
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

    private boolean isBase64(String str) {
        try {
            Base64.getDecoder().decode(str);
            return true;
        } catch (IllegalArgumentException e) {
            return false;
        }
    }
}
