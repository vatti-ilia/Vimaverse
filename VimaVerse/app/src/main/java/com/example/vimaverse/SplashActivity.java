package com.example.vimaverse;

import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.animation.Animation;
import android.view.animation.AnimationUtils;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.app.AppCompatDelegate;

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;

public class SplashActivity extends AppCompatActivity {

    //Oθόνη εκκίνησης (λογότυπο)
    //Aποφασίζει αν θα σε πάει στο Login ή κατευθείαν στον Πλανήτη.
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        // Φόρτωση ρυθμίσεων ΠΡΙΝ ανοίξει η οθόνη
        SharedPreferences prefs = getSharedPreferences("VimaVersePrefs", Context.MODE_PRIVATE);

        // Εφαρμογή Dark Mode
        boolean isDark = prefs.getBoolean("darkMode", false);
        if (isDark) {
            AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_YES);
        } else {
            AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_NO);
        }

        // Εφαρμογή Γλώσσας
        String lang = prefs.getString("appLanguage", "el");
        java.util.Locale myLocale = new java.util.Locale(lang);
        android.content.res.Resources res = getResources();
        android.util.DisplayMetrics dm = res.getDisplayMetrics();
        android.content.res.Configuration conf = res.getConfiguration();
        conf.setLocale(myLocale);
        res.updateConfiguration(conf, dm);

        //Φόρτωση του γραφικού περιβάλλοντος
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_splash);

        // Φόρτωση και εκτέλεση του animation
        TextView tvSplashLogo = findViewById(R.id.tvSplashLogo);
        Animation fadeIn = AnimationUtils.loadAnimation(this, R.anim.fade_in);
        tvSplashLogo.startAnimation(fadeIn);

        // Καθυστέρηση 2.5 δευτερολέπτων
        new Handler(Looper.getMainLooper()).postDelayed(() -> {
            //Έλεγχος αν ο χρήστης είναι ήδη συνδεδεμένος
            FirebaseUser currentUser = FirebaseAuth.getInstance().getCurrentUser();
            // Αν ναι, άνοιξε την κύρια εφαρμογή (Πλανήτης)
            if (currentUser != null) {
                startActivity(new Intent(SplashActivity.this, MainActivity.class));
            }
            // Αν όχι, άνοιξε την οθόνη Σύνδεσης
            else {
                startActivity(new Intent(SplashActivity.this, LoginActivity.class));
            }
            //Κλείσε την οθόνη Splash ώστε να μην μπορείς να γυρίσεις σε αυτή
            finish();
        }, 2500);// Χρόνος αναμονής σε χιλιοστά του δευτερολέπτου (2.5 sec)
    }
}