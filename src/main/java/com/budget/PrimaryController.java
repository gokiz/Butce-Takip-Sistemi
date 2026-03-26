package com.budget;

import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.fxml.FXML;
import javafx.scene.control.*;
import java.sql.*;
import java.util.Locale;
import java.io.FileOutputStream;

// Excel için gerekli Apache POI importları
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.CellStyle;
import org.apache.poi.ss.usermodel.Font;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;

public class PrimaryController {

    @FXML private TextField titleField;
    @FXML private TextField amountField;
    @FXML private TextField filterField; 
    @FXML private Label totalLabel;
    @FXML private TableView<Expense> expenseTable;
    @FXML private TableColumn<Expense, Integer> colId;
    @FXML private TableColumn<Expense, String> colTitle;
    @FXML private TableColumn<Expense, Double> colAmount;
    @FXML private ComboBox<String> monthSelector;

    private final String URL = "jdbc:sqlite:budget.db";
    private ObservableList<Expense> expenseList = FXCollections.observableArrayList();
    private final Locale TR = new Locale("tr", "TR");

    @FXML
    public void initialize() {
        // Sütun yapılandırması
        colId.setCellValueFactory(cellData -> cellData.getValue().idProperty().asObject());
        colTitle.setCellValueFactory(cellData -> cellData.getValue().titleProperty());
        
        colAmount.setCellValueFactory(cellData -> cellData.getValue().amountProperty().asObject());
        colAmount.setCellFactory(column -> new TableCell<Expense, Double>() {
            @Override
            protected void updateItem(Double item, boolean empty) {
                super.updateItem(item, empty);
                if (item == null || empty) {
                    setText(null);
                } else {
                    setText(String.format(TR, "%,.2f TL", item));
                }
            }
        });

        // Tablodan seçim yapıldığında kutuları doldur
        expenseTable.getSelectionModel().selectedItemProperty().addListener((obs, oldSelection, newSelection) -> {
            if (newSelection != null) {
                titleField.setText(newSelection.titleProperty().get());
                amountField.setText(String.valueOf(newSelection.amountProperty().get()));
            }
        });

        // ComboBox İçeriği
        if(monthSelector != null){
            monthSelector.setItems(FXCollections.observableArrayList(
                "01 - January", "02 - February", "03 - March", "04 - April",
                "05 - May", "06 - June", "07 - July", "08 - August",
                "09 - September", "10 - October", "11 - November", "12 - December"
            ));
        }

        createTable();
        loadData();
    }

    private void createTable() {
        try (Connection conn = DriverManager.getConnection(URL);
             Statement stmt = conn.createStatement()) {
            // Tablo oluşturma ve eksik tarih sütununu otomatik tamamlama
            stmt.execute("CREATE TABLE IF NOT EXISTS expenses (id INTEGER PRIMARY KEY AUTOINCREMENT, title TEXT, amount REAL, date TEXT DEFAULT CURRENT_DATE)");
            // Eski kayıtlarda tarih boşsa bugünün tarihini ata (Filtrede görünmeleri için)
            stmt.execute("UPDATE expenses SET date = CURRENT_DATE WHERE date IS NULL OR date = ''");
        } catch (SQLException e) { 
            showError("Database Error", "Could not initialize database.");
        }
    }

    @FXML
    private void loadData() {
        expenseList.clear();
        double total = 0;
        try (Connection conn = DriverManager.getConnection(URL);
             ResultSet rs = conn.createStatement().executeQuery("SELECT * FROM expenses")) {
            while (rs.next()) {
                expenseList.add(new Expense(rs.getInt("id"), rs.getString("title"), rs.getDouble("amount")));
                total += rs.getDouble("amount");
            }
        } catch (SQLException e) { 
            showError("Load Error", "Failed to load data.");
        }
        
        expenseTable.setItems(expenseList);
        totalLabel.setText(String.format(TR, "Total: %,.2f TL", total));
        if(filterField != null) filterField.clear(); 
        if(monthSelector != null) monthSelector.setValue(null);
    }

    @FXML
    private void addExpense() {
        String title = titleField.getText();
        String amountStr = amountField.getText();

        if (title.isEmpty() || amountStr.isEmpty()) {
            showWarning("Input Error", "Please fill in all fields.");
            return;
        }

        try {
            double amount = Double.parseDouble(amountStr);
            try (Connection conn = DriverManager.getConnection(URL);
                 PreparedStatement pstmt = conn.prepareStatement("INSERT INTO expenses(title, amount, date) VALUES(?, ?, CURRENT_DATE)")) {
                pstmt.setString(1, title);
                pstmt.setDouble(2, amount);
                pstmt.executeUpdate();
                
                titleField.clear();
                amountField.clear();
                loadData();
            }
        } catch (Exception e) { 
            showError("Add Error", "Invalid amount or database error.");
        }
    }

    @FXML
    private void updateExpense() {
        Expense selected = expenseTable.getSelectionModel().getSelectedItem();
        if (selected == null) {
            showWarning("Selection Error", "Please select an item to update.");
            return;
        }

        try (Connection conn = DriverManager.getConnection(URL);
             PreparedStatement pstmt = conn.prepareStatement("UPDATE expenses SET title = ?, amount = ? WHERE id = ?")) {
            pstmt.setString(1, titleField.getText());
            pstmt.setDouble(2, Double.parseDouble(amountField.getText()));
            pstmt.setInt(3, selected.idProperty().get());
            pstmt.executeUpdate();
            loadData();
        } catch (Exception e) {
            showError("Update Error", "Check your inputs.");
        }
    }

    @FXML
    private void deleteExpense() {
        Expense selected = expenseTable.getSelectionModel().getSelectedItem();
        if (selected == null) return;

        try (Connection conn = DriverManager.getConnection(URL);
             PreparedStatement pstmt = conn.prepareStatement("DELETE FROM expenses WHERE id = ?")) {
            pstmt.setInt(1, selected.idProperty().get());
            pstmt.executeUpdate();
            loadData();
        } catch (SQLException e) { 
            showError("Delete Error", "Could not delete.");
        }
    }

    @FXML
    private void filterExpenses() {
        String limitStr = filterField.getText();
        if (limitStr.isEmpty()) return;

        try {
            double limit = Double.parseDouble(limitStr);
            expenseList.clear();
            double total = 0;
            try (Connection conn = DriverManager.getConnection(URL);
                 PreparedStatement pstmt = conn.prepareStatement("SELECT * FROM expenses WHERE amount > ?")) {
                pstmt.setDouble(1, limit);
                ResultSet rs = pstmt.executeQuery();
                while (rs.next()) {
                    expenseList.add(new Expense(rs.getInt("id"), rs.getString("title"), rs.getDouble("amount")));
                    total += rs.getDouble("amount");
                }
                expenseTable.setItems(expenseList);
                totalLabel.setText(String.format(TR, "Total (Above " + limit + " TL): %,.2f TL", total));
            }
        } catch (Exception e) {
            showWarning("Filter Error", "Enter a valid number.");
        }
    }

    @FXML
    private void showMonthlyExpenses(){
        String selectedMonth = monthSelector.getValue();
        if(selectedMonth == null){
            showWarning("Selection Error", "Please select a month");
            return;
        }
        
        String monthNum = selectedMonth.substring(0, 2);
        expenseList.clear();
        double total = 0;

        // strftime('%m', date) SQLite'da tarihin ay kısmını (01, 02 vb.) çeker
        String sql ="SELECT * FROM expenses WHERE strftime('%m', date) = ?";

        try(Connection conn = DriverManager.getConnection(URL);
            PreparedStatement pstmt = conn.prepareStatement(sql)) {
                pstmt.setString(1, monthNum);
                ResultSet rs = pstmt.executeQuery();
                while(rs.next()){
                    expenseList.add(new Expense(rs.getInt("id"), rs.getString("title"), rs.getDouble("amount")));
                    total += rs.getDouble("amount");
                }
                expenseTable.setItems(expenseList);
                totalLabel.setText(String.format(TR, "Total for " + selectedMonth + ": %,.2f TL", total));
            } catch(SQLException e){
                showError("Filter Error", "Could not filter monthly data.");
                e.printStackTrace();
            }
    }

    @FXML
    private void exportToExcel() {
        try (Workbook workbook = new XSSFWorkbook()) {
            Sheet sheet = workbook.createSheet("Expenses");
            Row headerRow = sheet.createRow(0);
            String[] columns = {"ID", "Title", "Amount (TL)"};

            CellStyle headerStyle = workbook.createCellStyle();
            Font font = workbook.createFont();
            font.setBold(true);
            headerStyle.setFont(font);

            for (int i = 0; i < columns.length; i++) {
                org.apache.poi.ss.usermodel.Cell cell = headerRow.createCell(i);
                cell.setCellValue(columns[i]);
                cell.setCellStyle(headerStyle);
            }

            int rowNum = 1;
            for (Expense expense : expenseList) {
                Row row = sheet.createRow(rowNum++);
                row.createCell(0).setCellValue(expense.idProperty().get());
                row.createCell(1).setCellValue(expense.titleProperty().get());
                row.createCell(2).setCellValue(expense.amountProperty().get());
            }

            for (int i = 0; i < columns.length; i++) {
                sheet.autoSizeColumn(i);
            }

            try (FileOutputStream fileOut = new FileOutputStream("Budget_Report.xlsx")) {
                workbook.write(fileOut);
                showInfo("Success", "Excel report saved as 'Budget_Report.xlsx'");
            }
        } catch (Exception e) {
            showError("Export Error", "Make sure the file is not open in Excel.");
            e.printStackTrace();
        }
    }

    private void showWarning(String title, String content) {
        Alert alert = new Alert(Alert.AlertType.WARNING);
        alert.setTitle(title);
        alert.setHeaderText(null);
        alert.setContentText(content);
        alert.showAndWait();
    }

    private void showError(String title, String content) {
        Alert alert = new Alert(Alert.AlertType.ERROR);
        alert.setTitle(title);
        alert.setHeaderText(null);
        alert.setContentText(content);
        alert.showAndWait();
    }

    private void showInfo(String title, String content) {
        Alert alert = new Alert(Alert.AlertType.INFORMATION);
        alert.setTitle(title);
        alert.setHeaderText(null);
        alert.setContentText(content);
        alert.showAndWait();
    }
}