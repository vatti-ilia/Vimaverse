package com.example.vimaverse;

import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.TextView;
import androidx.fragment.app.Fragment;
import com.google.android.material.switchmaterial.SwitchMaterial;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.firestore.FirebaseFirestore;

public class SettingsFragment extends Fragment {

    private TextView tvSettingsCoins;
    private SwitchMaterial switchDarkMode;
    private Button btnLangGR, btnLangEN, btnLangIT, btnSettingsLogout;
    private FirebaseAuth mAuth;
    private FirebaseFirestore db;
    private SharedPreferences prefs;

    //Διαβάζει ζωντανά τα στοιχεία του χρήστη (Βήματα/Νομίσματα) και διαχειρίζεται τα κουμπιά.
    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        //Φόρτωση του οπτικού (XML)
        View view = inflater.inflate(R.layout.fragment_settings, container, false);

        //Σύνδεση μεταβλητών με τα γραφικά στοιχεία
        tvSettingsCoins = view.findViewById(R.id.tvSettingsCoins);
        switchDarkMode = view.findViewById(R.id.switchDarkMode);
        btnLangGR = view.findViewById(R.id.btnLangGR);
        btnLangEN = view.findViewById(R.id.btnLangEN);
        btnLangIT = view.findViewById(R.id.btnLangIT);
        btnSettingsLogout = view.findViewById(R.id.btnSettingsLogout);

        mAuth = FirebaseAuth.getInstance();
        db = FirebaseFirestore.getInstance();
        prefs = requireActivity().getSharedPreferences("VimaVersePrefs", Context.MODE_PRIVATE);

        TextView tvSettingsUsername = view.findViewById(R.id.tvSettingsUsername);
        TextView tvSettingsTodaySteps = view.findViewById(R.id.tvSettingsTodaySteps);

        if (mAuth.getCurrentUser() != null) {
            String userId = mAuth.getCurrentUser().getUid();

            // Ζωντανή ενημέρωση προφίλ (Όνομα & Βήματα) από το Firebase
            db.collection("users").document(userId)
                    .addSnapshotListener((doc, error) -> {
                        if (doc != null && doc.exists()) {
                            String username = doc.getString("username");
                            Long steps = doc.getLong("today_steps");

                            if (username != null && tvSettingsUsername != null) {
                                tvSettingsUsername.setText(username);
                            }
                            if (steps != null && tvSettingsTodaySteps != null) {
                                String stepText = getString(R.string.settings_today_steps) + " " + steps;
                                tvSettingsTodaySteps.setText(stepText);
                            }
                        }
                    });

            // Φόρτωση Νομισμάτων
            db.collection("users").document(userId)
                    .addSnapshotListener((documentSnapshot, error) -> {
                        if (documentSnapshot != null && documentSnapshot.exists()) {
                            Long coins = documentSnapshot.getLong("coins");
                            if (coins != null && isAdded()) {
                                tvSettingsCoins.setText("🪙 " + coins);
                            }
                        }
                    });
        }

        // Dark Mode
        switchDarkMode.setChecked(prefs.getBoolean("darkMode", false));// Διαβάζει την παλιά επιλογή
        switchDarkMode.setOnCheckedChangeListener((buttonView, isChecked) -> {
            // Αποθηκεύει τη νέα επιλογή
            prefs.edit().putBoolean("darkMode", isChecked).apply();
            // Αλλάζει το θέμα της εφαρμογής σε Νύχτα/Μέρα
            if (isChecked) {
                androidx.appcompat.app.AppCompatDelegate.setDefaultNightMode(androidx.appcompat.app.AppCompatDelegate.MODE_NIGHT_YES);
            } else {
                androidx.appcompat.app.AppCompatDelegate.setDefaultNightMode(androidx.appcompat.app.AppCompatDelegate.MODE_NIGHT_NO);
            }

            refreshFragment();// Ανανεώνει την οθόνη των ρυθμίσεων

            // Επικοινωνεί με το MainActivity για να αλλάξει το χρώμα στο μενού
            if (getActivity() instanceof MainActivity) {
                ((MainActivity) getActivity()).forceMenuColorUpdate(isChecked);
            }
        });

        // Γλώσσας (Ελληνικά, Αγγλικά, Ιταλικά)
        View.OnClickListener langListener = v -> {
            String code = "el";// Προεπιλογή: Ελληνικά
            if (v.getId() == R.id.btnLangEN) code = "en";
            else if (v.getId() == R.id.btnLangIT) code = "it";

            prefs.edit().putString("appLanguage", code).apply();// Αποθήκευση επιλογής
            setLocale(code);// Εφαρμογή μετάφρασης
        };

        btnLangGR.setOnClickListener(langListener);
        btnLangEN.setOnClickListener(langListener);
        btnLangIT.setOnClickListener(langListener);

        // Logout
        btnSettingsLogout.setOnClickListener(v -> {
            mAuth.signOut();
            if (getActivity() != null) {
                // Ανοίγει την αρχική οθόνη (SplashActivity)
                Intent intent = new Intent(getActivity(), SplashActivity.class);
                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);// Κλείνει όλες τις άλλες οθόνες
                startActivity(intent);

                // Τερματίζει πλήρως την εφαρμογή μετά από 0.3 δευτερόλεπτα(ώστε να τερματίσει το Unity)
                new android.os.Handler(android.os.Looper.getMainLooper()).postDelayed(() -> {
                    System.exit(0);
                }, 300);
            }
        });

        return view;
    }

    //Αλλάζει ζωντανά τη γλώσσα σε ολόκληρη την εφαρμογή και ενημερώνει το κεντρικό Activity να μεταφράσει τα κείμενά του.
    private void setLocale(String lang) {
        java.util.Locale myLocale = new java.util.Locale(lang);
        android.content.res.Resources res = getResources();
        android.util.DisplayMetrics dm = res.getDisplayMetrics();
        android.content.res.Configuration conf = res.getConfiguration();
        conf.setLocale(myLocale);
        res.updateConfiguration(conf, dm);

        refreshFragment();// Ανανεώνει τη σελίδα ρυθμίσεων για να μεταφραστεί

        // Επικοινωνία με το MainActivity για μετάφραση
        if (getActivity() instanceof MainActivity) {
            ((MainActivity) getActivity()).updateLanguageTexts();
        }
    }

    //Φορτώνει ξανά την τρέχουσα οθόνη (Ρυθμίσεις) ώστε να εφαρμοστούν άμεσα οι οπτικές αλλαγές (Γλώσσα, Χρώματα).
    private void refreshFragment() {
        if (getActivity() != null) {
            getParentFragmentManager().beginTransaction()
                    .replace(R.id.fragment_container, new SettingsFragment())
                    .commit();
        }
    }
}
