package db;

import com.mongodb.client.*;
import com.mongodb.client.model.Filters;
import com.mongodb.client.model.Projections;
import org.bson.Document;
import com.mongodb.client.model.UpdateOptions;
import util.EncryptionUtil;

import java.io.FileWriter;
import java.io.InputStream;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.*;

import java.io.FileInputStream;
import java.io.IOException;
import java.util.logging.Logger;


public class MongoDBManager {
    private static final MongoDBManager instance = new MongoDBManager();
    private static final String URI;
    private static final Logger logger = Logger.getLogger(MongoDBManager.class.getName());

    static {
        String uriTemp = "";
        try {
            Properties props = new Properties();
            try (InputStream is = MongoDBManager.class.getClassLoader().getResourceAsStream("config.properties")) {
                if (is == null) {
                    throw new IOException("config.properties 파일을 classpath에서 찾을 수 없습니다.");
                }
                props.load(is);
                uriTemp = props.getProperty("mongo.uri");
            }
        } catch (IOException e) {
            logger.severe("MongoDB URI 설정 중 오류 발생: " + e.getMessage());
        }
        URI = uriTemp;
    }

    private static final String DB_NAME = "vending_machine";
    private static final MongoClient mongoClient = MongoClients.create(URI);
    private static final MongoDatabase database = mongoClient.getDatabase(DB_NAME);

    private MongoDBManager() {}

    private MongoCollection<Document> getMachineStateCollection() {
        return database.getCollection("machineState");
    }


    public static MongoDBManager getInstance() {
        return instance;
    }

    // ----------------- sales -----------------
    public MongoCollection<Document> getSalesCollection() {
        return database.getCollection("sales");
    }

    public void insertSale(String vmNumber, String drinkName, int price, String date) {
        try {
            MongoCollection<Document> sales = getSalesCollection();

            Document saleDoc =
                    new Document("vmNumber", EncryptionUtil.encrypt(String.valueOf(vmNumber)))
                            .append("drinkName", EncryptionUtil.encrypt(drinkName))
                            .append("price", EncryptionUtil.encrypt(String.valueOf(price)))
                            .append("date", EncryptionUtil.encrypt(date));


            sales.insertOne(saleDoc);

            saveSaleToFile(vmNumber, drinkName, price, date);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }




    public List<Document> getSalesByVM(String vmNumber) {
        List<Document> result = new ArrayList<>();
        MongoCollection<Document> collection = getSalesCollection();

        try {
            String encryptedVM = EncryptionUtil.encrypt(String.valueOf(vmNumber));
            String encVm = EncryptionUtil.encrypt(String.valueOf(vmNumber));
            for (Document doc : collection.find(new Document("vmNumber", encVm))) {
                String name = EncryptionUtil.decrypt(doc.getString("drinkName"));
                int price = Integer.parseInt(EncryptionUtil.decrypt(doc.getString("price")));
                String date = EncryptionUtil.decrypt(doc.getString("date"));

                result.add(new Document()
                        .append("vmNumber", vmNumber)
                        .append("drinkName", name)
                        .append("price", price)
                        .append("date", date));
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        return result;
    }


    // ----------------- inventory -----------------
    public MongoCollection<Document> getInventoryCollection() {
        return database.getCollection("inventory");
    }

    public void upsertInventory(String vmNumber, String drinkName, int price, int stock, LocalDate date) {
        try {
            String encPrice = EncryptionUtil.encrypt(String.valueOf(price));
            String encStock = EncryptionUtil.encrypt(String.valueOf(stock));
            String encDate = EncryptionUtil.encrypt(date.toString());

            Document filter = new Document("vmNumber", EncryptionUtil.encrypt(String.valueOf(vmNumber)))
                    .append("drinkName", EncryptionUtil.encrypt(drinkName));

            Document update = new Document("$set", new Document("price", encPrice)
                    .append("stock", encStock)
                    .append("lastUpdated", encDate));

            getInventoryCollection().updateOne(filter, update, new UpdateOptions().upsert(true));

            // 서버 전송은 평문으로 유지
            Map<String, String> inventoryData = new HashMap<>();
            inventoryData.put("type", "inventory");
            inventoryData.put("vmNumber", String.valueOf(vmNumber));
            inventoryData.put("drinkName", drinkName);
            inventoryData.put("price", String.valueOf(price));
            inventoryData.put("stock", String.valueOf(stock));
            inventoryData.put("date", date.toString());

            client.ClientSender.sendDataToServer(inventoryData);

        } catch (Exception e) {
            e.printStackTrace();
        }
    }



    public Document getInventory(String vmNumber, String drinkName) {
        try {
            Document doc = getInventoryCollection()
                    .find(new Document("vmNumber", EncryptionUtil.encrypt(String.valueOf(vmNumber)))
                            .append("drinkName", EncryptionUtil.encrypt(drinkName)))
                    .first();

            if (doc == null) return null;

            int price = Integer.parseInt(EncryptionUtil.decrypt(doc.getString("price")));
            int stock = Integer.parseInt(EncryptionUtil.decrypt(doc.getString("stock")));
            String lastUpdated = EncryptionUtil.decrypt(doc.getString("lastUpdated"));

            return new Document("drinkName", drinkName)
                    .append("price", price)
                    .append("stock", stock)
                    .append("lastUpdated", lastUpdated);
        } catch (Exception e) {
            e.printStackTrace();
            return null;
        }
    }


    // ----------------- drinks -----------------
    public MongoCollection<Document> getDrinksCollection() {
        return database.getCollection("drinks");
    }

    public void insertDrink(String vmNumber, int drinkId, String name, int defaultPrice) {
        try {
            String encryptedVmNumber = EncryptionUtil.encrypt(String.valueOf(vmNumber));
            String encryptedDrinkId = EncryptionUtil.encrypt(String.valueOf(drinkId));
            String encryptedName = EncryptionUtil.encrypt(name);
            String encryptedPrice = EncryptionUtil.encrypt(String.valueOf(defaultPrice));
            String encryptedStock = EncryptionUtil.encrypt("10"); // 초기 재고

            Document doc = new Document("vmNumber", encryptedVmNumber)
                    .append("drinkId", encryptedDrinkId)
                    .append("name", encryptedName)
                    .append("defaultPrice", encryptedPrice)
                    .append("stock", encryptedStock);

            getDrinksCollection().insertOne(doc);
            System.out.println("[MongoDB] 암호화된 음료 삽입 완료: " + name + " (id=" + drinkId + ")");
        } catch (Exception e) {
            System.err.println("[MongoDB] insertDrink 실패: " + e.getMessage());
            e.printStackTrace();
        }
    }



    public List<Document> getAllDrinks(String vmNumber) {
        List<Document> drinks = new ArrayList<>();
        try {
            String encryptedVM = EncryptionUtil.encrypt(String.valueOf(vmNumber));
            getDrinksCollection().find(new Document("vmNumber", encryptedVM)).into(drinks);
        } catch (Exception e) {
            e.printStackTrace();
        }
        return drinks;
    }

    public void insertInitialDrinks(String vmNumber) throws Exception {
        MongoCollection<Document> drinksCollection = getDrinksCollection();
        String encryptedVM = EncryptionUtil.encrypt(String.valueOf(vmNumber));

        long count = drinksCollection.countDocuments(new Document("vmNumber", encryptedVM));
        if (count > 0) return;

        // 기본 음료 이름과 가격 설정
        String[] names = {
                "물",         // 0 → /image/0.jpg
                "캔커피",     // 1 → /image/1.jpg
                "이온음료",   // 2 → /image/2.jpg
                "고급캔커피", // 3 → /image/3.jpg
                "탄산음료",   // 4 → /image/4.jpg
                "특화음료",   // 5 → /image/5.jpg
                "믹스커피",   // 6 → /image/6.jpg
                "고급믹스커피"// 7 → /image/7.jpg
        };

        int[] prices = {
                450, 500, 550, 700,
                750, 800, 200, 300
        };


        for (int i = 0; i < names.length; i++) {
            insertDrink(vmNumber, i, names[i], prices[i]);
        }
    }



    // ----------------- operations -----------------
    public void insertAdminOperation(String vmNumber, String operation, String target, Document detail) {
        try {
            MongoCollection<Document> collection = database.getCollection("operations");

            String encOperation = EncryptionUtil.encrypt(operation);
            String encTarget = EncryptionUtil.encrypt(target);
            String encTimestamp = EncryptionUtil.encrypt(LocalDateTime.now().toString());

            Document encryptedDetail = new Document();
            for (String key : detail.keySet()) {
                Object value = detail.get(key);
                if (value != null) {
                    encryptedDetail.append(key, EncryptionUtil.encrypt(value.toString()));
                }
            }


            Document doc =   new Document("vmNumber", EncryptionUtil.encrypt(String.valueOf(vmNumber)))
                    .append("operation", encOperation)
                    .append("target", encTarget)
                    .append("detail", encryptedDetail)
                    .append("timestamp", encTimestamp);

            collection.insertOne(doc);
        } catch (Exception e) {
            e.printStackTrace(); // 또는 로깅
        }
    }

    public MongoCollection<Document> getOperationsCollection() {
        return database.getCollection("operations");
    }


    public List<Document> getOperationsByVM(String vmNumber) {
        List<Document> result = new ArrayList<>();
        MongoCollection<Document> collection = getOperationsCollection();

        try {
            String encVm = EncryptionUtil.encrypt(String.valueOf(vmNumber));
            for (Document doc : collection.find(new Document("vmNumber", encVm))) {
                try {
                    String operation = EncryptionUtil.decrypt(doc.getString("operation"));
                    String target = EncryptionUtil.decrypt(doc.getString("target"));
                    String timestamp = EncryptionUtil.decrypt(doc.getString("timestamp"));

                    Document decryptedDetail = new Document();
                    Document encDetail = (Document) doc.get("detail");
                    for (String key : encDetail.keySet()) {
                        Object value = encDetail.get(key);
                        if (value instanceof String) {
                            decryptedDetail.append(key, EncryptionUtil.decrypt((String) value));
                        } else {
                            decryptedDetail.append(key, value);
                        }
                    }

                    result.add(new Document()
                            .append("vmNumber", vmNumber)
                            .append("operation", operation)
                            .append("target", target)
                            .append("detail", decryptedDetail)
                            .append("timestamp", timestamp));
                } catch (Exception e) {
                    e.printStackTrace();  // 각 문서별 복호화 실패 처리
                }
            }
        } catch (Exception e) {
            e.printStackTrace();  // 암호화 실패 처리
        }

        return result;
    }




    public List<Document> getDrinksByVMNumber(String vmNumber) {
        List<Document> decryptedDrinks = new ArrayList<>();
        try {
            String encryptedVM = EncryptionUtil.encrypt(String.valueOf(vmNumber));
            FindIterable<Document> docs = getDrinksCollection().find(new Document("vmNumber", encryptedVM));

            for (Document doc : docs) {
                String name = EncryptionUtil.decrypt(doc.getString("name"));
                int price = Integer.parseInt(EncryptionUtil.decrypt(doc.getString("defaultPrice")));
                int stock = Integer.parseInt(EncryptionUtil.decrypt(doc.getString("stock")));

                Document decrypted = new Document("name", name)
                        .append("defaultPrice", price)
                        .append("stock", stock);
                decryptedDrinks.add(decrypted);
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        return decryptedDrinks;
    }


    public void updateDrinkNameEverywhere(String vmNumber, String oldName, String newName) {
        try {
            String encVm = EncryptionUtil.encrypt(vmNumber.trim().toUpperCase());
            String encOldName = EncryptionUtil.encrypt(oldName);
            String encNewName = EncryptionUtil.encrypt(newName);

            Document filter = new Document("vmNumber", encVm)
                    .append("drinkName", encOldName);
            Document update = new Document("$set", new Document("drinkName", encNewName));

            // sales 컬렉션 이름 변경
            getSalesCollection().updateMany(filter, update);

            // inventory 컬렉션 이름 변경
            getInventoryCollection().updateMany(filter, update);

        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public void updateCurrentAmount(String vmNumber, int amount) {
        try {
            String encVm = EncryptionUtil.encrypt(String.valueOf(vmNumber));
            String encryptedAmount = EncryptionUtil.encrypt(String.valueOf(amount));
            getMachineStateCollection().updateOne(
                    new Document("vmNumber", encVm),
                    new Document("$set", new Document("currentAmount", encryptedAmount)),
                    new UpdateOptions().upsert(true)
            );
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public int getCurrentAmount(String vmNumber) {
        try {
            String encVm = EncryptionUtil.encrypt(String.valueOf(vmNumber));
            Document doc = getMachineStateCollection()
                    .find(new Document("vmNumber", encVm))
                    .first();
            if (doc != null && doc.containsKey("currentAmount")) {
                return Integer.parseInt(EncryptionUtil.decrypt(doc.getString("currentAmount")));
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        return 0;
    }


    public void addToStoredAmount(String vmNumber, int amountToAdd) {
        try {
            String encVm = EncryptionUtil.encrypt(String.valueOf(vmNumber));
            Document doc = getMachineStateCollection().find(new Document("vmNumber", encVm)).first();
            int currentStored = 0;

            if (doc != null && doc.containsKey("storedAmount")) {
                currentStored = Integer.parseInt(EncryptionUtil.decrypt(doc.getString("storedAmount")));
            }

            int updated = currentStored + amountToAdd;
            getMachineStateCollection().updateOne(
                    new Document("vmNumber", encVm),
                    new Document("$set", new Document("storedAmount", EncryptionUtil.encrypt(String.valueOf(updated)))),
                    new UpdateOptions().upsert(true)
            );
        } catch (Exception e) {
            e.printStackTrace();
        }
    }


    public int collectStoredAmount(String vmNumber) {
        try {
            String encVm = EncryptionUtil.encrypt(String.valueOf(vmNumber));
            Document doc = getMachineStateCollection().find(new Document("vmNumber", encVm)).first();

            int storedAmount = 0;
            if (doc != null && doc.containsKey("storedAmount")) {
                storedAmount = Integer.parseInt(EncryptionUtil.decrypt(doc.getString("storedAmount")));
            }

            getMachineStateCollection().updateOne(
                    new Document("vmNumber", encVm),
                    new Document("$set", new Document("storedAmount", EncryptionUtil.encrypt("0")))
            );

            return storedAmount;
        } catch (Exception e) {
            e.printStackTrace();
            return 0;
        }
    }


    public void incrementStoredMoney(String vmNumber, int denomination, int count) {
        try {
            String encVm = EncryptionUtil.encrypt(String.valueOf(vmNumber)); // 🔒 암호화
            Document doc = getMachineStateCollection().find(new Document("vmNumber", encVm)).first();
            Document storedMoney = new Document();

            if (doc != null && doc.containsKey("storedMoney")) {
                Document existing = (Document) doc.get("storedMoney");
                for (String key : existing.keySet()) {
                    String val = EncryptionUtil.decrypt(existing.getString(key));
                    storedMoney.append(key, val);
                }
            }

            int prevCount = Integer.parseInt(storedMoney.getOrDefault(String.valueOf(denomination), "0").toString());
            int newCount = prevCount + count;

            storedMoney.put(String.valueOf(denomination), EncryptionUtil.encrypt(String.valueOf(newCount)));

            getMachineStateCollection().updateOne(
                    new Document("vmNumber", encVm), // 🔒 암호화된 vmNumber
                    new Document("$set", new Document("storedMoney", storedMoney)),
                    new UpdateOptions().upsert(true)
            );
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public Document getStoredMoney(String vmNumber) {
        Document result = new Document();
        try {
            String encVm = EncryptionUtil.encrypt(String.valueOf(vmNumber)); // 🔒 암호화
            Document doc = getMachineStateCollection()
                    .find(new Document("vmNumber", encVm))
                    .first();
            if (doc != null && doc.containsKey("storedMoney")) {
                Document encMap = (Document) doc.get("storedMoney");
                for (String denom : encMap.keySet()) {
                    String decrypted = EncryptionUtil.decrypt(encMap.getString(denom));
                    result.append(denom, Integer.parseInt(decrypted));
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        return result;
    }

    public void resetStoredMoney(String vmNumber) {
        try {
            String encVm = EncryptionUtil.encrypt(String.valueOf(vmNumber)); // 🔒 암호화
            getMachineStateCollection().updateOne(
                    new Document("vmNumber", encVm),
                    new Document("$set", new Document("storedMoney", new Document()))
            );
        } catch (Exception e) {
            e.printStackTrace();
        }
    }



    // Change 정보 조회
    public Document getChangeState(String vmNumber) {
        try {
            String encryptedVM = EncryptionUtil.encrypt(String.valueOf(vmNumber));
            return database.getCollection("changeStorage")
                    .find(Filters.eq("vmNumber", encryptedVM))
                    .first();
        } catch (Exception e) {
            e.printStackTrace();
            return null;
        }
    }
    // Change 업데이트
    public void updateChangeState(String vmNumber, Document changeMap) {
        try {
            String encVm = EncryptionUtil.encrypt(String.valueOf(vmNumber));
            Document update = new Document();
            for (Map.Entry<String, Object> entry : changeMap.entrySet()) {
                update.append(entry.getKey(), entry.getValue());
            }

            database.getCollection("changeStorage").updateOne(
                    Filters.eq("vmNumber", encVm),
                    new Document("$set", update),
                    new UpdateOptions().upsert(true)
            );
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public boolean hasChangeData(String vmNumber) {
        try {
            String encVm = EncryptionUtil.encrypt(String.valueOf(vmNumber));
            Document doc = database.getCollection("changeStorage")
                    .find(new Document("vmNumber", encVm))
                    .first();
            return doc != null;
        } catch (Exception e) {
            e.printStackTrace();
            return false;
        }
    }

    public void updateAdminPassword(String vmNumber, String encryptedPw) {
        try {
            String encVm = EncryptionUtil.encrypt(String.valueOf(vmNumber));
            database.getCollection("admin_passwords").updateOne(
                    new Document("vmNumber", encVm),
                    new Document("$set", new Document("password", encryptedPw)),
                    new UpdateOptions().upsert(true)
            );
        } catch (Exception e) {
            e.printStackTrace();
        }
    }


    public MongoCollection<Document> getChangeStorageCollection() {
        return database.getCollection("changeStorage");
    }


    public void updateChangeStorage(String vmNumber, Map<String, Integer> changeMap) {
        try {
            // vmNumber 암호화
            String encryptedVmNumber = EncryptionUtil.encrypt(String.valueOf(vmNumber));

            Document encryptedDoc = new Document();
            for (Map.Entry<String, Integer> entry : changeMap.entrySet()) {
                String key = entry.getKey(); // 평문 key (예: "1000")
                String encryptedValue = EncryptionUtil.encrypt(String.valueOf(entry.getValue()));
                encryptedDoc.append(key, encryptedValue); // key는 평문, value만 암호화
            }

            encryptedDoc.append("vmNumber", encryptedVmNumber);

            getChangeStorageCollection().updateOne(
                    new Document("vmNumber", encryptedVmNumber), // 필터도 암호화된 vmNumber로
                    new Document("$set", encryptedDoc),
                    new UpdateOptions().upsert(true)
            );

            System.out.println("[MongoDB] 거스름돈 저장 완료 (key=평문, value/번호=암호화)");
        } catch (Exception e) {
            System.err.println("[MongoDB] updateChangeStorage 실패: " + e.getMessage());
            e.printStackTrace();
        }
    }


    public Map<Integer, Integer> getChangeStorage(String vmNumber) {
        Map<Integer, Integer> changeMap = new HashMap<>();

        try {
            String encVm = EncryptionUtil.encrypt(String.valueOf(vmNumber));
            Document doc = getChangeStorageCollection().find(new Document("vmNumber", encVm)).first();

            if (doc != null) {
                for (String key : doc.keySet()) {
                    if (key.equals("_id") || key.equals("vmNumber")) continue;
                    try {
                        String decrypted = EncryptionUtil.decrypt(doc.getString(key));
                        changeMap.put(Integer.parseInt(key), Integer.parseInt(decrypted));
                    } catch (Exception e) {
                        e.printStackTrace();  // 개별 필드 복호화 실패
                    }
                }
            }
        } catch (Exception e) {
            e.printStackTrace();  // vmNumber 암호화 실패
        }

        return changeMap;
    }




    // 초기 관리자 비밀번호 설정
    public void initializeAdminPasswordIfAbsent(String vmNumber) {
        MongoCollection<Document> collection = database.getCollection("admin_passwords");
        try {
            String encVm = EncryptionUtil.encrypt(String.valueOf(vmNumber));
            Document existing = collection.find(new Document("vmNumber", encVm)).first();

            if (existing == null) {
                String encrypted = EncryptionUtil.encrypt("1234");
                Document doc = new Document("vmNumber", encVm)
                        .append("password", encrypted);
                collection.insertOne(doc);
                System.out.println("초기 관리자 비밀번호 저장 완료 (암호화된 상태)");
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    // 관리자 비밀번호 업데이트


    // 관리자 비밀번호 조회
    public String getAdminPassword(String vmNumber) {
        try {
            String encVm = EncryptionUtil.encrypt(String.valueOf(vmNumber));
            Document doc = database.getCollection("admin_passwords")
                    .find(new Document("vmNumber", encVm))
                    .first();
            if (doc != null && doc.getString("password") != null) {
                return doc.getString("password");
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        return null;
    }

    public void insertAdditionalDrinksIfMissing(String vmNumber) {
        try {
            List<Document> existing = getAllDrinks(vmNumber);
            Set<String> existingNames = new HashSet<>();

            for (Document doc : existing) {
                String name = EncryptionUtil.decrypt(doc.getString("name"));
                existingNames.add(name);
            }

            if (!existingNames.contains("믹스커피")) {
                insertDrink(vmNumber, 6, "믹스커피", 200); // ID 6
            }

            if (!existingNames.contains("고급믹스커피")) {
                insertDrink(vmNumber, 7, "고급믹스커피", 300); // ID 7
            }

        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private void saveSaleToFile(String vmNumber, String drinkName, int price, String date) {
        try {
            String filename = "sales_log_" + vmNumber + ".txt";
            String line = String.format("[%s] %s 판매: %d원\n", date, drinkName, price);

            FileWriter fw = new FileWriter(filename, true); // true → append 모드
            fw.write(line);
            fw.close();
        } catch (IOException e) {
            System.err.println("[파일 기록 실패] " + e.getMessage());
        }
    }








}
