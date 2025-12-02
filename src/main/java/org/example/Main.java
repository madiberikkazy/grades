package org.example;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class Main {

    private static final String DATASET_CSV = "dataset.csv";
    private static final String INPUT_CSV = "grades.csv";
    private static final String OUTPUT_CSV = "grades_filled.csv";

    // Weights provided in requirements
    private static final double WEIGHT_LECTURE = 0.2;
    private static final double WEIGHT_PRACTICE = 0.3;
    private static final double WEIGHT_SRSP = 0.5;

    // --- Helper Methods ---

    /**
     * Parse a grade string.
     * "н" -> 0.0
     * "н.п." -> NaN (Excluded from calc)
     * "" or null -> NaN (Missing, needs filling)
     * "85" -> 85.0
     */
    private static double parseGrade(String gradeStr) {
        if (gradeStr == null) return Double.NaN;
        String s = gradeStr.trim();
        if (s.isEmpty()) return Double.NaN;
        if (s.equalsIgnoreCase("н")) return 0.0;
        if (s.equalsIgnoreCase("н.п.")) return Double.NaN;
        try {
            return Double.parseDouble(s);
        } catch (NumberFormatException e) {
            return Double.NaN;
        }
    }

    /**
     * Calculates average of a list of grades.
     * Logic: Sum / (Total Count - Count of NaN)
     * NaN (н.п.) is effectively skipped.
     */
    private static double calculateComponentAverage(List<Double> grades) {
        double sum = 0;
        int count = 0;
        for (Double g : grades) {
            if (!Double.isNaN(g)) {
                sum += g;
                count++;
            }
        }
        if (count == 0) return 0.0;
        return sum / count;
    }

    // --- Step 1: Analyze Dataset for Averages ---

    /**
     * Reads dataset.csv and maps column names (e.g., "Л1", "П5", "РК1") to their average values.
     */
    private static Map<String, Double> getDatasetAverages() throws IOException {
        Map<String, List<Double>> tempStorage = new HashMap<>();
        List<String> headers = new ArrayList<>();

        try (BufferedReader br = new BufferedReader(
                new InputStreamReader(new FileInputStream(DATASET_CSV), StandardCharsets.UTF_8))) {
            
            String line = br.readLine();
            if (line == null) return new HashMap<>();
            
            String[] headerParts = line.split(",");
            for (String h : headerParts) headers.add(h.trim());

            while ((line = br.readLine()) != null) {
                String[] parts = line.split(",", -1);
                for (int i = 1; i < parts.length; i++) { // Skip Name (index 0)
                    if (i < headers.size()) {
                        double val = parseGrade(parts[i]);
                        // Only add real numbers or 0 (from 'н') to average calculation. Skip NaN.
                        if (!Double.isNaN(val)) {
                            String colName = headers.get(i);
                            tempStorage.computeIfAbsent(colName, k -> new ArrayList<>()).add(val);
                        }
                    }
                }
            }
        }

        Map<String, Double> averages = new HashMap<>();
        for (Map.Entry<String, List<Double>> entry : tempStorage.entrySet()) {
            double avg = entry.getValue().stream().mapToDouble(d -> d).average().orElse(70.0);
            averages.put(entry.getKey(), avg);
        }
        
        // Add default fallback for Exam (Итоговый контроль) if not in dataset, usually random 50-100 or avg
        averages.putIfAbsent("Итог", 85.0); 

        return averages;
    }

    // --- Step 2: Process Input, Fill Gaps, Calculate ---

    public static void main(String[] args) {
        try {
            System.out.println("🔍 Loading reference dataset...");
            Map<String, Double> datasetAverages = getDatasetAverages();

            System.out.println("📖 Reading input " + INPUT_CSV + "...");
            List<String[]> rows = new ArrayList<>();
            String headerLine;

            try (BufferedReader br = new BufferedReader(
                    new InputStreamReader(new FileInputStream(INPUT_CSV), StandardCharsets.UTF_8))) {
                headerLine = br.readLine();
                String line;
                while ((line = br.readLine()) != null) {
                    rows.add(line.split(",", -1));
                }
            }

            // Identify rows
            String[] lectureRow = null, practiceRow = null, srspRow = null;
            for (String[] row : rows) {
                if (row.length > 0) {
                    if (row[0].equalsIgnoreCase("Лекция")) lectureRow = row;
                    if (row[0].equalsIgnoreCase("Практика")) practiceRow = row;
                    if (row[0].equalsIgnoreCase("СРСП")) srspRow = row;
                }
            }

            if (lectureRow == null || practiceRow == null || srspRow == null) {
                System.err.println("❌ Error: Input file must contain rows named 'Лекция', 'Практика', 'СРСП'.");
                return;
            }

            // --- FILL MISSING VALUES ---
            // Mapping input columns to dataset keys
            // Weeks 1-7 are at indices 1-7
            fillRowRange(lectureRow, 1, 7, "Л", datasetAverages);
            fillRowRange(practiceRow, 1, 7, "П", datasetAverages);
            fillRowRange(srspRow, 1, 7, "С", datasetAverages);

            // Weeks 8-15 are at indices 12-19 in the input file (columns named 8,9,10,11,12,13,14,15)
            // dataset keys are Л8..Л15
            fillRowRangeOffset(lectureRow, 12, 19, "Л", 8, datasetAverages);
            fillRowRangeOffset(practiceRow, 12, 19, "П", 8, datasetAverages);
            fillRowRangeOffset(srspRow, 12, 19, "С", 8, datasetAverages);

            // Fill RK1 (Index 10) and RK2 (Index 22) and Final Exam (Index 25)
            // Using Lecture row to store the exams usually, or fill all rows for visual consistency? 
            // Usually exams are written in one row or all. I will check Lecture row.
            fillSingleCell(lectureRow, 10, "РК1", datasetAverages);
            fillSingleCell(lectureRow, 22, "РК2", datasetAverages);
            // Final exam might not be in dataset averages under "Итоговый контроль", so we use "Итог" or "РК2" as fallback
            fillSingleCell(lectureRow, 25, "Итог", datasetAverages);

            // --- CALCULATIONS ---

            // 1. Calculate TK1 (Weeks 1-7)
            double tk1_Lec = getRangeAverage(lectureRow, 1, 7);
            double tk1_Prac = getRangeAverage(practiceRow, 1, 7);
            double tk1_Srsp = getRangeAverage(srspRow, 1, 7);
            
            // Weighted TK1 Total
            double tk1_Total = (tk1_Lec * WEIGHT_LECTURE) + (tk1_Prac * WEIGHT_PRACTICE) + (tk1_Srsp * WEIGHT_SRSP);
            
            // RK1
            double rk1 = parseGrade(lectureRow[10]); 
            
            // R1 (Midterm 1)
            double r1 = (tk1_Total + rk1) / 2.0;

            // 2. Calculate TK2 (Weeks 8-15 -> indices 12-19)
            double tk2_Lec = getRangeAverage(lectureRow, 12, 19);
            double tk2_Prac = getRangeAverage(practiceRow, 12, 19);
            double tk2_Srsp = getRangeAverage(srspRow, 12, 19);

            // Weighted TK2 Total
            double tk2_Total = (tk2_Lec * WEIGHT_LECTURE) + (tk2_Prac * WEIGHT_PRACTICE) + (tk2_Srsp * WEIGHT_SRSP);

            // RK2
            double rk2 = parseGrade(lectureRow[22]);

            // R2 (Midterm 2)
            double r2 = (tk2_Total + rk2) / 2.0;

            // 3. Finals
            double admissionRating = (r1 + r2) / 2.0;
            double finalExam = parseGrade(lectureRow[25]);
            double finalGradePercent = (admissionRating * 0.6) + (finalExam * 0.4);

            // --- UPDATE DATA STRUCTURE FOR OUTPUT ---
            
            // Update Lecture Row with calculated totals
            updateCell(lectureRow, 8, tk1_Lec);
            updateCell(lectureRow, 9, tk1_Total); // Shared column often put in top row
            updateCell(lectureRow, 11, r1);
            
            updateCell(lectureRow, 20, tk2_Lec);
            updateCell(lectureRow, 21, tk2_Total);
            updateCell(lectureRow, 23, r2);
            
            updateCell(lectureRow, 24, admissionRating);
            updateCell(lectureRow, 26, finalGradePercent);
            lectureRow[27] = getLetterGrade(finalGradePercent);

            // Update Practice/SRSP rows with their specific averages for clarity
            updateCell(practiceRow, 8, tk1_Prac);
            updateCell(practiceRow, 20, tk2_Prac);
            
            updateCell(srspRow, 8, tk1_Srsp);
            updateCell(srspRow, 20, tk2_Srsp);

            // --- OUTPUT TO FILE ---
            try (BufferedWriter bw = new BufferedWriter(
                    new OutputStreamWriter(new FileOutputStream(OUTPUT_CSV), StandardCharsets.UTF_8))) {
                bw.write(headerLine);
                bw.newLine();
                for (String[] row : rows) {
                    bw.write(String.join(",", row));
                    bw.newLine();
                }
            }

            // --- CONSOLE OUTPUT ---
            System.out.println("✅ Calculation Complete!");
            System.out.println("════════════════ RESULTS ════════════════");
            System.out.printf("TK1 Weighted: %.2f (Lec:%.0f, Prac:%.0f, Srsp:%.0f)%n", tk1_Total, tk1_Lec, tk1_Prac, tk1_Srsp);
            System.out.printf("R1 (Midterm 1): %.2f (RK1: %.0f)%n", r1, rk1);
            System.out.println("-----------------------------------------");
            System.out.printf("TK2 Weighted: %.2f (Lec:%.0f, Prac:%.0f, Srsp:%.0f)%n", tk2_Total, tk2_Lec, tk2_Prac, tk2_Srsp);
            System.out.printf("R2 (Midterm 2): %.2f (RK2: %.0f)%n", r2, rk2);
            System.out.println("-----------------------------------------");
            System.out.printf("Admission Rating: %.2f%n", admissionRating);
            System.out.printf("Final Exam:       %.2f%n", finalExam);
            System.out.printf("FINAL GRADE:      %.2f (%s)%n", finalGradePercent, lectureRow[27]);
            System.out.println("═════════════════════════════════════════");
            System.out.println("📂 File saved: " + OUTPUT_CSV);

        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    // --- Helpers for filling and updating ---

    // Fills a range of columns (e.g., 1-7) using dataset keys (e.g., Л1-Л7)
    private static void fillRowRange(String[] row, int startIdx, int endIdx, String prefix, Map<String, Double> avgs) {
        for (int i = startIdx; i <= endIdx; i++) {
            String val = row[i].trim();
            if (val.isEmpty()) {
                double avg = avgs.getOrDefault(prefix + i, 75.0); // Default if dataset missing col
                row[i] = String.format("%.0f", avg);
            }
        }
    }

    // Fills range with offset (e.g., Input index 12 -> Dataset key prefix + 8)
    private static void fillRowRangeOffset(String[] row, int startIdx, int endIdx, String prefix, int keyStart, Map<String, Double> avgs) {
        int keyCounter = keyStart;
        for (int i = startIdx; i <= endIdx; i++) {
            String val = row[i].trim();
            if (val.isEmpty()) {
                double avg = avgs.getOrDefault(prefix + keyCounter, 75.0);
                row[i] = String.format("%.0f", avg);
            }
            keyCounter++;
        }
    }

    private static void fillSingleCell(String[] row, int index, String key, Map<String, Double> avgs) {
        if (index >= row.length) return;
        String val = row[index].trim();
        if (val.isEmpty()) {
            double avg = avgs.getOrDefault(key, 80.0);
            row[index] = String.format("%.0f", avg);
        }
    }

    private static double getRangeAverage(String[] row, int start, int end) {
        List<Double> values = new ArrayList<>();
        for (int i = start; i <= end; i++) {
            values.add(parseGrade(row[i]));
        }
        return calculateComponentAverage(values);
    }

    private static void updateCell(String[] row, int index, double value) {
        if (index < row.length) {
            row[index] = String.format("%.0f", value);
        }
    }

    private static String getLetterGrade(double grade) {
        if (grade >= 95) return "A";
        if (grade >= 90) return "A-";
        if (grade >= 85) return "B+";
        if (grade >= 80) return "B";
        if (grade >= 75) return "B-";
        if (grade >= 70) return "C+";
        if (grade >= 65) return "C";
        if (grade >= 60) return "C-";
        if (grade >= 55) return "D+";
        if (grade >= 50) return "D";
        return "F";
    }
}