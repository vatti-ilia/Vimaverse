package com.example.vimaverse;

import android.annotation.SuppressLint;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.os.Build;
import android.os.IBinder;

import androidx.core.app.NotificationCompat;

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.firestore.FieldValue;
import com.google.firebase.firestore.FirebaseFirestore;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

public class StepService extends Service implements SensorEventListener {
    // Μεταβλητές για τον αισθητήρα και την οθόνη
    private SensorManager sensorManager;
    private Sensor stepCounter;


    @SuppressLint({"WrongConstant"})
    @Override
    public void onCreate() {
        super.onCreate();
        createNotificationChannel();
        //Χτίσιμο της μόνιμης ειδοποίησης που φαίνεται στην μπάρα
        Notification notification = new NotificationCompat.Builder(this, "VIMAVERSE_CHANNEL")
                .setContentTitle("VimaVerse")
                .setContentText("Μετράμε τα βήματά σου ζωντανά!")
                .setSmallIcon(android.R.drawable.ic_menu_compass)
                .setPriority(NotificationCompat.PRIORITY_LOW)
                .build();

        //Ξεκινάει την υπηρεσία ως "Foreground" (στο προσκήνιο) ώστε να μην την κλείσει το Android
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(1, notification, android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_HEALTH);
        } else {
            startForeground(1, notification);
        }



        //Εντοπισμός του αισθητήρα βημάτων (TYPE_STEP_COUNTER)
        sensorManager = (SensorManager) getSystemService(SENSOR_SERVICE);
        if (sensorManager != null) {
            // Χρησιμοποιούμε τον COUNTER που δεν κοιμάται ποτέ!
            stepCounter = sensorManager.getDefaultSensor(Sensor.TYPE_STEP_COUNTER);
        }
    }

    //Δίνει την εντολή στον αισθητήρα να αρχίσει να ακούει για βήματα.
    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        if (stepCounter != null) {
            // Ζητάει ενημερώσεις από τον αισθητήρα
            sensorManager.registerListener(this, stepCounter, SensorManager.SENSOR_DELAY_UI);
        }
        return START_STICKY;// Αν το Android κλείσει την υπηρεσία λόγω μνήμης, να την ξανανοίξει αμέσως
    }

    //Τρέχει ΑΥΤΟΜΑΤΑ ΚΑΘΕ ΦΟΡΑ που ο χρήστης κάνει ένα βήμα!
    @Override
    public void onSensorChanged(SensorEvent event) {
        if (event.sensor.getType() == Sensor.TYPE_STEP_COUNTER) {
            float currentSteps = event.values[0];// Ο συνολικός αριθμός βημάτων από τότε που άνοιξε το κινητό
            saveStepToFirebase(currentSteps);// Στέλνει τα βήματα για αποθήκευση
        }
    }
    //Υπολογίζει πόσα νέα βήματα έγιναν, ελέγχει αν άλλαξε η μέρα,και αποθηκεύει τα βήματα/νομίσματα στο Firebase.
    private void saveStepToFirebase(float currentSteps) {
        FirebaseAuth mAuth = FirebaseAuth.getInstance();
        if (mAuth.getCurrentUser() == null) return;
        String userId = mAuth.getCurrentUser().getUid();

        FirebaseFirestore db = FirebaseFirestore.getInstance();
        SharedPreferences prefs = getSharedPreferences("VimaVersePrefs", Context.MODE_PRIVATE);

        //Βρίσκουμε τη σημερινή ημερομηνία
        String today = new SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(new Date());
        String savedDate = prefs.getString("lastDate", "");

        // Αν άλλαξε η μέρα, μηδενίζουμε τα σημερινά βήματα στο Firebase
        if (!today.equals(savedDate)) {
            prefs.edit().putString("lastDate", today).apply();
            prefs.edit().putFloat("lastSensorValue", currentSteps).apply();
            db.collection("users").document(userId).update("today_steps", 0);
            return;
        }

        float lastSensorValue = prefs.getFloat("lastSensorValue", currentSteps);

        // Αν το κινητό έκανε επανεκκίνηση, ο αισθητήρας ξεκινάει πάλι από το 0
        if (currentSteps < lastSensorValue) {
            prefs.edit().putFloat("lastSensorValue", currentSteps).apply();
            return;
        }

        //Υπολογισμός των νέων βημάτων
        int stepDifference = (int) (currentSteps - lastSensorValue);

        // Αν έχουν γίνει νέα βήματα, τα προσθέτουμε στο Firebase
        if (stepDifference > 0) {
            prefs.edit().putFloat("lastSensorValue", currentSteps).apply();

            // Ενημερώνουμε ΜΟΝΟ τα σημερινά βήματα στο κεντρικό προφίλ
            db.collection("users").document(userId)
                    .update("today_steps", FieldValue.increment(stepDifference));

            // Ενημέρωση ιστορικού βημάτων (για το Ημερολόγιο)
            java.util.Map<String, Object> dailyData = new java.util.HashMap<>();
            dailyData.put("steps", FieldValue.increment(stepDifference));

            db.collection("users").document(userId)
                    .collection("daily_steps").document(today)
                    .set(dailyData, com.google.firebase.firestore.SetOptions.merge());

            // Υπολογισμός και απονομή νομισμάτων με βάση τα 100 βήματα
            int unconvertedSteps = prefs.getInt("unconvertedSteps", 0) + stepDifference;
            int coinsToAward = unconvertedSteps / 100;
            int remainder = unconvertedSteps % 100;
            prefs.edit().putInt("unconvertedSteps", remainder).apply();

            if (coinsToAward > 0) {

                db.collection("users").document(userId)
                        .update("coins", FieldValue.increment(coinsToAward));
            }
        }
    }

    @Override
    public void onAccuracyChanged(Sensor sensor, int accuracy) {}

    @Override
    public IBinder onBind(Intent intent) { return null; }
    //Ρυθμίζει την ειδοποίηση ώστε να μην βγάζει ενοχλητική "κονκάρδα"(badge) στο εικονίδιο της εφαρμογής.
    private void createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel serviceChannel = new NotificationChannel(
                    "VIMAVERSE_CHANNEL",
                    "VimaVerse Step Tracker",
                    NotificationManager.IMPORTANCE_LOW
            );

            // Κρύβει το σημαδάκι ειδοποίησης στο εικονίδιο
            serviceChannel.setShowBadge(false);

            NotificationManager manager = getSystemService(NotificationManager.class);
            if (manager != null) {
                manager.createNotificationChannel(serviceChannel);
            }
        }
    }

}