package db;

import com.mongodb.client.*;
import com.mongodb.client.model.Filters;
import com.mongodb.client.model.Projections;
import org.bson.Document;
import com.mongodb.client.model.UpdateOptions;
import util.EncryptionUtil;

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
            props.load(new FileInputStream("config.properties"));
            uriTemp = props.getProperty("mongo.uri");
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

    public void insertSale(int vmNumber, String drinkName, int price, String date) {
        try {
            MongoCollection<Document> sales = getSalesCollection();

            Document saleDoc = new Document("vmNumber", vmNumber)  // 평문 저장
                    .append("drinkName", EncryptionUtil.encrypt(drinkName))
                    .append("price", EncryptionUtil.encrypt(String.valueOf(price)))
                    .append("date", EncryptionUtil.encrypt(date));

            sales.insertOne(saleDoc);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }




    public List<Document> getSalesByVM(int vmNumber) {
        List<Document> result = new ArrayList<>();
        MongoCollection<Document> collection = getSalesCollection();

        for (Document doc : collection.find(new Document("vmNumber", vmNumber))) {
            try {
                String name = EncryptionUtil.decrypt(doc.getString("drinkName"));
                int price = Integer.parseInt(EncryptionUtil.decrypt(doc.getString("price")));
                String date = EncryptionUtil.decrypt(doc.getString("date"));

                result.add(new Document()
                        .append("vmNumber", vmNumber)
                        .append("drinkName", name)
                        .append("price", price)
                        .append("date", date));
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
        return result;
    }


    // ----------------- inventory -----------------
    public MongoCollection<Document> getInventoryCollection() {
        return database.getCollection("inventory");
    }

    public void upsertInventory(int vmNumber, String drinkName, int price, int stock, LocalDate date) {
        try {
            String encPrice = EncryptionUtil.encrypt(String.valueOf(price));
            String encStock = EncryptionUtil.encrypt(String.valueOf(stock));
            String encDate = EncryptionUtil.encrypt(date.toString());

            Document filter = new Document("vmNumber", vmNumber)
                    .append("drinkName", drinkName);

            Document update = new Document("$set", new Document("price", encPrice)
                    .append("stock", encStock)
                    .append("lastUpdated", encDate));

            getInventoryCollection().updateOne(filter, update, new UpdateOptions().upsert(true));

            // ✅ 서버로 전송
            Map<String, String> inventoryData = new HashMap<>();
            inventoryData.put("type", "inventory");
            inventoryData.put("vmNumber", String.valueOf(vmNumber)); // ⚠️ 서버에는 평문으로!
            inventoryData.put("drinkName", drinkName);
            inventoryData.put("price", String.valueOf(price));
            inventoryData.put("stock", String.valueOf(stock));
            inventoryData.put("date", date.toString());

            client.ClientSender.sendDataToServer(inventoryData);

        } catch (Exception e) {
            e.printStackTrace();
        }
    }



    public Document getInventory(int vmNumber, String drinkName) {
        try {
            Document doc = getInventoryCollection()
                    .find(new Document("vmNumber", vmNumber)
                            .append("drinkName", drinkName))
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

    public void insertDrink(int vmNumber, int drinkId, String name, int defaultPrice) {
        try {
            String encryptedName = EncryptionUtil.encrypt(name);
            String encryptedPrice = EncryptionUtil.encrypt(String.valueOf(defaultPrice));
            String encryptedStock = EncryptionUtil.encrypt("10"); // 초기 재고

            Document doc = new Document("vmNumber", vmNumber)
                    .append("drinkId", drinkId)
                    .append("name", encryptedName)
                    .append("defaultPrice", encryptedPrice)
                    .append("stock", encryptedStock);

            getDrinksCollection().insertOne(doc);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }


    public List<Document> getAllDrinks(int vmNumber) {
        List<Document> drinks = new ArrayList<>();
        getDrinksCollection().find(new Document("vmNumber", vmNumber)).into(drinks);
        return drinks;
    }

    public void insertInitialDrinks(int vmNumber) {
        MongoCollection<Document> drinksCollection = getDrinksCollection();
        long count = drinksCollection.countDocuments(new Document("vmNumber", vmNumber));
        if (count > 0) return;

        String[] names = {"물", "커피", "이온 음료", "고급 커피", "탄산 음료", "특화 음료"};
        int[] prices = {450, 500, 550, 700, 750, 800};

        for (int i = 0; i < names.length; i++) {
            insertDrink(vmNumber, i, names[i], prices[i]);
        }
    }

    // ----------------- operations -----------------
    public void insertAdminOperation(int vmNumber, String operation, String target, Document detail) {
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


            Document doc = new Document("vmNumber", vmNumber)
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


    public List<Document> getOperationsByVM(int vmNumber) {
        List<Document> result = new ArrayList<>();
        MongoCollection<Document> collection = getOperationsCollection();

        for (Document doc : collection.find(new Document("vmNumber", vmNumber))) {
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
                e.printStackTrace();
            }
        }

        return result;
    }



    public List<Document> getDrinksByVMNumber(int vmNumber) {
        List<Document> decryptedDrinks = new ArrayList<>();
        FindIterable<Document> docs = getDrinksCollection().find(new Document("vmNumber", vmNumber));

        for (Document doc : docs) {
            try {
                String name = EncryptionUtil.decrypt(doc.getString("name"));
                int price = Integer.parseInt(EncryptionUtil.decrypt(doc.getString("defaultPrice")));
                int stock = Integer.parseInt(EncryptionUtil.decrypt(doc.getString("stock")));

                Document decrypted = new Document("name", name)
                        .append("defaultPrice", price)
                        .append("stock", stock);
                decryptedDrinks.add(decrypted);
            } catch (Exception e) {
                e.printStackTrace(); // 복호화 실패 시 로깅
            }
        }

        return decryptedDrinks;
    }


    public void updateDrinkNameEverywhere(int vmNumber, String oldName, String newName) {
        Document filter = new Document("vmNumber", vmNumber).append("drinkName", oldName);
        Document update = new Document("$set", new Document("drinkName", newName));

        // sales 컬렉션 이름 변경
        getSalesCollection().updateMany(filter, update);

        // inventory 컬렉션 이름 변경
        getInventoryCollection().updateMany(filter, update);
    }
    public void updateCurrentAmount(int vmNumber, int amount) {
        try {
            String encryptedAmount = EncryptionUtil.encrypt(String.valueOf(amount));
            getMachineStateCollection().updateOne(
                    new Document("vmNumber", vmNumber),
                    new Document("$set", new Document("currentAmount", encryptedAmount)),
                    new UpdateOptions().upsert(true)
            );
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
    public int getCurrentAmount(int vmNumber) {
        try {
            Document doc = getMachineStateCollection()
                    .find(new Document("vmNumber", vmNumber))
                    .first();
            if (doc != null && doc.containsKey("currentAmount")) {
                return Integer.parseInt(EncryptionUtil.decrypt(doc.getString("currentAmount")));
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        return 0;
    }

    public void addToStoredAmount(int vmNumber, int amountToAdd) {
        try {
            Document doc = getMachineStateCollection().find(new Document("vmNumber", vmNumber)).first();
            int currentStored = 0;

            if (doc != null && doc.containsKey("storedAmount")) {
                currentStored = Integer.parseInt(EncryptionUtil.decrypt(doc.getString("storedAmount")));
            }

            int updated = currentStored + amountToAdd;
            getMachineStateCollection().updateOne(
                    new Document("vmNumber", vmNumber),
                    new Document("$set", new Document("storedAmount", EncryptionUtil.encrypt(String.valueOf(updated)))),
                    new UpdateOptions().upsert(true)
            );
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public int collectStoredAmount(int vmNumber) {
        try {
            Document doc = getMachineStateCollection().find(new Document("vmNumber", vmNumber)).first();

            int storedAmount = 0;
            if (doc != null && doc.containsKey("storedAmount")) {
                storedAmount = Integer.parseInt(EncryptionUtil.decrypt(doc.getString("storedAmount")));
            }

            getMachineStateCollection().updateOne(
                    new Document("vmNumber", vmNumber),
                    new Document("$set", new Document("storedAmount", EncryptionUtil.encrypt("0")))
            );

            return storedAmount;
        } catch (Exception e) {
            e.printStackTrace();
            return 0;
        }
    }

    public void incrementStoredMoney(int vmNumber, int denomination, int count) {
        try {
            Document doc = getMachineStateCollection().find(new Document("vmNumber", vmNumber)).first();
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
                    new Document("vmNumber", vmNumber),
                    new Document("$set", new Document("storedMoney", storedMoney)),
                    new UpdateOptions().upsert(true)
            );
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public Document getStoredMoney(int vmNumber) {
        Document result = new Document();
        try {
            Document doc = getMachineStateCollection()
                    .find(new Document("vmNumber", vmNumber))
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

    public void resetStoredMoney(int vmNumber) {
        getMachineStateCollection().updateOne(
                new Document("vmNumber", vmNumber),
                new Document("$set", new Document("storedMoney", new Document()))
        );
    }


    // Change 정보 조회
    public Document getChangeState(int vmNumber) {
        return database.getCollection("changeStorage")
                .find(Filters.eq("vmNumber", vmNumber))
                .first();
    }

    // Change 업데이트
    public void updateChangeState(int vmNumber, Document changeMap) {
        Document update = new Document();
        for (Map.Entry<String, Object> entry : changeMap.entrySet()) {
            update.append(entry.getKey(), entry.getValue());
        }

        database.getCollection("changeStorage").updateOne(
                Filters.eq("vmNumber", vmNumber),
                new Document("$set", update),
                new UpdateOptions().upsert(true)
        );
    }
    public boolean hasChangeData(int vmNumber) {
        Document doc = database.getCollection("changeStorage")
                .find(new Document("vmNumber", vmNumber))
                .first();
        return doc != null;
    }

    public MongoCollection<Document> getChangeStorageCollection() {
        return database.getCollection("changeStorage");
    }


    public void updateChangeStorage(int vmNumber, Map<String, Integer> changeMap) {
        try {
            Document encryptedDoc = new Document();

            for (Map.Entry<String, Integer> entry : changeMap.entrySet()) {
                String key = entry.getKey();
                String encryptedValue = EncryptionUtil.encrypt(String.valueOf(entry.getValue()));
                encryptedDoc.append(key, encryptedValue);
            }

            // vmNumber 포함시켜서 저장할 경우, $set 내부로 넣기
            encryptedDoc.append("vmNumber", vmNumber);

            getChangeStorageCollection().updateOne(
                    new Document("vmNumber", vmNumber),
                    new Document("$set", encryptedDoc),
                    new UpdateOptions().upsert(true)
            );

        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public Map<Integer, Integer> getChangeStorage(int vmNumber) {
        Map<Integer, Integer> changeMap = new HashMap<>();
        Document doc = getChangeStorageCollection().find(new Document("vmNumber", vmNumber)).first();

        if (doc != null) {
            for (String key : doc.keySet()) {
                if (key.equals("_id") || key.equals("vmNumber")) continue;
                try {
                    String decrypted = EncryptionUtil.decrypt(doc.getString(key));
                    changeMap.put(Integer.parseInt(key), Integer.parseInt(decrypted));
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }
        }

        return changeMap;
    }



    // 초기 관리자 비밀번호 설정
    public void initializeAdminPasswordIfAbsent(int vmNumber) {
        MongoCollection<Document> collection = database.getCollection("admin_passwords");
        Document existing = collection.find(new Document("vmNumber", vmNumber)).first();

        if (existing == null) {
            try {
                String encrypted = EncryptionUtil.encrypt("1234");
                Document doc = new Document("vmNumber", vmNumber)
                        .append("password", encrypted);
                collection.insertOne(doc);
                System.out.println("초기 관리자 비밀번호 저장 완료 (암호화된 상태)");
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
    }

    // 관리자 비밀번호 업데이트
    public void updateAdminPassword(int vmNumber, String encryptedPw) {
        database.getCollection("admin_passwords").updateOne(
                new Document("vmNumber", vmNumber),
                new Document("$set", new Document("password", encryptedPw)),
                new UpdateOptions().upsert(true)
        );
    }

    // 관리자 비밀번호 조회
    public String getAdminPassword(int vmNumber) {
        Document doc = database.getCollection("admin_passwords")
                .find(new Document("vmNumber", vmNumber))
                .first();
        if (doc != null && doc.getString("password") != null) {
            return doc.getString("password");
        }
        return null;
    }





}
