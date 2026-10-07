package com.example.vimaverse;

import android.graphics.Color;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

import androidx.fragment.app.Fragment;

import com.github.mikephil.charting.charts.BarChart;
import com.github.mikephil.charting.components.XAxis;
import com.github.mikephil.charting.data.BarData;
import com.github.mikephil.charting.data.BarDataSet;
import com.github.mikephil.charting.data.BarEntry;
import com.github.mikephil.charting.formatter.IndexAxisValueFormatter;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.QueryDocumentSnapshot;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.TreeMap;

public class CalendarFragment extends Fragment {

    private BarChart barChart;
    private FirebaseFirestore db;
    private FirebaseAuth mAuth;
    private TextView tvTotalHistory, tvRecord;
    private Button btnDays, btnWeeks, btnMonths;

    // Δομή για να κρατάμε τα δεδομένα στη μνήμη χωρίς να τα κατεβάζουμε συνέχεια
    static class DailyRecord {
        String dateStr;
        float steps;
        public DailyRecord(String dateStr, float steps) {
            this.dateStr = dateStr;
            this.steps = steps;
        }
    }
    private List<DailyRecord> allRecords = new ArrayList<>();

    //Φορτώνει το γραφικό του Ημερολογίου, συνδέει τα κουμπιά (Ημέρες,Εβδομάδες, Μήνες) και κατεβάζει τα δεδομένα ιστορικού.
    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        //Προετοιμασία UI
        View view = inflater.inflate(R.layout.fragment_calendar, container, false);

        barChart = view.findViewById(R.id.barChart);
        tvTotalHistory = view.findViewById(R.id.tvTotalHistory);
        tvRecord = view.findViewById(R.id.tvRecord);
        btnDays = view.findViewById(R.id.btnDays);
        btnWeeks = view.findViewById(R.id.btnWeeks);
        btnMonths = view.findViewById(R.id.btnMonths);

        db = FirebaseFirestore.getInstance();
        mAuth = FirebaseAuth.getInstance();

        // Εναλλαγή προβολών στο γράφημα
        btnDays.setOnClickListener(v -> updateChart("DAYS"));
        btnWeeks.setOnClickListener(v -> updateChart("WEEKS"));
        btnMonths.setOnClickListener(v -> updateChart("MONTHS"));
        //Ανάγνωση δεδομένων από το Firebase
        loadStepHistory();

        return view;
    }

    //Αντλεί όλο το ιστορικό βημάτων ανά ημέρα, υπολογίζει ρεκόρ και περνάει τα δεδομένα στο γράφημα.
    private void loadStepHistory() {
        if (mAuth.getCurrentUser() == null) return;
        String currentUserId = mAuth.getCurrentUser().getUid();

        db.collection("users").document(currentUserId).collection("daily_steps")
                .get()
                .addOnSuccessListener(queryDocumentSnapshots -> {
                    allRecords.clear();// Καθαρίζει την παλιά μνήμη
                    int totalSteps = 0;
                    int recordSteps = 0;

                    //Διαβάζει ένα-ένα τα έγγραφα του Firebase
                    for (QueryDocumentSnapshot document : queryDocumentSnapshots) {
                        String dateStr = document.getId();// Η ημερομηνία είναι το ID του εγγράφου
                        Long stepsLong = document.getLong("steps");
                        float steps = (stepsLong != null) ? stepsLong.floatValue() : 0f;
                        // Τα αποθηκεύει στην τοπική λίστα
                        allRecords.add(new DailyRecord(dateStr, steps));
                        // Υπολογισμός Συνολικών βημάτων & Ρεκόρ
                        int currentDaySteps = (int) steps;
                        totalSteps += currentDaySteps;
                        if (currentDaySteps > recordSteps) {
                            recordSteps = currentDaySteps;
                        }
                    }

                    //Εμφάνιση των αριθμών στο UI
                    tvTotalHistory.setText(String.valueOf(totalSteps));
                    tvRecord.setText(String.valueOf(recordSteps));

                    if (!allRecords.isEmpty()) {
                        // Ταξινομούμε τα δεδομένα από το παλαιότερο στο νεότερο
                        Collections.sort(allRecords, (a, b) -> a.dateStr.compareTo(b.dateStr));
                        updateChart("DAYS"); // Προεπιλογή: Ημέρες
                    } else {
                        Toast.makeText(requireContext(), "Δεν υπάρχει ακόμα ιστορικό", Toast.LENGTH_SHORT).show();
                    }
                });
    }

    //Ομαδοποιεί τα βήματα ανά ημέρα, εβδομάδα ή μήνα (ανάλογα με το φίλτρο).
    private void updateChart(String mode) {
        if (allRecords.isEmpty()) return;

        TreeMap<String, Float> groupedData = new TreeMap<>();
        HashMap<String, String> keyToLabel = new HashMap<>();
        SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd", Locale.getDefault());
        Calendar cal = Calendar.getInstance();

        // 1. Ομαδοποίηση των δεδομένων
        for (DailyRecord record : allRecords) {
            try {
                Date date = sdf.parse(record.dateStr);
                cal.setTime(date);
                int year = cal.get(Calendar.YEAR);

                String key;
                String label;

                if (mode.equals("WEEKS")) {
                    // Βρίσκουμε την πρώτη μέρα της εβδομάδας (π.χ. Δευτέρα)
                    cal.set(Calendar.DAY_OF_WEEK, cal.getFirstDayOfWeek());
                    String firstDay = new SimpleDateFormat("dd/MM", Locale.getDefault()).format(cal.getTime());

                    // Προσθέτουμε 6 μέρες για να βρούμε την τελευταία (π.χ. Κυριακή)
                    cal.add(Calendar.DAY_OF_WEEK, 6);
                    String lastDay = new SimpleDateFormat("dd/MM/yy", Locale.getDefault()).format(cal.getTime());

                    int week = cal.get(Calendar.WEEK_OF_YEAR);
                    key = year + "-W" + String.format(Locale.getDefault(), "%02d", week); // Το key μένει ίδιο για τη σωστή ταξινόμηση
                    label = firstDay + " - " + lastDay;
                } else if (mode.equals("MONTHS")) {
                    int month = cal.get(Calendar.MONTH) + 1;
                    key = year + "-" + String.format("%02d", month);
                    label = month + "/" + year;
                } else {
                    key = record.dateStr;
                    label = record.dateStr.length() >= 5 ? record.dateStr.substring(5) : record.dateStr; // Εμφανίζει MM-dd
                }

                // Προσθέτει τα βήματα στην αντίστοιχη ομάδα
                groupedData.put(key, groupedData.getOrDefault(key, 0f) + record.steps);
                keyToLabel.put(key, label);
            } catch (Exception e) {
                e.printStackTrace();
            }
        }

        // Μεταφορά στο Γράφημα
        ArrayList<BarEntry> entries = new ArrayList<>();
        ArrayList<String> labels = new ArrayList<>();
        int index = 0;

        for (Map.Entry<String, Float> entry : groupedData.entrySet()) {
            entries.add(new BarEntry(index, entry.getValue()));
            labels.add(keyToLabel.get(entry.getKey()));
            index++;
        }

        setupChartUI(entries, labels);// Καλεί τη μέθοδο για να τα ζωγραφίσει
    }

    //Δημιουργεί τη γραφική παράσταση (μπάρες), προσαρμόζει τα χρώματα σε Dark Mode και προσθέτει το animation της μπάρας.
    private void setupChartUI(ArrayList<BarEntry> entries, ArrayList<String> dates) {
        //Έλεγχος Dark Mode και ορισμός χρωμάτων κειμένου/αξόνων
        boolean isDark = requireContext().getSharedPreferences("VimaVersePrefs", android.content.Context.MODE_PRIVATE)
                .getBoolean("darkMode", false);

        int textColor = isDark ? Color.WHITE : Color.parseColor("#2B2D42");
        int axisColor = isDark ? Color.parseColor("#E0E0E0") : Color.parseColor("#8D99AE");
        //Δημιουργία Μπαρών (Χρώμα και Κείμενο αξίας)
        BarDataSet dataSet = new BarDataSet(entries, "Βήματα");
        dataSet.setColor(Color.parseColor("#5E6AD2"));
        dataSet.setValueTextColor(textColor);
        dataSet.setValueTextSize(12f);

        BarData barData = new BarData(dataSet);
        barData.setBarWidth(0.5f);
        barChart.setData(barData);

        //Ρύθμιση άξονα Χ (Κάτω μέρος - Ημερομηνίες)
        XAxis xAxis = barChart.getXAxis();
        xAxis.setValueFormatter(new IndexAxisValueFormatter(dates));
        xAxis.setPosition(XAxis.XAxisPosition.BOTTOM);
        xAxis.setTextColor(axisColor);
        xAxis.setDrawGridLines(false);
        xAxis.setGranularity(1f);
        xAxis.setAxisMinimum(-0.5f);
        xAxis.setAxisMaximum(entries.size() - 0.5f);

        //Ρύθμιση αξόνων Υ (Αριστερά/Δεξιά) και Λεζάντας
        barChart.getAxisLeft().setTextColor(axisColor);
        barChart.getAxisLeft().setAxisMinimum(0f);
        barChart.getAxisRight().setEnabled(false);
        barChart.getLegend().setTextColor(axisColor);
        barChart.getDescription().setEnabled(false);

        // Προσθέτει animation για να φαίνεται η αλλαγή
        barChart.animateY(500);
        barChart.invalidate();
    }
}