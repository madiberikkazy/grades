package org.example;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.stream.Collectors;

public class Main {

    private static final int WEEKS_TK1 = 7;
    private static final int WEEKS_TK2 = 8;
    private static final int WEEKS_TK1_END_INDEX = 7;
    private static final int WEEKS_TK2_START_INDEX = 12;
    private static final int WEEKS_TK2_END_INDEX = 19;
    private static final int MAX_COLUMNS = 26;

    private static class ComponentGrades {
        String name;
        List<String> grades1_7;
        String rk1;
        List<String> grades8_15;
        String rk2;
        String finalExam;

        public ComponentGrades(String name, List<String> grades1_7, String rk1,
                               List<String> grades8_15, String rk2, String finalExam) {
            this.name = name;
            this.grades1_7 = grades1_7;
            this.rk1 = rk1;
            this.grades8_15 = grades8_15;
            this.rk2 = rk2;
            this.finalExam = finalExam;
        }
    }

    private static double safeParse(String gradeStr) {
        if (gradeStr == null) return Double.NaN;
        String s = gradeStr.trim();
        if (s.isEmpty() || s.equalsIgnoreCase("н.п.")) return Double.NaN;
        try {
            return Double.parseDouble(s);
        } catch (NumberFormatException e) {
            return Double.NaN;
        }
    }

    private static double calculateAverage(List<String> grades, int totalWeeks) {
        if (grades == null || grades.isEmpty()) return 0.0;
        List<Double> parsed = new ArrayList<>();
        for (String g : grades) {
            if (g != null && !g.trim().equalsIgnoreCase("н.п.") && !g.trim().isEmpty()) {
                parsed.add(Double.parseDouble(g.trim()));
            }
        }
        if (parsed.isEmpty()) return 0.0;
        double sum = parsed.stream().mapToDouble(Double::doubleValue).sum();
        // делим на количество реально учтённых значений (не "н.п.")
        return sum / parsed.size();
    }

    public static double calculateTKComponent(List<String> gradesWeeks, int totalWeeks) {
        return calculateAverage(gradesWeeks, totalWeeks);
    }

    public static double calculateTKTotal(double lecture, double practice, double srsp) {
        return (lecture * 0.20) + (practice * 0.50) + (srsp * 0.30);
    }

    public static double calculateR(double tkTotal, double rkGrade) {
        return (tkTotal + rkGrade) / 2.0;
    }

    public static double calculateAdmissionRating(double r1, double r2) {
        return (r1 + r2) / 2.0;
    }

    public static double calculateFinalGrade(double finalExam, double admissionRating) {
        return (finalExam * 0.40) + (admissionRating * 0.60);
    }

    // ---------- FILE FILL WITH 90 ----------
    private static void fillAndSaveWith90(String inputFilePath, String outputFilePath) throws IOException {
        System.out.println("Чтение CSV...");
        List<String[]> rows = new ArrayList<>();
        String headerLine;

        try (BufferedReader br = new BufferedReader(new InputStreamReader(new FileInputStream(inputFilePath), StandardCharsets.UTF_8))) {
            headerLine = br.readLine();
            if (headerLine == null) throw new IOException("CSV пустой или отсутствует заголовок.");
            String line;
            while ((line = br.readLine()) != null) {
                String[] values = line.split(",", -1);
                if (values.length < MAX_COLUMNS) {
                    String[] tmp = Arrays.copyOf(values, MAX_COLUMNS);
                    for (int i = values.length; i < MAX_COLUMNS; i++) tmp[i] = "";
                    values = tmp;
                }
                rows.add(values);
            }
        }

        List<String[]> filledRows = new ArrayList<>();
        for (String[] row : rows) {
            if (row.length == 0) {
                filledRows.add(row);
                continue;
            }

            String[] newRow = Arrays.copyOf(row, Math.max(row.length, MAX_COLUMNS));

            // TK1  индексы: 1..7
            for (int c = 1; c <= WEEKS_TK1_END_INDEX; c++) {
                String val = newRow[c];
                if (val == null || val.trim().isEmpty()) {
                    newRow[c] = "90";
                }
            }

            // TK2 индексы: 12..19
            for (int c = WEEKS_TK2_START_INDEX; c <= WEEKS_TK2_END_INDEX; c++) {
                String val = newRow[c];
                if (val == null || val.trim().isEmpty()) {
                    newRow[c] = "90";
                }
            }

            // РК1 (index 10), РК2 (index 22), FinalExam (index 25)
            int[] specialIndexes = {10, 22, 25};
            for (int idx : specialIndexes) {
                if (idx < newRow.length) {
                    String val = newRow[idx];
                    if (val == null || val.trim().isEmpty()) {
                        newRow[idx] = "90";
                        System.out.printf("Нету данных в ячейке %d — поставили 90%n", idx + 1);
                    }
                }
            }

            filledRows.add(newRow);
        }

        // Сохраняем CSV
        try (BufferedWriter bw = new BufferedWriter(new OutputStreamWriter(new FileOutputStream(outputFilePath), StandardCharsets.UTF_8))) {
            bw.write(headerLine);
            bw.newLine();
            for (String[] r : filledRows) {
                String[] toWrite = Arrays.copyOf(r, MAX_COLUMNS);
                for (int i = 0; i < toWrite.length; i++) if (toWrite[i] == null) toWrite[i] = "";
                bw.write(String.join(",", toWrite));
                bw.newLine();
            }
        }

        System.out.println("✅ Файл с 90 для пустых ячеек создан: " + outputFilePath);
    }

    // ---------- CSV -> ComponentGrades ----------
    private static List<ComponentGrades> readGradesFromCsv(String filePath) throws IOException {
        List<ComponentGrades> list = new ArrayList<>();

        try (BufferedReader br = new BufferedReader(new InputStreamReader(new FileInputStream(filePath), StandardCharsets.UTF_8))) {
            String line;
            int lineNumber = 0;
            while ((line = br.readLine()) != null) {
                lineNumber++;
                if (lineNumber == 1 || line.trim().isEmpty()) continue;
                String[] values = line.split(",", -1);
                if (values.length < MAX_COLUMNS) continue;
                String name = values[0].trim();
                if (!name.equals("Лекция") && !name.equals("Практика") && !name.equals("СРСП")) continue;

                List<String> grades1_7 = new ArrayList<>();
                for (int i = 1; i <= WEEKS_TK1; i++) grades1_7.add(values[i]);

                String rk1 = values.length > 10 ? values[10] : "90";
                String rk2 = values.length > 22 ? values[22] : "90";
                String finalExam = values.length > 25 ? values[25] : "90";

                List<String> grades8_15 = new ArrayList<>();
                for (int i = 12; i < 12 + WEEKS_TK2; i++) grades8_15.add(values[i]);

                list.add(new ComponentGrades(name, grades1_7, rk1, grades8_15, rk2, finalExam));
            }
        }
        return list;
    }

    public static void main(String[] args) {
        final String INPUT_CSV = "grades.csv";
        final String FILLED_CSV = "grades_filled.csv";

        try {
            // 1) Заполнение пустых ячеек 90
            fillAndSaveWith90(INPUT_CSV, FILLED_CSV);

            // 2) Чтение и расчёт итогов
            System.out.println("Шаг 2: Расчет оценок из " + FILLED_CSV + " ...");
            List<ComponentGrades> all = readGradesFromCsv(FILLED_CSV);

            ComponentGrades lecture = all.stream().filter(x -> x.name.equals("Лекция"))
                    .findFirst().orElseThrow(() -> new NoSuchElementException("Не найдена строка 'Лекция'"));
            ComponentGrades practice = all.stream().filter(x -> x.name.equals("Практика"))
                    .findFirst().orElseThrow(() -> new NoSuchElementException("Не найдена строка 'Практика'"));
            ComponentGrades srsp = all.stream().filter(x -> x.name.equals("СРСП"))
                    .findFirst().orElseThrow(() -> new NoSuchElementException("Не найдена строка 'СРСП'"));

            double tk1Lecture = calculateTKComponent(lecture.grades1_7, WEEKS_TK1);
            double tk1Practice = calculateTKComponent(practice.grades1_7, WEEKS_TK1);
            double tk1SRSP = calculateTKComponent(srsp.grades1_7, WEEKS_TK1);
            double tk1Total = calculateTKTotal(tk1Lecture, tk1Practice, tk1SRSP);
            double rk1 = safeParse(lecture.rk1);
            double r1 = calculateR(tk1Total, rk1);

            double tk2Lecture = calculateTKComponent(lecture.grades8_15, WEEKS_TK2);
            double tk2Practice = calculateTKComponent(practice.grades8_15, WEEKS_TK2);
            double tk2SRSP = calculateTKComponent(srsp.grades8_15, WEEKS_TK2);
            double tk2Total = calculateTKTotal(tk2Lecture, tk2Practice, tk2SRSP);
            double rk2 = safeParse(lecture.rk2);
            double r2 = calculateR(tk2Total, rk2);

            double unroundedAdmissionRating = calculateAdmissionRating(r1, r2);
            double admissionRating = Math.round(unroundedAdmissionRating);
            double finalExam = safeParse(lecture.finalExam);
            double unroundedFinalGrade = calculateFinalGrade(finalExam, admissionRating);
            double finalGrade = Math.round(unroundedFinalGrade);

            // Вывод
            System.out.println("\n===============================================");
            System.out.println("     ИТОГОВЫЙ РАСЧЁТ ОЦЕНОК");
            System.out.println("===============================================");
            System.out.printf("ТК1 (Лекции, /%d недель): %.2f%n", WEEKS_TK1, tk1Lecture);
            System.out.printf("ТК1 (Практика, /%d недель): %.2f%n", WEEKS_TK1, tk1Practice);
            System.out.printf("ТК1 (СРСП, /%d недель): %.2f%n", WEEKS_TK1, tk1SRSP);
            System.out.printf("ТК1 ОБЩ. (20/50/30): %.2f%n", tk1Total);
            System.out.printf("РК1 Оценка: %.2f%n", rk1);
            System.out.printf("Р1: (%.2f + %.2f) / 2 = %.2f%n", tk1Total, rk1, r1);
            System.out.println("===============================================");
            System.out.printf("ТК2 (Лекции, /%d недель): %.2f%n", WEEKS_TK2, tk2Lecture);
            System.out.printf("ТК2 (Практика, /%d недель): %.2f%n", WEEKS_TK2, tk2Practice);
            System.out.printf("ТК2 (СРСП, /%d недель): %.2f%n", WEEKS_TK2, tk2SRSP);
            System.out.printf("ТК2 ОБЩ. (20/50/30): %.2f%n", tk2Total);
            System.out.printf("РК2 Оценка: %.2f%n", rk2);
            System.out.printf("Р2: (%.2f + %.2f) / 2 = %.2f%n", tk2Total, rk2, r2);

            System.out.println("\n--- Итоговая оценка ---");
            System.out.printf("Неокругленный Рейтинг допуска: %.2f%n", unroundedAdmissionRating);
            System.out.printf("Рейтинг допуска (округленный, 60%%): %.0f%n", admissionRating);
            System.out.printf("Итоговый контроль (40%%): %.2f%n", finalExam);
            System.out.printf("Неокругленная Итоговая оценка: %.2f%n", unroundedFinalGrade);
            System.out.printf("ИТОГОВАЯ ОЦЕНКА (округленная): %.0f%n", finalGrade);

        } catch (IOException e) {
            System.err.println("IOException: " + e.getMessage());
            e.printStackTrace();
        } catch (Exception e) {
            System.err.println("Error: " + e.getMessage());
            e.printStackTrace();
        }
    }
}