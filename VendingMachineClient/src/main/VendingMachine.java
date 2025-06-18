package main;

import GUI.VendingMachineGUI;
import db.MongoDBManager;

import javax.swing.*;

public class VendingMachine {
    public static void main(String[] args) {
        try {
            String[] options = {"자판기1", "자판기2", "자판기3", "자판기4"};
            String selected = (String) JOptionPane.showInputDialog(
                    null,
                    "자판기를 선택하세요:",
                    "자판기 선택",
                    JOptionPane.QUESTION_MESSAGE,
                    null,
                    options,
                    options[0]);

            if (selected == null) {
                JOptionPane.showMessageDialog(null, "자판기가 선택되지 않았습니다. 프로그램을 종료합니다.");
                System.exit(0);
            }

            // VM 문자열로 명확히 고정
            String vmNumber = "VM" + selected.replaceAll("[^0-9]", "");

            MongoDBManager dbManager = MongoDBManager.getInstance();
            dbManager.insertInitialDrinks(vmNumber);

            new VendingMachineGUI(vmNumber);

        } catch (Exception e) {
            e.printStackTrace();
            JOptionPane.showMessageDialog(null, "초기 데이터 삽입 중 오류가 발생했습니다.", "오류", JOptionPane.ERROR_MESSAGE);
        }
    }
}
