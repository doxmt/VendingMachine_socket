package model;

import java.util.HashMap;
import java.util.LinkedList;
import java.util.Map;
import java.util.Queue;
import java.util.Stack;

public class Drink {
    private String name;
    private int price;
    private Queue<Integer> stock; // LinkedList를 Queue로 사용
    private Stack<String> purchaseStack; // 구매 기록을 관리하는 스택
    private Map<Integer, Map<Integer, Map<Integer, Integer>>> salesRecord; // 판매 기록을 관리하는 Map

    public Drink(String name, int price) {
        this.name = name;
        this.price = price;
        this.stock = new LinkedList<>();
        for (int i = 0; i < 10; i++) {
            stock.add(1); // 기본 재고 10개로 초기화
        }
        this.purchaseStack = new Stack<>();
        this.salesRecord = new HashMap<>();
    }

    public String getName() { // 음료 이름 반환
        return name;
    }

    public void setName(String name) { // 음료 이름 설정
        this.name = name;
    }

    public int getPrice() { // 음료 가격 반환
        return price;
    }

    public void setPrice(int price) { //음료 가격 설정
        this.price = price;
    }

    public int getStock() { // 음료의 재고 수량 반환
        return stock.size();
    }

    public void reduceStock() { // 음료의 재고 감소
        if (!stock.isEmpty()) {
            stock.poll(); // Queue에서 가장 오래된 재고를 소진
        }
    }

    public void restock(int quantity) { // 음료 재고 추가
        for (int i = 0; i < quantity; i++) {
            stock.add(1); // 새로운 재고 추가
        }
    }

    public boolean isSoldOut() { // 재고가 없는지 확인
        return stock.isEmpty();
    }

    public void addPurchase(String drinkName) { //구매 기록 추가
        purchaseStack.push(drinkName);
    }

    public String getLastPurchase() { // 마지막 구매 기록 반환
        if (!purchaseStack.isEmpty()) {
            return purchaseStack.pop();
        }
        return null;
    }

    public boolean hasPurchases() { // 구매 기록 확인
        return !purchaseStack.isEmpty();
    }

    public void addSale(int year, int month, int day) { //판매 기록 추가 
        salesRecord.putIfAbsent(year, new HashMap<>());
        Map<Integer, Map<Integer, Integer>> yearMap = salesRecord.get(year);

        yearMap.putIfAbsent(month, new HashMap<>());
        Map<Integer, Integer> monthMap = yearMap.get(month);

        monthMap.put(day, monthMap.getOrDefault(day, 0) + 1);
    }

    public Map<Integer, Map<Integer, Map<Integer, Integer>>> getSalesRecord() {
        return salesRecord;
    }

    public void setStock(int stock) {
        this.stock.clear(); // 기존 재고 비움
        for (int i = 0; i < stock; i++) {
            this.stock.add(1); // 지정된 수량만큼 재고 추가
        }
    }

}
