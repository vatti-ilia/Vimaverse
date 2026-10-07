package com.example.vimaverse;

import android.content.Intent;
import android.os.Bundle;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.google.firebase.auth.FirebaseAuth;

public class LoginActivity extends AppCompatActivity {

    // Δήλωση των μεταβλητών για τα στοιχεία της οθόνης
    private EditText etEmail, etPassword;
    private Button btnLogin;

    private TextView tvGoToRegister;
    // Η μεταβλητή για την επικοινωνία με το σύστημα ταυτοποίησης του Firebase
    private FirebaseAuth mAuth;

    //Εκτελείται μόλις ανοίξει η οθόνη Σύνδεσης. Ελέγχει αν ο χρήστης είναι ήδη συνδεδεμένος και διαχειρίζεται το πάτημα των κουμπιών.
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        //Προετοιμασία της οθόνης και σύνδεση με το γραφικό περιβάλλον (XML)
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_login);

        //Αρχικοποίηση του Firebase Auth
        mAuth = FirebaseAuth.getInstance();

        //Έλεγχος αν ο χρήστης είχε μείνει συνδεδεμένος από προηγούμενη φορά
        if (mAuth.getCurrentUser() != null) {
            goToMainActivity();//Τον στέλνει κατευθείαν στον Πλανήτη
        }

        //Σύνδεση των μεταβλητών της Java με τα IDs του XML
        etEmail = findViewById(R.id.etEmail);
        etPassword = findViewById(R.id.etPassword);
        btnLogin = findViewById(R.id.btnLogin);

        tvGoToRegister = findViewById(R.id.tvGoToRegister);

        // Λειτουργία Εισόδου
        btnLogin.setOnClickListener(v -> {
            //Ανάγνωση των όσων πληκτρολόγησε και αφαίρεση κενών (trim)
            String email = etEmail.getText().toString().trim();
            String password = etPassword.getText().toString().trim();

            //Έλεγχος αν άφησε κάποιο πεδίο άδειο
            if (email.isEmpty() || password.isEmpty()) {
                Toast.makeText(this, "Συμπληρώστε όλα τα πεδία", Toast.LENGTH_SHORT).show();
                return;// Σταματάει τον κώδικα εδώ
            }

            //Αποστολή των στοιχείων στο Firebase για επαλήθευση
            mAuth.signInWithEmailAndPassword(email, password)
                    .addOnSuccessListener(authResult -> goToMainActivity())
                    .addOnFailureListener(e -> Toast.makeText(this, "Λάθος στοιχεία!", Toast.LENGTH_SHORT).show());
        });

        // Μεταφορά στην οθόνη Εγγραφής όταν πατάει το κείμενο
        tvGoToRegister.setOnClickListener(v -> {
            // Ανοίγει την οθόνη Εγγραφής (RegisterActivity)
            startActivity(new Intent(LoginActivity.this, RegisterActivity.class));
        });
    }

    //Αλλάζει την οθόνη από το Login στο κεντρικό παιχνίδι (MainActivity) και "κλείνει" την οθόνη του Login.
    private void goToMainActivity() {
        //Δίνει την εντολή μετάβασης
        startActivity(new Intent(LoginActivity.this, MainActivity.class));
        //Κλείνει την οθόνη Σύνδεσης για να μην μπορεί να επιστρέψει εδώ πατώντας το κουμπί "Πίσω" του κινητού
        finish();
    }
}