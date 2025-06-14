package db;

import com.mongodb.client.*;
import com.mongodb.client.model.Filters;
import com.mongodb.client.model.Projections;
import org.bson.Document;
import com.mongodb.client.model.UpdateOptions;
import util.EncryptionUtil;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.ArrayList;

import java.io.FileInputStream;
import java.io.IOException;
import java.util.Map;
import java.util.Properties;
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
        MongoCollection<Document> sales = getSalesCollection();
        Document doc = new Document("vmNumber", vmNumber)
                .append("drinkName", drinkName)
                .append("price", price)
                .append("quantity", 1)
                .append("date", date);
        sales.insertOne(doc);
    }

    // ----------------- inventory -----------------
    public MongoCollection<Document> getInventoryCollection() {
        return database.getCollection("inventory");
    }

    public void upsertInventory(int vmNumber, String drinkName, int price, int stock, LocalDate date) {
        Document filter = new Document("vmNumber", vmNumber).append("drinkName", drinkName);
        Document update = new Document("$set", new Document("price", price)
                .append("stock", stock)
                .append("lastUpdated", date.toString()));
        getInventoryCollection().updateOne(filter, update, new UpdateOptions().upsert(true));
    }

    public Document getInventory(int vmNumber, String drinkName) {
        return getInventoryCollection()
                .find(new Document("vmNumber", vmNumber).append("drinkName", drinkName))
                .first();
    }

    // ----------------- drinks -----------------
    public MongoCollection<Document> getDrinksCollection() {
        return database.getCollection("drinks");
    }

    public void insertDrink(int vmNumber, int drinkId, String name, int defaultPrice) {
        Document doc = new Document("vmNumber", vmNumber)
                .append("drinkId", drinkId)
                .append("name", name)
                .append("defaultPrice", defaultPrice)
                .append("stock", 10);
        getDrinksCollection().insertOne(doc);
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
        MongoCollection<Document> collection = database.getCollection("operations");
        Document doc = new Document("vmNumber", vmNumber)
                .append("operation", operation)
                .append("target", target)
                .append("detail", detail)
                .append("timestamp", LocalDateTime.now().toString());
        collection.insertOne(doc);
    }

    public List<Document> getDrinksByVMNumber(int vmNumber) {
        return getDrinksCollection()
                .find(new Document("vmNumber", vmNumber))
                .into(new ArrayList<>());
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
        getInventoryCollection().updateOne(
                new Document("vmNumber", vmNumber).append("type", "amount"),
                new Document("$set", new Document("currentAmount", amount)),
                new UpdateOptions().upsert(true)
        );
    }

    public int getCurrentAmount(int vmNumber) {
        Document doc = getInventoryCollection()
                .find(new Document("vmNumber", vmNumber).append("type", "amount"))
                .first();
        return doc != null ? doc.getInteger("currentAmount", 0) : 0;
    }

    public void addToStoredAmount(int vmNumber, int amount) {
        Document query = new Document("vmNumber", vmNumber);
        Document update = new Document("$inc", new Document("storedAmount", amount));
        getMachineStateCollection().updateOne(query, update, new UpdateOptions().upsert(true));
    }

    public int collectStoredAmount(int vmNumber) {
        Document state = getMachineStateCollection().findOneAndUpdate(
                new Document("vmNumber", vmNumber),
                new Document("$set", new Document("storedAmount", 0))
        );
        return state != null ? state.getInteger("storedAmount", 0) : 0;
    }

    public void incrementStoredMoney(int vmNumber, int denomination, int count) {
        Document query = new Document("vmNumber", vmNumber);
        Document update = new Document("$inc", new Document("storedMoney." + denomination, count));
        getMachineStateCollection().updateOne(query, update, new UpdateOptions().upsert(true));
    }

    public Document getStoredMoney(int vmNumber) {
        Document doc = getMachineStateCollection()
                .find(new Document("vmNumber", vmNumber))
                .projection(Projections.include("storedMoney"))
                .first();
        return (doc != null) ? (Document) doc.get("storedMoney") : new Document();
    }

    public void resetStoredMoney(int vmNumber) {
        Document query = new Document("vmNumber", vmNumber);
        Document update = new Document("$set", new Document("storedMoney", new Document()));
        getMachineStateCollection().updateOne(query, update);
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

    public void updateChangeStorage(int vmNumber, Document changeDoc) {
        database.getCollection("changeStorage").updateOne(
                Filters.eq("vmNumber", vmNumber),
                new Document("$set", changeDoc.append("vmNumber", vmNumber)),
                new UpdateOptions().upsert(true)
        );
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
