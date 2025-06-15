package main;

import GUI.VendingMachineGUI;
import db.MongoDBManager;

import javax.swing.*;

public class VendingMachine {
    public static void main(String[] args) {

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

        int vmNumber = Integer.parseInt(selected.replaceAll("[^0-9]", ""));

        // ✅ 반드시 먼저 초기화 데이터 삽입
        MongoDBManager dbManager = MongoDBManager.getInstance();
        dbManager.insertInitialDrinks(vmNumber);  // ⭐ drinks 데이터가 없다면 기본 6개 삽입



        // ✅ 그 다음에 자판기 GUI 생성
        new VendingMachineGUI(vmNumber);
    }
}

