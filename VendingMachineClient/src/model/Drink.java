package model;

import java.util.*;

public class Drink {
    private String name;
    private int price;
    private final Queue<Integer> stock;
    private final Stack<String> purchaseStack;
    private final Map<Integer, Map<Integer, Map<Integer, Integer>>> salesRecord;

    public Drink(String name, int price) {
        this.name = name;
        this.price = price;
        this.stock = new LinkedList<>();
        for (int i = 0; i < 10; i++) {
            stock.add(1);
        }
        this.purchaseStack = new Stack<>();
        this.salesRecord = new HashMap<>();
    }

    public synchronized String getName() {
        return name;
    }

    public synchronized void setName(String name) {
        this.name = name;
    }

    public synchronized int getPrice() {
        return price;
    }

    public synchronized void setPrice(int price) {
        this.price = price;
    }

    public synchronized int getStock() {
        return stock.size();
    }

    public synchronized void reduceStock() {
        if (!stock.isEmpty()) {
            stock.poll();
        }
    }

    public synchronized void restock(int quantity) {
        for (int i = 0; i < quantity; i++) {
            stock.add(1);
        }
    }

    public synchronized boolean isSoldOut() {
        return stock.isEmpty();
    }

    public synchronized void addPurchase(String drinkName) {
        purchaseStack.push(drinkName);
    }

    public synchronized String getLastPurchase() {
        if (!purchaseStack.isEmpty()) {
            return purchaseStack.pop();
        }
        return null;
    }

    public synchronized boolean hasPurchases() {
        return !purchaseStack.isEmpty();
    }

    public synchronized void addSale(int year, int month, int day) {
        salesRecord.putIfAbsent(year, new HashMap<>());
        Map<Integer, Map<Integer, Integer>> yearMap = salesRecord.get(year);

        yearMap.putIfAbsent(month, new HashMap<>());
        Map<Integer, Integer> monthMap = yearMap.get(month);

        monthMap.put(day, monthMap.getOrDefault(day, 0) + 1);
    }

    public synchronized Map<Integer, Map<Integer, Map<Integer, Integer>>> getSalesRecord() {
        return salesRecord;
    }

    public synchronized void setStock(int stock) {
        this.stock.clear();
        for (int i = 0; i < stock; i++) {
            this.stock.add(1);
        }
    }
}
