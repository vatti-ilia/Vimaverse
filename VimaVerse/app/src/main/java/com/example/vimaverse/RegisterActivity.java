package com.example.vimaverse;

import android.content.Intent;
import android.os.Bundle;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.firestore.FirebaseFirestore;

import java.util.HashMap;
import java.util.Map;

public class RegisterActivity extends AppCompatActivity {

    // Δήλωση των μεταβλητών που θα χρησιμοποιήσουμε σε όλη την οθόνη
    private EditText etUsername, etRegEmail, etRegPassword;
    private Button btnCreateAccount;
    private TextView tvGoToLogin;
    private FirebaseAuth mAuth;
    private FirebaseFirestore db;


    // Συνδέει τα γραφικά με τον κώδικα, διαβάζει τα στοιχεία του χρήστη και τον εγγράφει στο Firebase.
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_register);

        // Αρχικοποίηση Firebase
        mAuth = FirebaseAuth.getInstance();
        db = FirebaseFirestore.getInstance();

        // Σύνδεση με το XML
        etUsername = findViewById(R.id.etUsername);
        etRegEmail = findViewById(R.id.etRegEmail);
        etRegPassword = findViewById(R.id.etRegPassword);
        btnCreateAccount = findViewById(R.id.btnCreateAccount);
        tvGoToLogin = findViewById(R.id.tvGoToLogin);

        // Λειτουργία δημιουργίας λογαριασμού
        btnCreateAccount.setOnClickListener(v -> {
            String username = etUsername.getText().toString().trim();
            String email = etRegEmail.getText().toString().trim();
            String password = etRegPassword.getText().toString().trim();

            // Έλεγχος αν τα πεδία είναι κενά ή ο κωδικός μικρός
            if (username.isEmpty() || email.isEmpty() || password.length() < 6) {
                Toast.makeText(this, "Συμπληρώστε σωστά όλα τα πεδία (Κωδικός > 6 χαρακτήρες)", Toast.LENGTH_SHORT).show();
                return;
            }

            // Δημιουργία χρήστη στο Firebase Auth
            mAuth.createUserWithEmailAndPassword(email, password)
                    .addOnSuccessListener(authResult -> {
                        String userId = mAuth.getCurrentUser().getUid();

                        // Δημιουργία του ψηφιακού προφίλ του χρήστη με αρχικές τιμές (0)
                        Map<String, Object> userMap = new HashMap<>();
                        userMap.put("username", username);
                        userMap.put("total_steps", 0);
                        userMap.put("coins", 0);

                        // Αποθήκευση στο Firestore
                        db.collection("users").document(userId).set(userMap)
                                .addOnSuccessListener(aVoid -> {
                                    // Αν όλα πάνε καλά, πάμε στην κεντρική οθόνη του πλανήτη
                                    startActivity(new Intent(RegisterActivity.this, MainActivity.class));
                                    finishAffinity(); // Κλείνει εντελώς τις οθόνες Login/Register για να μην μπορεί να γυρίσει πίσω με το back button
                                });
                    })
                    //Αν το Firebase απορρίψει την εγγραφή (π.χ. υπάρχει ήδη το email), δείξε το σφάλμα
                    .addOnFailureListener(e -> Toast.makeText(this, "Σφάλμα Εγγραφής: " + e.getMessage(), Toast.LENGTH_SHORT).show());
        });

        // Επιστροφή στο Login
        tvGoToLogin.setOnClickListener(v -> finish());
    }
}