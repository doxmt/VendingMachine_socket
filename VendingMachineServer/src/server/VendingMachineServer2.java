    package server;

    import com.mongodb.client.MongoDatabase;
    import db.ServerMongoDBManager;
    import util.EncryptionUtil_Server;

    import java.io.FileInputStream;
    import java.io.IOException;
    import java.io.ObjectInputStream;
    import java.net.ServerSocket;
    import java.net.Socket;
    import java.util.Base64;
    import java.util.Map;

    import com.mongodb.client.MongoClients;
    import com.mongodb.client.MongoClient;
    import com.mongodb.client.MongoCollection;
    import org.bson.Document;
    import java.time.LocalDate;
    import java.util.*;
    import java.util.stream.Collectors;

    public class VendingMachineServer2 {
        private static final int PORT = 9998;
        private static final int SYNC_PORT = 9202;  // Server2는 9202
        public static void startSyncListener() {
            new Thread(() -> {
                try (ServerSocket syncServer = new ServerSocket(SYNC_PORT)) {
                    System.out.println("[동기화 리스너] SYNC 포트 열림: " + SYNC_PORT);
                    while (true) {
                        Socket client = syncServer.accept();
                        byte[] buf = new byte[64];
                        int len = client.getInputStream().read(buf);
                        String msg = new String(buf, 0, len);
                        if ("PING".equals(msg)) {
                            client.getOutputStream().write("PONG".getBytes());
                        }
                        client.close();
                    }
                } catch (IOException e) {
                    System.err.println("[동기화 리스너] 오류:");
                    e.printStackTrace();
                }
            }).start();
        }


        public static void main(String[] args) {
            new VendingMachineServer2().startServer();
            startSyncListener();
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

                String syncTargetHost = "localhost"; // 또는 서버1의 IP
                int syncTargetPort = 9201; // 🔁 Server2 → Server1 전송용 포트


                // 데이터 타입별 저장 처리
                switch (type) {
                    case "sale":
                        System.out.println("[서버] 매출 데이터 저장 시도");
                        ServerMongoDBManager.getInstance().insertEncryptedSale(receivedData);
                        SyncSender.send(syncTargetHost, syncTargetPort, receivedData);
                        break;
                    case "drink":
                        System.out.println("[서버] 음료 데이터 저장 시도");
                        ServerMongoDBManager.getInstance().insertOrUpdateEncryptedDrink(receivedData);
                        SyncSender.send(syncTargetHost, syncTargetPort, receivedData);
                        break;
                    case "inventory":
                        System.out.println("[서버] 재고 데이터 저장 시도");
                        ServerMongoDBManager.getInstance().insertOrUpdateEncryptedInventory(receivedData);
                        SyncSender.send(syncTargetHost, syncTargetPort, receivedData);
                        break;
                    case "drinkRename":
                        String oldName = receivedData.get("oldName");
                        String newName = receivedData.get("newName");
                        try {
                            ServerMongoDBManager.getInstance().updateDrinkNameEverywhere(vmNumber, oldName, newName);
                            SyncSender.send(syncTargetHost, syncTargetPort, receivedData);
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
                } catch (Exception ignore) {
                }
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



        public class ServerAdminFunctions {

            private static final String DB_NAME = "vending_machine_server";
            private static final String URI = util.EnvUtil.get("MONGO_URI");
            // ✅ 매출 요약 (일별, 월별, 총합)
            public static void getSalesSummary(String vmNumber) {
                System.out.println("[기능] 매출 요약 조회 준비: " + vmNumber);
                try (MongoClient client = MongoClients.create(URI)) {
                    MongoDatabase db = client.getDatabase(DB_NAME);
                    MongoCollection<Document> salesCol = db.getCollection("sales");

                    String encVm = EncryptionUtil_Server.encrypt(vmNumber);
                    String today = LocalDate.now().toString();        // yyyy-MM-dd
                    String month = today.substring(0, 7);             // yyyy-MM

                    int total = 0, daily = 0, monthly = 0;

                    for (Document doc : salesCol.find(new Document("vmNumber", encVm))) {
                        String date = EncryptionUtil_Server.decrypt(doc.getString("date"));
                        int price = Integer.parseInt(EncryptionUtil_Server.decrypt(doc.getString("price")));
                        total += price;
                        if (date.equals(today)) daily += price;
                        if (date.startsWith(month)) monthly += price;
                    }

                    System.out.printf("🧾 일별: %d, 월별: %d, 총합: %d\n", daily, monthly, total);
                } catch (Exception e) {
                    System.err.println("[오류] 매출 요약 조회 실패");
                    e.printStackTrace();
                }
            }

            // ✅ 음료별 매출 집계
            public static void getDrinkSalesSummary(String vmNumber) {
                System.out.println("[기능] 음료별 매출 조회 준비: " + vmNumber);
                try (MongoClient client = MongoClients.create(URI)) {
                    MongoDatabase db = client.getDatabase(DB_NAME);
                    MongoCollection<Document> salesCol = db.getCollection("sales");

                    String encVm = EncryptionUtil_Server.encrypt(vmNumber);
                    Map<String, Integer> salesMap = new HashMap<>();

                    for (Document doc : salesCol.find(new Document("vmNumber", encVm))) {
                        String name = EncryptionUtil_Server.decrypt(doc.getString("drinkName"));
                        int price = Integer.parseInt(EncryptionUtil_Server.decrypt(doc.getString("price")));
                        salesMap.put(name, salesMap.getOrDefault(name, 0) + price);
                    }

                    salesMap.forEach((name, total) ->
                            System.out.printf("🥤 %s: %d원\n", name, total)
                    );

                } catch (Exception e) {
                    System.err.println("[오류] 음료별 매출 조회 실패");
                    e.printStackTrace();
                }
            }

            // ✅ 현재 재고 조회
            public static void getStockStatus(String vmNumber) {
                System.out.println("[기능] 재고 현황 조회 준비: " + vmNumber);
                try (MongoClient client = MongoClients.create(URI)) {
                    MongoDatabase db = client.getDatabase(DB_NAME);
                    MongoCollection<Document> inventoryCol = db.getCollection("inventory");

                    String encVm = EncryptionUtil_Server.encrypt(vmNumber);
                    for (Document doc : inventoryCol.find(new Document("vmNumber", encVm))) {
                        String name = EncryptionUtil_Server.decrypt(doc.getString("name"));
                        int stock = Integer.parseInt(EncryptionUtil_Server.decrypt(doc.getString("stock")));
                        System.out.printf("📦 %s: %d개\n", name, stock);
                    }
                } catch (Exception e) {
                    System.err.println("[오류] 재고 조회 실패");
                    e.printStackTrace();
                }
            }

            // ✅ 등록된 음료 이름 목록
            public static void getDrinkNameList(String vmNumber) {
                System.out.println("[기능] 음료 이름 목록 조회 준비: " + vmNumber);
                try (MongoClient client = MongoClients.create(URI)) {
                    MongoDatabase db = client.getDatabase(DB_NAME);
                    MongoCollection<Document> drinksCol = db.getCollection("drinks");

                    String encVm = EncryptionUtil_Server.encrypt(vmNumber);
                    List<String> names = new ArrayList<>();

                    for (Document doc : drinksCol.find(new Document("vmNumber", encVm))) {
                        String name = EncryptionUtil_Server.decrypt(doc.getString("name"));
                        names.add(name);
                    }

                    System.out.println("🍹 등록된 음료: " + String.join(", ", names));

                } catch (Exception e) {
                    System.err.println("[오류] 음료 목록 조회 실패");
                    e.printStackTrace();
                }
            }

            // ✅ 최근 7일 일별 매출 추이
            public static void getDailySalesTrend() {
                System.out.println("[기능] 최근 7일간 일별 매출 조회 준비");
                try (MongoClient client = MongoClients.create(URI)) {
                    MongoDatabase db = client.getDatabase(DB_NAME);
                    MongoCollection<Document> salesCol = db.getCollection("sales");

                    Map<String, Integer> dateToTotal = new HashMap<>();

                    for (Document doc : salesCol.find()) {
                        String date = EncryptionUtil_Server.decrypt(doc.getString("date"));
                        int price = Integer.parseInt(EncryptionUtil_Server.decrypt(doc.getString("price")));
                        dateToTotal.put(date, dateToTotal.getOrDefault(date, 0) + price);
                    }

                    List<String> sortedDates = dateToTotal.keySet().stream()
                            .sorted()
                            .collect(Collectors.toList());

                    List<String> recent7 = sortedDates.stream()
                            .skip(Math.max(0, sortedDates.size() - 7))
                            .collect(Collectors.toList());

                    System.out.println("📊 최근 7일 매출:");
                    for (String date : recent7) {
                        System.out.printf(" - %s: %d원\n", date, dateToTotal.get(date));
                    }

                } catch (Exception e) {
                    System.err.println("[오류] 일별 매출 조회 실패");
                    e.printStackTrace();
                }
            }
        }
    }
