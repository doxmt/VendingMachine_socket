package db;

import com.mongodb.client.*;
import com.mongodb.client.model.UpdateOptions;
import org.bson.Document;
import util.EncryptionUtil_Server;

import java.io.FileInputStream;
import java.io.IOException;
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
            props.load(new FileInputStream("config.properties"));
            uriTemp = props.getProperty("server.mongo.uri");
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
    private MongoCollection<Document> getDrinksCollection() {
        return database.getCollection("drinks");
    }


    public void insertEncryptedSale(Map<String, String> data) {
        try {
            MongoCollection<Document> sales = getSalesCollection();

            Document saleDoc = new Document("vmNumber", data.get("vmNumber"))  // 평문 저장
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

    public void insertOrUpdateEncryptedDrink(Map<String, String> data) {
        try {
            MongoCollection<Document> drinks = getDrinksCollection();

            // 평문 vmNumber
            String plainVmNumber = data.get("vmNumber");

            // 암호화된 필드들
            String encryptedName = EncryptionUtil_Server.encrypt(data.get("drinkName"));
            String encryptedPrice = EncryptionUtil_Server.encrypt(data.get("price"));
            String encryptedStock = EncryptionUtil_Server.encrypt(data.get("stock"));

            // 검색 기준 (vmNumber는 평문, 이름은 암호화)
            Document filter = new Document("vmNumber", plainVmNumber)
                    .append("name", encryptedName);

            // upsert 문서
            Document update = new Document("$set", new Document("vmNumber", plainVmNumber)
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

    public MongoCollection<Document> getInventoryCollection() {
        return database.getCollection("inventory");
    }

    public void insertOrUpdateEncryptedInventory(Map<String, String> data) {
        try {
            MongoCollection<Document> inventory = getInventoryCollection();

            String vmNumber = data.get("vmNumber"); // 평문
            String encryptedName = EncryptionUtil_Server.encrypt(data.get("drinkName"));
            String encryptedPrice = EncryptionUtil_Server.encrypt(data.get("price"));
            String encryptedStock = EncryptionUtil_Server.encrypt(data.get("stock"));
            String encryptedDate = EncryptionUtil_Server.encrypt(data.get("date"));

            Document filter = new Document("vmNumber", vmNumber)
                    .append("name", encryptedName)
                    .append("date", encryptedDate);

            Document update = new Document("$set", new Document("vmNumber", vmNumber)
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

}
