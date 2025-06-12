package GUI;

import model.Drink;
import model.Money;

import javax.swing.*;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.util.*;
import java.util.regex.Pattern;
import java.util.List;

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
            public void actionPerformed(ActionEvent e) {
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

            if (!oldPassword.equals(VendingMachineGUI.adminPassword)) {
                JOptionPane.showMessageDialog(this, "기존 비밀번호가 틀렸습니다.", "오류", JOptionPane.ERROR_MESSAGE);
            } else if (!isValidPassword(newPassword)) {
                JOptionPane.showMessageDialog(this, "새 비밀번호는 특수문자 및 숫자가 하나 이상 포함된 8자리 이상이어야 합니다.", "오류", JOptionPane.ERROR_MESSAGE);
            } else {
                VendingMachineGUI.adminPassword = newPassword;
                JOptionPane.showMessageDialog(this, "비밀번호가 성공적으로 변경되었습니다.", "성공", JOptionPane.INFORMATION_MESSAGE);
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
            Money money = vendingMachineGUI.getMoney(); //수금작업에 멀티스레드 사용

            int returned5000Won = Math.max(0, money.get5000WonCount() - 10);
            int returned1000Won = Math.max(0, money.get1000WonCount() - 10);
            int returned500Won = Math.max(0, money.get500WonCount() - 10);
            int returned100Won = Math.max(0, money.get100WonCount() - 10);
            int returned50Won = Math.max(0, money.get50WonCount() - 10);
            int returned10Won = Math.max(0, money.get10WonCount() - 10);

            money.use5000Won(returned5000Won);
            money.use1000Won(returned1000Won);
            money.use500Won(returned500Won);
            money.use100Won(returned100Won);
            money.use50Won(returned50Won);
            money.use10Won(returned10Won);

            int totalReturned = returned5000Won * 5000 + returned1000Won * 1000 + returned500Won * 500 + returned100Won * 100 + returned50Won * 50 + returned10Won * 10;

            String message = String.format("수금 완료:\n5000원: %d개\n1000원: %d개\n500원: %d개\n100원: %d개\n50원: %d개\n10원: %d개\n총 수금 금액: %d원",
                    returned5000Won, returned1000Won, returned500Won, returned100Won, returned50Won, returned10Won, totalReturned);

            SwingUtilities.invokeLater(() -> 
                JOptionPane.showMessageDialog(this, message, "수금 완료", JOptionPane.INFORMATION_MESSAGE)
            );
        }).start();
    }


    private void changeItemInfo() {
        String[] drinkNames = new String[vendingMachineGUI.drinks.length];
        for (int i = 0; i < vendingMachineGUI.drinks.length; i++) {
            drinkNames[i] = vendingMachineGUI.drinks[i].getName();
        }

        String selectedDrink = (String) JOptionPane.showInputDialog(this, "정보를 변경할 음료를 선택하세요.", "음료 정보 변경", JOptionPane.QUESTION_MESSAGE, null, drinkNames, drinkNames[0]);
        if (selectedDrink != null) {
            for (Drink drink : vendingMachineGUI.drinks) {
                if (drink.getName().equals(selectedDrink)) {
                    String newName = JOptionPane.showInputDialog(this, "새 음료 이름을 입력하세요.", drink.getName());
                    String newPriceStr = JOptionPane.showInputDialog(this, "새 음료 가격을 입력하세요.", drink.getPrice());
                    try {
                        int newPrice = Integer.parseInt(newPriceStr);
                        if (newPrice % 10 != 0) {
                            throw new NumberFormatException("가격은 10원 단위여야 합니다.");
                        }
                        drink.setName(newName);
                        drink.setPrice(newPrice);
                        JOptionPane.showMessageDialog(this, "음료 정보가 성공적으로 변경되었습니다.", "변경 완료", JOptionPane.INFORMATION_MESSAGE);
                        vendingMachineGUI.updateButtonColors();
                    } catch (NumberFormatException ex) {
                        JOptionPane.showMessageDialog(this, "유효한 가격을 입력하세요. 가격은 10원 단위여야 합니다.", "입력 오류", JOptionPane.ERROR_MESSAGE);
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
}
