// ServerMongoDBManager.java
// 서버 측 MongoDB 저장용 클래스 (암호화 적용 - vmNumber 포함)
// 주요 컬렉션: sales, drinks, inventory

package db;

import com.mongodb.client.*;
import com.mongodb.client.model.UpdateOptions;
import org.bson.Document;
import util.EncryptionUtil_Server;

import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.Map;
import java.util.Properties;

public class ServerMongoDBManager {
    private static final ServerMongoDBManager instance = new ServerMongoDBManager();
    private static final String URI;
    private static final String DB_NAME = "vending_machine_server";
    private static final MongoClient mongoClient;
    private static final MongoDatabase database;

    static {
        String uriTemp = "";
        try {
            Properties props = new Properties();
            try (InputStream is = ServerMongoDBManager.class.getClassLoader().getResourceAsStream("config.properties")) {
                if (is == null) {
                    throw new IOException("config.properties 파일을 classpath에서 찾을 수 없습니다.");
                }
                props.load(is);
                uriTemp = props.getProperty("server.mongo.uri");
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
        URI = uriTemp;
        mongoClient = MongoClients.create(URI);
        database = mongoClient.getDatabase(DB_NAME);
    }


    private ServerMongoDBManager() {}

    public static ServerMongoDBManager getInstance() {
        return instance;
    }

    public MongoCollection<Document> getSalesCollection() {
        return database.getCollection("sales");
    }

    public MongoCollection<Document> getInventoryCollection() {
        return database.getCollection("inventory");
    }

    private MongoCollection<Document> getDrinksCollection() {
        return database.getCollection("drinks");
    }

    // 🔐 서버 수신 후 암호화하여 sales 저장
    public void insertEncryptedSale(Map<String, String> data) {
        try {
            MongoCollection<Document> sales = getSalesCollection();

            Document saleDoc = new Document("vmNumber", EncryptionUtil_Server.encrypt(data.get("vmNumber")))
                    .append("drinkName", EncryptionUtil_Server.encrypt(data.get("drinkName")))
                    .append("price", EncryptionUtil_Server.encrypt(data.get("price")))
                    .append("date", EncryptionUtil_Server.encrypt(data.get("date")));

            sales.insertOne(saleDoc);
            System.out.println("[서버] 암호화된 매출 데이터 저장 완료");
        } catch (Exception e) {
            System.err.println("[서버] 매출 저장 오류:");
            e.printStackTrace();
        }
    }

    // 🔐 음료 정보 upsert 저장
    public void insertOrUpdateEncryptedDrink(Map<String, String> data) {
        try {
            MongoCollection<Document> drinks = getDrinksCollection();

            String encryptedVmNumber = EncryptionUtil_Server.encrypt(data.get("vmNumber"));
            String encryptedName = EncryptionUtil_Server.encrypt(data.get("drinkName"));
            String encryptedPrice = EncryptionUtil_Server.encrypt(data.get("price"));
            String encryptedStock = EncryptionUtil_Server.encrypt(data.get("stock"));

            Document filter = new Document("vmNumber", encryptedVmNumber)
                    .append("name", encryptedName);

            Document update = new Document("$set", new Document("vmNumber", encryptedVmNumber)
                    .append("name", encryptedName)
                    .append("defaultPrice", encryptedPrice)
                    .append("stock", encryptedStock));

            drinks.updateOne(filter, update, new UpdateOptions().upsert(true));
            System.out.println("[서버] 음료 정보 저장 완료");

        } catch (Exception e) {
            System.err.println("[서버] 음료 정보 저장 중 오류 발생:");
            e.printStackTrace();
        }
    }

    // 🔐 재고 정보 저장
    public void insertOrUpdateEncryptedInventory(Map<String, String> data) {
        try {
            MongoCollection<Document> inventory = getInventoryCollection();

            String encryptedVmNumber = EncryptionUtil_Server.encrypt(data.get("vmNumber"));
            String encryptedName = EncryptionUtil_Server.encrypt(data.get("drinkName"));
            String encryptedPrice = EncryptionUtil_Server.encrypt(data.get("price"));
            String encryptedStock = EncryptionUtil_Server.encrypt(data.get("stock"));
            String encryptedDate = EncryptionUtil_Server.encrypt(data.get("date"));

            Document filter = new Document("vmNumber", encryptedVmNumber)
                    .append("name", encryptedName)
                    .append("date", encryptedDate);

            Document update = new Document("$set", new Document("vmNumber", encryptedVmNumber)
                    .append("name", encryptedName)
                    .append("price", encryptedPrice)
                    .append("stock", encryptedStock)
                    .append("date", encryptedDate));

            inventory.updateOne(filter, update, new UpdateOptions().upsert(true));
            System.out.println("[서버] 재고 데이터 저장 완료");

        } catch (Exception e) {
            System.err.println("[서버] 재고 저장 중 오류 발생:");
            e.printStackTrace();
        }
    }
    public void updateDrinkNameEverywhere(String vmNumber, String oldName, String newName) {
        try {
            String encVm = EncryptionUtil_Server.encrypt(vmNumber.trim().toUpperCase());
            String encOldName = EncryptionUtil_Server.encrypt(oldName);
            String encNewName = EncryptionUtil_Server.encrypt(newName);

            Document filterSales = new Document("vmNumber", encVm)
                    .append("drinkName", encOldName);
            Document updateSales = new Document("$set", new Document("drinkName", encNewName));
            getSalesCollection().updateMany(filterSales, updateSales);

            Document filterInventory = new Document("vmNumber", encVm)
                    .append("name", encOldName);
            Document updateInventory = new Document("$set", new Document("name", encNewName));
            getInventoryCollection().updateMany(filterInventory, updateInventory);

            Document filterDrinks = new Document("vmNumber", encVm)
                    .append("name", encOldName);
            Document updateDrinks = new Document("$set", new Document("name", encNewName));
            getDrinksCollection().updateMany(filterDrinks, updateDrinks);

            System.out.println("[서버] 음료 이름 변경 완료: " + oldName + " → " + newName);

        } catch (Exception e) {
            System.err.println("[서버] 음료 이름 변경 중 오류:");
            e.printStackTrace();
        }
    }

}
