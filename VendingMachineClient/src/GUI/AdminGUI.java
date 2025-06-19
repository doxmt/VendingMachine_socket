package GUI;

import model.Drink;
import model.Money;

import javax.swing.*;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.time.LocalDate;
import java.util.*;
import java.util.regex.Pattern;
import java.util.List;
import db.MongoDBManager;
import org.bson.Document;
import util.EncryptionUtil;


public class AdminGUI extends JFrame {
    private VendingMachineGUI vendingMachineGUI;



    public AdminGUI(VendingMachineGUI vendingMachineGUI) {
        this.vendingMachineGUI = vendingMachineGUI;

        setTitle("관리자 메뉴");
        setSize(400, 400);
        setDefaultCloseOperation(JFrame.DISPOSE_ON_CLOSE);

        JPanel adminPanel = new JPanel();
        adminPanel.setLayout(new GridLayout(11, 1, 10, 10));

        JButton changePasswordButton = new JButton("비밀번호 변경");
        JButton dailySalesButton = new JButton("일별 총 매출");
        JButton monthlySalesButton = new JButton("월별 총 매출");
        JButton itemDailySalesButton = new JButton("음료별 일별 매출");
        JButton itemMonthlySalesButton = new JButton("음료별 월별 매출");
        JButton restockButton = new JButton("음료 재고 보충");
        JButton collectMoneyButton = new JButton("수금");
        JButton changeItemInfoButton = new JButton("음료 가격/이름 변경");
        JButton viewRecentPurchasesButton = new JButton("최근 구매 내역");
        JButton refillChangeButton = new JButton("거스름돈 보충");


        adminPanel.add(refillChangeButton);


        adminPanel.add(changePasswordButton);
        adminPanel.add(dailySalesButton);
        adminPanel.add(monthlySalesButton);
        adminPanel.add(itemDailySalesButton);
        adminPanel.add(itemMonthlySalesButton);
        adminPanel.add(restockButton);
        adminPanel.add(collectMoneyButton);
        adminPanel.add(changeItemInfoButton);
        adminPanel.add(viewRecentPurchasesButton);

        changePasswordButton.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e)
            {
                changePassword();
            }
        });

        dailySalesButton.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                showDailyTotalSales("일별 총 매출", "일별 총 매출");
            }
        });

        monthlySalesButton.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                showMonthlyTotalSales("월별 총 매출", "월별 총 매출");
            }
        });

        itemDailySalesButton.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                showItemSales("일별 음료별 매출", "일별 음료별 매출", true);
            }
        });

        itemMonthlySalesButton.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                showItemSales("월별 음료별 매출", "월별 음료별 매출", false);
            }
        });

        restockButton.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                searchAndRestockDrink();
            }
        });

        collectMoneyButton.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                collectMoney();
            }
        });

        changeItemInfoButton.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                changeItemInfo();
            }
        });

        refillChangeButton.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                refillChangeTo10Each();
            }
        });


        viewRecentPurchasesButton.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                viewLastPurchase();
            }
        });

        addWindowListener(new java.awt.event.WindowAdapter() {
            @Override
            public void windowClosing(java.awt.event.WindowEvent windowEvent) {
                vendingMachineGUI.exitAdminMode(); // 관리자 모드 종료 시 관리자 모드 비활성화
            }
        });

        add(adminPanel);
        setVisible(true);
    }

    private void refillChangeTo10Each() {
        String vmNumber = vendingMachineGUI.getVmNumber();
        MongoDBManager dbManager = MongoDBManager.getInstance();

        Document changeDoc = new Document();
        changeDoc.append("10", 10);
        changeDoc.append("50", 10);
        changeDoc.append("100", 10);
        changeDoc.append("500", 10);
        changeDoc.append("1000", 10);
        changeDoc.append("5000", 10);

        // 🔁 DB에 저장 (암호화된 updateChangeStorage 이용)
        dbManager.updateChangeStorage(vmNumber, Map.of(
                "10", 10, "50", 10, "100", 10,
                "500", 10, "1000", 10, "5000", 10
        ));

        // 📄 관리자 작업 로그 기록
        Document detail = new Document("10", 10)
                .append("50", 10)
                .append("100", 10)
                .append("500", 10)
                .append("1000", 10)
                .append("5000", 10);
        dbManager.insertAdminOperation(vmNumber, "거스름돈 보충", "change", detail);

        // 📊 sales 기록 (✅ 암호화된 insertSale 사용)
        dbManager.insertSale(vmNumber, "거스름돈 보충", 0, LocalDate.now().toString());

        // ✔️ 안내 메시지
        JOptionPane.showMessageDialog(this, "각 화폐 단위를 10개로 보충했습니다.", "보충 완료", JOptionPane.INFORMATION_MESSAGE);
    }



    private void changePassword() {
        JPanel panel = new JPanel(new GridLayout(2, 2));
        JLabel oldPasswordLabel = new JLabel("기존 비밀번호:");
        JPasswordField oldPasswordField = new JPasswordField();
        JLabel newPasswordLabel = new JLabel("새 비밀번호:");
        JPasswordField newPasswordField = new JPasswordField();
        panel.add(oldPasswordLabel);
        panel.add(oldPasswordField);
        panel.add(newPasswordLabel);
        panel.add(newPasswordField);

        int result = JOptionPane.showConfirmDialog(this, panel, "비밀번호 변경", JOptionPane.OK_CANCEL_OPTION, JOptionPane.PLAIN_MESSAGE);

        if (result == JOptionPane.OK_OPTION) {
            String oldPassword = new String(oldPasswordField.getPassword());
            String newPassword = new String(newPasswordField.getPassword());

            try {
                // 현재 암호화된 비밀번호 복호화하여 확인
                String encryptedStoredPassword = MongoDBManager.getInstance().getAdminPassword(vendingMachineGUI.getVmNumber());
                String decryptedPassword = EncryptionUtil.decrypt(encryptedStoredPassword);
                if (!oldPassword.equals(decryptedPassword)) {
                    JOptionPane.showMessageDialog(this, "기존 비밀번호가 틀렸습니다.", "오류", JOptionPane.ERROR_MESSAGE);
                    return;
                }

                if (!isValidPassword(newPassword)) {
                    JOptionPane.showMessageDialog(this, "새 비밀번호는 특수문자 및 숫자가 하나 이상 포함된 8자리 이상이어야 합니다.", "오류", JOptionPane.ERROR_MESSAGE);
                    return;
                }

                // 암호화 후 DB 저장 및 메모리 반영
                String encryptedNewPw = EncryptionUtil.encrypt(newPassword);
                MongoDBManager.getInstance().updateAdminPassword(vendingMachineGUI.getVmNumber(), encryptedNewPw);
                VendingMachineGUI.encryptedAdminPassword = encryptedNewPw;
                JOptionPane.showMessageDialog(this, "비밀번호가 성공적으로 변경되었습니다.", "성공", JOptionPane.INFORMATION_MESSAGE);
            } catch (Exception e) {
                e.printStackTrace();
                JOptionPane.showMessageDialog(this, "암호화 또는 복호화 중 오류가 발생했습니다.", "오류", JOptionPane.ERROR_MESSAGE);
            }
        }
    }


    private boolean isValidPassword(String password) {
        if (password.length() < 8) {
            return false;
        }
        boolean hasSpecialChar = Pattern.compile("[^a-zA-Z0-9]").matcher(password).find();
        boolean hasDigit = Pattern.compile("[0-9]").matcher(password).find();
        return hasSpecialChar && hasDigit;
    }

    private void showDailyTotalSales(String title, String salesType) {
        new Thread(() -> {
            Map<Integer, Map<Integer, Map<Integer, Integer>>> totalSales = new HashMap<>();

            for (Drink drink : vendingMachineGUI.drinks) {
                Map<Integer, Map<Integer, Map<Integer, Integer>>> salesRecord = drink.getSalesRecord();

                for (Map.Entry<Integer, Map<Integer, Map<Integer, Integer>>> yearEntry : salesRecord.entrySet()) {
                    int year = yearEntry.getKey();
                    totalSales.putIfAbsent(year, new HashMap<>());
                    Map<Integer, Map<Integer, Integer>> yearMap = totalSales.get(year);

                    for (Map.Entry<Integer, Map<Integer, Integer>> monthEntry : yearEntry.getValue().entrySet()) {
                        int month = monthEntry.getKey();
                        yearMap.putIfAbsent(month, new HashMap<>());
                        Map<Integer, Integer> monthMap = yearMap.get(month);

                        for (Map.Entry<Integer, Integer> dayEntry : monthEntry.getValue().entrySet()) {
                            int day = dayEntry.getKey();
                            int totalAmount = dayEntry.getValue() * drink.getPrice();
                            monthMap.put(day, monthMap.getOrDefault(day, 0) + totalAmount);
                        }
                    }
                }
            }

            StringBuilder message = new StringBuilder(salesType + ":\n");

            for (Map.Entry<Integer, Map<Integer, Map<Integer, Integer>>> yearEntry : totalSales.entrySet()) {
                int year = yearEntry.getKey();
                message.append(year).append("년:\n");
                for (Map.Entry<Integer, Map<Integer, Integer>> monthEntry : yearEntry.getValue().entrySet()) {
                    int month = monthEntry.getKey();
                    message.append("  ").append(month).append("월:\n");
                    for (Map.Entry<Integer, Integer> dayEntry : monthEntry.getValue().entrySet()) {
                        int day = dayEntry.getKey();
                        int totalAmount = dayEntry.getValue();
                        message.append("    ").append(day).append("일: ").append(totalAmount).append("원\n");
                    }
                }
            }

            SwingUtilities.invokeLater(() -> 
                JOptionPane.showMessageDialog(this, message.toString(), title, JOptionPane.INFORMATION_MESSAGE)
            );
        }).start();
    }

    private void showMonthlyTotalSales(String title, String salesType) {
        new Thread(() -> {
            Map<Integer, Map<Integer, Integer>> totalSales = new HashMap<>();

            for (Drink drink : vendingMachineGUI.drinks) {
                Map<Integer, Map<Integer, Map<Integer, Integer>>> salesRecord = drink.getSalesRecord();

                for (Map.Entry<Integer, Map<Integer, Map<Integer, Integer>>> yearEntry : salesRecord.entrySet()) {
                    int year = yearEntry.getKey();
                    totalSales.putIfAbsent(year, new HashMap<>());
                    Map<Integer, Integer> yearMap = totalSales.get(year);

                    for (Map.Entry<Integer, Map<Integer, Integer>> monthEntry : yearEntry.getValue().entrySet()) {
                        int month = monthEntry.getKey();
                        int totalAmount = 0;
                        for (Map.Entry<Integer, Integer> dayEntry : monthEntry.getValue().entrySet()) {
                            int count = dayEntry.getValue();
                            totalAmount += count * drink.getPrice();
                        }
                        yearMap.put(month, yearMap.getOrDefault(month, 0) + totalAmount);
                    }
                }
            }

            StringBuilder message = new StringBuilder(salesType + ":\n");

            for (Map.Entry<Integer, Map<Integer, Integer>> yearEntry : totalSales.entrySet()) {
                int year = yearEntry.getKey();
                message.append(year).append("년:\n");
                for (Map.Entry<Integer, Integer> monthEntry : yearEntry.getValue().entrySet()) {
                    int month = monthEntry.getKey();
                    int totalAmount = monthEntry.getValue();
                    message.append("  ").append(month).append("월: ").append(totalAmount).append("원\n");
                }
            }

            SwingUtilities.invokeLater(() -> 
                JOptionPane.showMessageDialog(this, message.toString(), title, JOptionPane.INFORMATION_MESSAGE)
            );
        }).start();
    }

    private void showItemSales(String title, String salesType, boolean isDaily) {
        StringBuilder message = new StringBuilder(salesType + ":\n");
        List<DrinkSalesRecord> drinkSalesRecords = new ArrayList<>();

        for (Drink drink : vendingMachineGUI.drinks) {
            int totalSales = 0;
            Map<Integer, Map<Integer, Map<Integer, Integer>>> salesRecord = drink.getSalesRecord();

            for (Map<Integer, Map<Integer, Integer>> yearMap : salesRecord.values()) {
                for (Map<Integer, Integer> monthMap : yearMap.values()) {
                    for (int count : monthMap.values()) {
                        totalSales += count;
                    }
                }
            }
            drinkSalesRecords.add(new DrinkSalesRecord(drink.getName(), drink.getPrice(), totalSales));
        }

        Collections.sort(drinkSalesRecords, Comparator.comparingInt(DrinkSalesRecord::getTotalSales).reversed());

        for (DrinkSalesRecord record : drinkSalesRecords) {
            message.append(record.getName()).append(" (").append(record.getPrice()).append("원): ").append(record.getTotalSales()).append("개\n");
        }

        JOptionPane.showMessageDialog(this, message.toString(), title, JOptionPane.INFORMATION_MESSAGE);
    }

    private void searchAndRestockDrink() {
        new Thread(() -> {
            String searchName = JOptionPane.showInputDialog(this, "재고를 보충할 음료 이름을 입력하세요.");
            if (searchName != null) {
                Optional<Drink> drinkOptional = Arrays.stream(vendingMachineGUI.drinks)
                        .filter(drink -> drink.getName().equalsIgnoreCase(searchName))
                        .findFirst();

                if (drinkOptional.isPresent()) {
                    Drink drink = drinkOptional.get();
                    String quantityStr = JOptionPane.showInputDialog(this, "추가할 재고 수량을 입력하세요.");
                    try {
                        int quantity = Integer.parseInt(quantityStr);
                        drink.restock(quantity);

                        // DB 반영
                        MongoDBManager dbManager = MongoDBManager.getInstance();
                        String vmNumber = vendingMachineGUI.getVmNumber(); // 자판기 번호 가져오기
                        dbManager.upsertInventory(vmNumber, drink.getName(), drink.getPrice(), drink.getStock(), LocalDate.now());

                        // 관리자 작업 기록
                        Document detail = new Document("restockedAmount", quantity)
                                .append("newStock", drink.getStock());
                        dbManager.insertAdminOperation(vmNumber, "재고 보충", drink.getName(), detail);

                        // ✅ 자판기 로컬 정보 동기화
                        vendingMachineGUI.reloadDrinksFromDB();

                        SwingUtilities.invokeLater(() -> {
                            JOptionPane.showMessageDialog(this, drink.getName() + "의 재고가 " + quantity + "개 추가되었습니다.", "재고 보충 완료", JOptionPane.INFORMATION_MESSAGE);
                            vendingMachineGUI.updateButtonColors();
                        });
                    } catch (NumberFormatException e) {
                        SwingUtilities.invokeLater(() ->
                                JOptionPane.showMessageDialog(this, "유효한 수량을 입력하세요.", "입력 오류", JOptionPane.ERROR_MESSAGE)
                        );
                    }
                } else {
                    SwingUtilities.invokeLater(() ->
                            JOptionPane.showMessageDialog(this, "해당 음료를 찾을 수 없습니다.", "오류", JOptionPane.ERROR_MESSAGE)
                    );
                }
            }
        }).start();
    }


    private void collectMoney() {
        new Thread(() -> {
            MongoDBManager dbManager = MongoDBManager.getInstance();
            String vmNumber = vendingMachineGUI.getVmNumber();

            // 💰 실제 누적된 수익 금액 가져오기 (→ 자동 초기화)
            int totalCollected = dbManager.collectStoredAmount(vmNumber);

            if (totalCollected <= 0) {
                SwingUtilities.invokeLater(() ->
                        JOptionPane.showMessageDialog(this, "보관된 금액이 없습니다.", "수금 실패", JOptionPane.WARNING_MESSAGE)
                );
                return;
            }

            // 🔍 화폐 단위별 구성 정보 가져오기
            Document storedMoney = dbManager.getStoredMoney(vmNumber);
            Document detail = new Document();

            if (storedMoney != null && !storedMoney.isEmpty()) {
                for (String denomStr : storedMoney.keySet()) {
                    try {
                        int count = storedMoney.getInteger(denomStr, 0);
                        if (count > 0) {
                            detail.append(denomStr + "원", count);
                        }
                    } catch (NumberFormatException ex) {
                        // 무시 (예: _id)
                    }
                }
            }

            detail.append("총 수금", totalCollected);

            // ✅ 수금 기록 + 저장된 지폐 기록 초기화
            dbManager.insertAdminOperation(vmNumber, "collect", "money", detail);
            dbManager.resetStoredMoney(vmNumber);

            // ✅ 팝업 메시지
            StringBuilder msg = new StringBuilder("수금 완료:\n");
            for (Map.Entry<String, Object> entry : detail.entrySet()) {
                msg.append(entry.getKey()).append(": ").append(entry.getValue()).append("\n");
            }

            SwingUtilities.invokeLater(() ->
                    JOptionPane.showMessageDialog(this, msg.toString(), "수금 완료", JOptionPane.INFORMATION_MESSAGE)
            );
        }).start();
    }





    private void changeItemInfo() {
        String[] drinkNames = new String[vendingMachineGUI.drinks.length];
        for (int i = 0; i < vendingMachineGUI.drinks.length; i++) {
            drinkNames[i] = vendingMachineGUI.drinks[i].getName();
        }

        String selectedDrink = (String) JOptionPane.showInputDialog(
                this, "정보를 변경할 음료를 선택하세요.", "음료 정보 변경",
                JOptionPane.QUESTION_MESSAGE, null, drinkNames, drinkNames[0]);

        if (selectedDrink != null) {
            for (Drink drink : vendingMachineGUI.drinks) {
                if (drink.getName().equals(selectedDrink)) {
                    String newName = JOptionPane.showInputDialog(this, "새 음료 이름을 입력하세요.", drink.getName());
                    String newPriceStr = JOptionPane.showInputDialog(this, "새 음료 가격을 입력하세요.", drink.getPrice());

                    if (newName == null || newPriceStr == null) return;

                    try {
                        int newPrice = Integer.parseInt(newPriceStr);
                        if (newPrice % 10 != 0) {
                            throw new NumberFormatException("가격은 10원 단위여야 합니다.");
                        }

                        boolean duplicate = Arrays.stream(vendingMachineGUI.drinks)
                                .anyMatch(d -> d.getName().equals(newName) && !d.getName().equals(selectedDrink));
                        if (duplicate) {
                            JOptionPane.showMessageDialog(this, "이미 존재하는 음료 이름입니다.", "이름 중복", JOptionPane.ERROR_MESSAGE);
                            return;
                        }

                        // 로컬 데이터 반영
                        drink.setName(newName);
                        drink.setPrice(newPrice);

                        MongoDBManager dbManager = MongoDBManager.getInstance();
                        String vmNumber = vendingMachineGUI.getVmNumber();

                        // 🔐 암호화된 필드값 생성
                        String encVm = EncryptionUtil.encrypt(vmNumber.trim().toUpperCase());
                        String encOldName = EncryptionUtil.encrypt(selectedDrink);
                        String encNewName = EncryptionUtil.encrypt(newName);
                        String encNewPrice = EncryptionUtil.encrypt(String.valueOf(newPrice));

                        // drinks 컬렉션 업데이트
                        dbManager.getDrinksCollection().updateOne(
                                new Document("vmNumber", encVm).append("name", encOldName),
                                new Document("$set", new Document("name", encNewName)
                                        .append("defaultPrice", encNewPrice))
                        );

                        // inventory 컬렉션 업데이트
                        dbManager.getInventoryCollection().updateMany(
                                new Document("vmNumber", encVm).append("drinkName", encOldName),
                                new Document("$set", new Document("drinkName", encNewName)
                                        .append("price", encNewPrice))
                        );

                        // sales 컬렉션도 함께 변경
                        dbManager.updateDrinkNameEverywhere(vmNumber, selectedDrink, newName);

                        // UI 반영
                        vendingMachineGUI.reloadDrinksFromDB();
                        vendingMachineGUI.updateButtonColors();

                        JOptionPane.showMessageDialog(this, "음료 정보가 성공적으로 변경되었습니다.", "변경 완료", JOptionPane.INFORMATION_MESSAGE);
                    } catch (NumberFormatException ex) {
                        JOptionPane.showMessageDialog(this, "유효한 가격을 입력하세요. 가격은 10원 단위여야 합니다.", "입력 오류", JOptionPane.ERROR_MESSAGE);
                    } catch (Exception ex) {
                        ex.printStackTrace();
                        JOptionPane.showMessageDialog(this, "DB 수정 중 오류가 발생했습니다.", "오류", JOptionPane.ERROR_MESSAGE);
                    }
                    break;
                }
            }
        }
    }




    private void viewLastPurchase() {
        StringBuilder message = new StringBuilder("최근 구매 내역:\n");

        for (Drink drink : vendingMachineGUI.drinks) {
            if (drink.hasPurchases()) {
                message.append(drink.getName()).append(": ");
                while (drink.hasPurchases()) {
                    message.append(drink.getLastPurchase()).append(", ");
                }
                message.append("\n");
            }
        }

        JOptionPane.showMessageDialog(this, message.toString(), "최근 구매 내역", JOptionPane.INFORMATION_MESSAGE);
    }

    private static class DrinkSalesRecord {
        private String name;
        private int price;
        private int totalSales;

        public DrinkSalesRecord(String name, int price, int totalSales) {
            this.name = name;
            this.price = price;
            this.totalSales = totalSales;
        }

        public String getName() {
            return name;
        }

        public int getPrice() {
            return price;
        }

        public int getTotalSales() {
            return totalSales;
        }
    }

    public void changeDrinkName(String oldName, String newName) {
        MongoDBManager dbManager = MongoDBManager.getInstance();

        // vendingMachineGUI를 통해 vmNumber 가져오기
        String vmNumber = vendingMachineGUI.getVmNumber();

        // 1. 클라이언트 DB 변경
        dbManager.updateDrinkNameEverywhere(vmNumber, oldName, newName);

        // 2. 서버에 변경 요청 전송
        Map<String, String> changeData = new HashMap<>();
        changeData.put("type", "drinkRename");
        changeData.put("vmNumber", vmNumber);
        changeData.put("oldName", oldName);
        changeData.put("newName", newName);

        client.ClientSender.sendDataToServer(changeData);
    }


}
