package GUI;

import model.Drink;
import model.Money;
import org.bson.Document;

import javax.swing.*;
import javax.swing.border.Border;
import javax.swing.border.MatteBorder;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.time.LocalDate;
import java.util.Map;
import db.MongoDBManager;
import util.EncryptionUtil;

import java.util.HashMap;
import java.util.Map;

public class VendingMachineGUI extends JFrame {
    private int vmNumber; // 자판기 번호 저장
    public static String adminPassword = "1234"; // 초기 관리자 비밀번호
    public static String encryptedAdminPassword = "";


    public int getVmNumber() {
        return this.vmNumber;
    }


    private JLabel currentAmountLabel;
    private int currentAmount;
    private static final int MAX_AMOUNT = 7000; // 최대 금액 설정

    JButton[] drinkButtons;
    Drink[] drinks;
    private JLabel blackLabel; // black 이미지를 표시하는 JLabel

    private Money money;
    private boolean adminMode = false; // 관리자 모드 상태

    public VendingMachineGUI(int vmNumber) {
        this.vmNumber = vmNumber;


        MongoDBManager dbManager = MongoDBManager.getInstance();
        dbManager.initializeAdminPasswordIfAbsent(vmNumber);

        try {
            String encryptedPw = dbManager.getAdminPassword(vmNumber); // MongoDB에서 가져오기

            if (encryptedPw != null) {
                VendingMachineGUI.adminPassword = EncryptionUtil.decrypt(encryptedPw);
            } else {
                System.out.println("❗ 관리자 비밀번호가 DB에 없습니다.");
                VendingMachineGUI.adminPassword = "1234"; // fallback 기본값
            }
        } catch (Exception e) {
            e.printStackTrace();
        }


        reloadDrinksFromDB();
        if (!dbManager.hasChangeData(vmNumber)) {
            Map<String, Integer> initialChange = new HashMap<>();
            initialChange.put("5000", 10);
            initialChange.put("1000", 10);
            initialChange.put("500", 10);
            initialChange.put("100", 10);
            initialChange.put("50", 10);
            initialChange.put("10", 10);

            dbManager.updateChangeStorage(vmNumber, new Document(initialChange));
        }
        setTitle("음료 자판기 " + vmNumber);
        setSize(700, 800);
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        currentAmount = dbManager.getCurrentAmount(vmNumber);


        Container c = getContentPane();

        // 자판기 돈 초기화
        money = new Money();

        // 자판기 메인 패널
        JPanel p1 = createMainPanel();
        c.add(p1, BorderLayout.WEST);

        // 중앙 패널
        JPanel p2 = createCentralPanel();
        c.add(p2, BorderLayout.CENTER);

        setVisible(true);
        updateButtonColors();
    }

    public Money getMoney() {
        return money;
    }



    private JPanel createMainPanel() {
        JPanel p1 = new JPanel();
        GridLayout gridLayout = new GridLayout(3, 2); // 이미지를 3x2로 배치
        gridLayout.setVgap(20); // 세로 간격 설정
        gridLayout.setHgap(20); // 가로 간격 설정
        p1.setLayout(gridLayout);
        p1.setBackground(Color.black);
        p1.setPreferredSize(new Dimension(400, 800)); // 높이를 800으로 설정
        Border border1 = new MatteBorder(15, 15, 15, 15, Color.BLACK); // p1의 테두리 설정
        p1.setBorder(border1);

        drinkButtons = new JButton[6];

        // 이미지를 p1 패널에 추가
        addDrinkToPanel(p1, "/image/0.jpg", drinks[0], 0);
        addDrinkToPanel(p1, "/image/1.jpg", drinks[1], 1);
        addDrinkToPanel(p1, "/image/2.jpg", drinks[2], 2);
        addDrinkToPanel(p1, "/image/3.jpg", drinks[3], 3);
        addDrinkToPanel(p1, "/image/4.jpg", drinks[4], 4);
        addDrinkToPanel(p1, "/image/5.jpg", drinks[5], 5);

        return p1;
    }

    private void addDrinkToPanel(JPanel parentPanel, String imagePath, Drink drink, int index) {
        ImageIcon image = new ImageIcon(getClass().getResource(imagePath));
        JLabel label = new JLabel(image);
        JButton button = new JButton(drink.getName() + " (" + drink.getPrice() + ")");
        button.setPreferredSize(new Dimension(50, 30));
        drinkButtons[index] = button;
        button.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                if (!adminMode) {
                    purchaseDrink(index);
                } else {
                    JOptionPane.showMessageDialog(null, "관리자 모드에서는 음료를 구매할 수 없습니다.", "경고", JOptionPane.WARNING_MESSAGE);
                }
            }
        });
        addComponentWithButton(parentPanel, label, button);
    }

    private JPanel createCentralPanel() {
        JPanel p2 = new JPanel();
        p2.setBackground(Color.gray);
        p2.setPreferredSize(new Dimension(700, 800));
        Border border2 = new MatteBorder(15, 0, 15, 15, Color.BLACK); // p2의 테두리 설정
        p2.setBorder(border2);
        p2.setLayout(new BorderLayout()); // p2 패널의 레이아웃을 BorderLayout으로 설정

        // 투입 금액, 현재 금액 및 반환 버튼, 관리자 모드 패널을 포함할 중첩 패널
        JPanel nestedPanel = new JPanel();
        nestedPanel.setLayout(new BoxLayout(nestedPanel, BoxLayout.Y_AXIS));
        nestedPanel.setBackground(Color.gray);

        // 투입 금액 레이블과 버튼들 추가
        JPanel p4 = createMoneyInputPanel();
        nestedPanel.add(p4);

        // 현재 금액 및 반환 버튼 패널 추가
        JPanel p5 = createCurrentAmountPanel();
        nestedPanel.add(p5);

        // 관리자 모드 패널 추가
        JPanel adminPanel = createAdminPanel();
        nestedPanel.add(adminPanel);

        p2.add(nestedPanel, BorderLayout.CENTER);

        // 중앙 패널에 black 이미지 추가
        blackLabel = new JLabel(new ImageIcon(getClass().getResource("/image/black.jpg")));
        blackLabel.addMouseListener(new java.awt.event.MouseAdapter() {
            @Override
            public void mouseClicked(java.awt.event.MouseEvent evt) {
                if (!adminMode) {
                    toggleBlackImage();
                }
            }
        });
        p2.add(blackLabel, BorderLayout.SOUTH); // 이미지를 맨 아래에 추가

        return p2;
    }

    private void toggleBlackImage() {
        if (blackLabel.getIcon().toString().contains("black.jpg")) {
            blackLabel.setIcon(new ImageIcon(getClass().getResource("/image/0_0.png")));
        } else {
            blackLabel.setIcon(new ImageIcon(getClass().getResource("/image/black.jpg")));
        }
    }

    private JPanel createMoneyInputPanel() {
        JPanel p4 = new JPanel();
        p4.setBackground(Color.gray); // p4 배경색 설정
        p4.setLayout(new GridLayout(3, 3, 10, 10)); // 3x3 그리드로 설정하고 버튼 사이 간격 설정
        JLabel l7 = new JLabel("투입 할 금액 : ");

        int[] denominations = {10, 50, 100, 500, 1000, 5000};
        Dimension buttonSize = new Dimension(60, 20);

        p4.add(l7);

        for (int denomination : denominations) {
            JButton moneyButton = new JButton(denomination + "원");
            moneyButton.setPreferredSize(buttonSize);
            moneyButton.addActionListener(new ActionListener() {
                @Override
                public void actionPerformed(ActionEvent e) {
                    if (!adminMode) {
                        if (currentAmount + denomination <= MAX_AMOUNT) {
                            currentAmount += denomination;
                            currentAmountLabel.setText("현재 금액: " + currentAmount + "원");
                            MongoDBManager.getInstance().updateCurrentAmount(vmNumber, currentAmount);
                            updateButtonColors(); // 금액 변경 후 버튼 색상 업데이트

                            // 각 화폐 단위의 개수를 증가시킴
                            switch (denomination) {
                                case 10:
                                    money.add10Won(1);
                                    MongoDBManager.getInstance().incrementStoredMoney(vmNumber, 10, 1);
                                    break;
                                case 50:
                                    money.add50Won(1);
                                    MongoDBManager.getInstance().incrementStoredMoney(vmNumber, 50, 1);
                                    break;
                                case 100:
                                    money.add100Won(1);
                                    MongoDBManager.getInstance().incrementStoredMoney(vmNumber, 100, 1);
                                    break;
                                case 500:
                                    money.add500Won(1);
                                    MongoDBManager.getInstance().incrementStoredMoney(vmNumber, 500, 1);
                                    break;
                                case 1000:
                                    money.add1000Won(1);
                                    MongoDBManager.getInstance().incrementStoredMoney(vmNumber, 1000, 1);
                                    break;
                                case 5000:
                                    money.add5000Won(1);
                                    MongoDBManager.getInstance().incrementStoredMoney(vmNumber, 5000, 1);
                                    break;
                            }

                        } else {
                            JOptionPane.showMessageDialog(null, "최대 투입 금액은 7000원입니다.", "오류", JOptionPane.ERROR_MESSAGE);
                        }
                    } else {
                        JOptionPane.showMessageDialog(null, "관리자 모드에서는 금액을 투입할 수 없습니다.", "경고", JOptionPane.WARNING_MESSAGE);
                    }
                }
            });
            p4.add(moneyButton);
        }

        return p4;
    }

    private JPanel createCurrentAmountPanel() {
        JPanel p5 = new JPanel();
        p5.setBackground(Color.gray); // p5 배경색 설정
        p5.setLayout(new FlowLayout(FlowLayout.LEFT));
        currentAmountLabel = new JLabel("현재 금액: " + currentAmount + "원");
        JButton returnButton = new JButton("반환");
        returnButton.setPreferredSize(new Dimension(80, 30));
        returnButton.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                if (!adminMode) {
                    if (returnMoney(currentAmount)) {
                        currentAmount = 0;
                        currentAmountLabel.setText("현재 금액: " + currentAmount + "원");
                        MongoDBManager.getInstance().updateCurrentAmount(vmNumber, currentAmount);
                        updateButtonColors(); // 금액 반환 후 버튼 색상 업데이트
                    } else {
                        JOptionPane.showMessageDialog(null, "충분한 거스름돈이 없습니다.", "오류", JOptionPane.ERROR_MESSAGE);
                    }
                } else {
                    JOptionPane.showMessageDialog(null, "관리자 모드에서는 금액을 반환할 수 없습니다.", "경고", JOptionPane.WARNING_MESSAGE);
                }
            }
        });

        p5.add(currentAmountLabel);
        p5.add(returnButton);
        return p5;
    }

    private JPanel createAdminPanel() {
        JPanel adminPanel = new JPanel();
        adminPanel.setBackground(Color.gray); // adminPanel 배경색 설정
        adminPanel.setLayout(new FlowLayout());
        JLabel passwordLabel = new JLabel("관리자 비밀번호: ");
        JPasswordField passwordField = new JPasswordField(10);
        JButton adminButton = new JButton("접속");

        adminButton.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                String password = new String(passwordField.getPassword());
                if (adminPassword.equals(password)) {
                    JOptionPane.showMessageDialog(null, "관리자 모드에 접속했습니다.");
                    adminMode = true;
                    new AdminGUI(VendingMachineGUI.this); // AdminGUI에 VendingMachineGUI를 전달
                } else {
                    JOptionPane.showMessageDialog(null, "비밀번호가 틀렸습니다.", "오류", JOptionPane.ERROR_MESSAGE);
                }
            }
        });

        adminPanel.add(passwordLabel);
        adminPanel.add(passwordField);
        adminPanel.add(adminButton);
        return adminPanel;
    }

    public void exitAdminMode() {
        adminMode = false;
    }

    private void addComponentWithButton(JPanel parentPanel, JLabel label, JButton button) {
        JPanel panel = new JPanel();
        panel.setLayout(new BorderLayout());
        panel.add(label, BorderLayout.CENTER);
        panel.add(button, BorderLayout.SOUTH);
        parentPanel.add(panel);
    }

    private void purchaseDrink(int index) {
        Drink drink = drinks[index];
        if (currentAmount >= drink.getPrice()) {
            if (drink.getStock() > 0) {
                currentAmount -= drink.getPrice();
                currentAmountLabel.setText("현재 금액: " + currentAmount + "원");
                MongoDBManager.getInstance().updateCurrentAmount(vmNumber, currentAmount);

                JOptionPane.showMessageDialog(null, "음료를 가져가주세요.", "구매 완료", JOptionPane.INFORMATION_MESSAGE);

                // 매출 기록
                recordSale(drink);

                // 재고 감소
                drink.reduceStock();
                if (drink.isSoldOut()) {
                    drinkButtons[index].setText("품절");
                    drinkButtons[index].setEnabled(false);
                }
                MongoDBManager dbManager = MongoDBManager.getInstance();
                dbManager.upsertInventory(vmNumber, drink.getName(), drink.getPrice(), drink.getStock(), LocalDate.now());

                // 구매 기록 스택에 추가
                drink.addPurchase(drink.getName());

                // 구매한 음료에 따라 배출구에 음료가 나오게 이미지를 변경하고 음료를 클릭시 이미지가 돌아오게
                String imagePath = null;
                switch (index) {
                    case 0:
                        imagePath = "/image/0_0.png";
                        break;
                    case 1:
                        imagePath = "/image/1_1.png";
                        break;
                    case 2:
                        imagePath = "/image/2_2.png";
                        break;
                    case 3:
                        imagePath = "/image/3_3.png";
                        break;
                    case 4:
                        imagePath = "/image/4_4.png";
                        break;
                    case 5:
                        imagePath = "/image/5_5.png";
                        break;
                    default:
                        imagePath = "/image/black.jpg";
                        break;
                }

                ImageIcon imageIcon = new ImageIcon(getClass().getResource(imagePath));
                if (imageIcon.getImageLoadStatus() != MediaTracker.ERRORED) {
                    blackLabel.setIcon(imageIcon);
                } else {
                    System.err.println("Could not find image: " + imagePath);
                }
            } else {
                JOptionPane.showMessageDialog(null, "해당 음료는 품절입니다.", "구매 실패", JOptionPane.ERROR_MESSAGE);
            }
        } else {
            JOptionPane.showMessageDialog(null, "금액이 부족합니다.", "구매 실패", JOptionPane.ERROR_MESSAGE);
        }
        updateButtonColors();
    }

    private void recordSale(Drink drink) {
        LocalDate now = LocalDate.now();
        int year = now.getYear();
        int month = now.getMonthValue();
        int day = now.getDayOfMonth();

        // 로컬에 기록
        drink.addSale(year, month, day);

        // DB에 기록
        MongoDBManager dbManager = MongoDBManager.getInstance();
        dbManager.insertSale(vmNumber, drink.getName(), drink.getPrice(), now.toString());
    }


    public void updateButtonColors() {
        for (int i = 0; i < drinkButtons.length; i++) {
            Drink drink = drinks[i];
            if (drink.getStock() > 0) {
                drinkButtons[i].setText(drink.getName() + " (" + drink.getPrice() + ")");
                if (currentAmount >= drink.getPrice()) {
                    drinkButtons[i].setBackground(Color.blue);
                } else {
                    drinkButtons[i].setBackground(Color.red);
                }
                drinkButtons[i].setEnabled(true);
            } else {
                drinkButtons[i].setText("품절");
                drinkButtons[i].setBackground(Color.red);
                drinkButtons[i].setEnabled(false);
            }
        }
    }

    private boolean returnMoney(int amount) {
        MongoDBManager dbManager = MongoDBManager.getInstance();
        int vmNumber = getVmNumber();

        Document changeDoc = dbManager.getChangeState(vmNumber);
        if (changeDoc == null) {
            JOptionPane.showMessageDialog(null, "거스름돈 정보가 없습니다.", "오류", JOptionPane.ERROR_MESSAGE);
            return false;
        }

        Map<String, Integer> changeMap = new HashMap<>();
        for (String key : changeDoc.keySet()) {
            if (key.equals("_id") || key.equals("vmNumber")) continue;
            Object value = changeDoc.get(key);
            if (value instanceof Integer) {
                changeMap.put(key, (Integer) value);
            }
        }

        int[] denominations = {5000, 1000, 500, 100, 50, 10};
        Map<String, Integer> usedChange = new HashMap<>();

        int remaining = amount;

        for (int denom : denominations) {
            String key = String.valueOf(denom);
            int available = changeMap.getOrDefault(key, 0);
            int count = Math.min(remaining / denom, available);

            if (count > 0) {
                usedChange.put(key, count);
                remaining -= count * denom;
            }
        }

        if (remaining > 0) {
            JOptionPane.showMessageDialog(null, "충분한 거스름돈이 없습니다.", "오류", JOptionPane.ERROR_MESSAGE);
            return false;
        }

        for (Map.Entry<String, Integer> entry : usedChange.entrySet()) {
            String denom = entry.getKey();
            int prevCount = changeMap.getOrDefault(denom, 0);
            changeMap.put(denom, prevCount - entry.getValue());
        }

        Document updateDoc = new Document();
        for (Map.Entry<String, Integer> entry : changeMap.entrySet()) {
            updateDoc.append(entry.getKey(), entry.getValue());
        }
        dbManager.updateChangeState(vmNumber, updateDoc);

        return true;
    }




    public void reloadDrinksFromDB() {
        MongoDBManager dbManager = MongoDBManager.getInstance();
        java.util.List<org.bson.Document> drinkDocs = dbManager.getDrinksByVMNumber(vmNumber);

        if (drinkDocs == null || drinkDocs.isEmpty()) {
            JOptionPane.showMessageDialog(this, "음료 정보를 불러오지 못했습니다.", "오류", JOptionPane.ERROR_MESSAGE);
            return;
        }

        drinks = new Drink[drinkDocs.size()];
        for (int i = 0; i < drinkDocs.size(); i++) {
            org.bson.Document doc = drinkDocs.get(i);
            String name = doc.getString("name");
            int price = doc.getInteger("defaultPrice");

            // ⭐ inventory에서 실제 재고 불러오기
            Document inventoryDoc = dbManager.getInventory(vmNumber, name);
            int stock = (inventoryDoc != null) ? inventoryDoc.getInteger("stock", 10) : 10;

            Drink drink = new Drink(name, price);
            drink.setStock(stock);
            drinks[i] = drink;
        }
    }





}
