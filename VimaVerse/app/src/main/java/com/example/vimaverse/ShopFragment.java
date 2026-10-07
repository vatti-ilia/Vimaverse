package com.example.vimaverse;

import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.fragment.app.Fragment;

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.QueryDocumentSnapshot;

import java.util.HashMap;
import java.util.Map;

public class ShopFragment extends Fragment {

    // Δήλωση όλων των μεταβλητών που θα χρειαστούμε
    private TextView tvShopCoins;
    private LinearLayout productsContainer;
    private FirebaseFirestore db;
    private FirebaseAuth mAuth;
    private long currentCoins = 0;

    // Προετοιμάζει το γραφικό περιβάλλον του Καταστήματος όταν το ανοίγει ο χρήστης.
    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_shop, container, false);

        tvShopCoins = view.findViewById(R.id.tvShopCoins);
        productsContainer = view.findViewById(R.id.productsContainer);

        db = FirebaseFirestore.getInstance();
        mAuth = FirebaseAuth.getInstance();

        // Ζητάμε να φορτώσουν τα νομίσματα και τα αντικείμενα
        loadUserCoins();
        loadProductsFromFirebase();

        return view;
    }
    //Όποτε αλλάζουν τα νομίσματα του χρήστη,αλλάζει κατευθείαν και το νούμερο πάνω δεξιά στο Κατάστημα.
    private void loadUserCoins() {
        if (mAuth.getCurrentUser() == null) return;
        String userId = mAuth.getCurrentUser().getUid();

        db.collection("users").document(userId)
                .addSnapshotListener((documentSnapshot, error) -> {
                    if (documentSnapshot != null && documentSnapshot.exists()) {
                        Long coins = documentSnapshot.getLong("coins");
                        currentCoins = (coins != null) ? coins : 0;
                        if (isAdded()) {
                            tvShopCoins.setText("🪙 " + currentCoins);
                        }
                    }
                });
    }

    //Διαβάζει τη γλώσσα που έχει επιλέξει ο χρήστης (el, en, it), κατεβάζει τα αντικείμενα από το "shop_items" και τα ζωγραφίζει ένα-ένα.
    private void loadProductsFromFirebase() {
        if (!isAdded()) return;

        //Έλεγχος της επιλεγμένης γλώσσας
        android.content.SharedPreferences prefs = requireActivity().getSharedPreferences("VimaVersePrefs", android.content.Context.MODE_PRIVATE);
        String currentLang = prefs.getString("appLanguage", "el");

        //Κλείδωμα της μεταβλητής nameField (final) για να μην χτυπάει η Java
        final String nameField;
        if (currentLang.equals("en")) {
            nameField = "name_en";
        } else if (currentLang.equals("it")) {
            nameField = "name_it";
        } else {
            nameField = "name_el";
        }

        // Κατέβασμα δεδομένων
        db.collection("shop_items").get()
                .addOnSuccessListener(queryDocumentSnapshots -> {
                    if (!isAdded()) return;
                    productsContainer.removeAllViews();// Καθαρίζουμε την οθόνη

                    for (QueryDocumentSnapshot document : queryDocumentSnapshots) {
                        // Διαβάζουμε τα στοιχεία με βάση το σωστό πεδίο γλώσσας
                        String name = document.getString(nameField);
                        if (name == null) name = document.getString("name_el");// Προεπιλογή αν λείπει η μετάφραση

                        String icon = document.getString("icon");
                        Long priceLong = document.getLong("price");
                        int price = (priceLong != null) ? priceLong.intValue() : 0;
                        String prefabName = document.getString("prefabName");

                        // Στέλνουμε τα δεδομένα για να χτιστεί η κάρτα στην οθόνη
                        createProductCard(name, icon, price, prefabName);
                    }
                })
                .addOnFailureListener(e -> {
                    if (isAdded()) {
                        Toast.makeText(requireContext(), "Σφάλμα: " + e.getMessage(), Toast.LENGTH_LONG).show();
                    }
                });
    }

    //Δημιουργεί ΜΕΣΑ ΑΠΟ ΤΟΝ ΚΩΔΙΚΑ (όχι από XML) το οπτικό κουτάκι (κάρτα) για κάθε αντικείμενο, ελέγχοντας αν έχουμε Dark ή Light Mode.
    private void createProductCard(String name, String icon, int cost, String prefabName) {
        // Ελέγχουμε αν είναι ενεργό το Dark Mode
        boolean isDark = requireContext().getSharedPreferences("VimaVersePrefs", android.content.Context.MODE_PRIVATE)
                .getBoolean("darkMode", false);

        //Δημιουργία του εξωτερικού κουτιού (κάρτα)
        LinearLayout card = new LinearLayout(requireContext());
        card.setOrientation(LinearLayout.HORIZONTAL);
        card.setPadding(40, 40, 40, 40);
        card.setGravity(Gravity.CENTER_VERTICAL);

        GradientDrawable gd = new GradientDrawable();
        gd.setColor(isDark ? Color.parseColor("#2C2C2C") : Color.parseColor("#FFFFFF"));
        gd.setCornerRadius(40f);
        card.setBackground(gd);

        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        params.setMargins(0, 0, 0, 32);
        card.setLayoutParams(params);

        //Δημιουργία του Εικονιδίου/Emoji
        TextView tvIcon = new TextView(requireContext());
        tvIcon.setText(icon);
        tvIcon.setTextSize(36f);
        tvIcon.setPadding(0, 0, 32, 0);

        //Δημιουργία του Ονόματος
        LinearLayout textContainer = new LinearLayout(requireContext());
        textContainer.setOrientation(LinearLayout.VERTICAL);
        textContainer.setLayoutParams(new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));

        TextView tvName = new TextView(requireContext());
        tvName.setText(name);
        tvName.setTextColor(isDark ? Color.WHITE : Color.parseColor("#2B2D42"));
        tvName.setTextSize(18f);
        tvName.setTypeface(null, android.graphics.Typeface.BOLD);

        textContainer.addView(tvName);
        //Δημιουργία του Κουμπιού Αγοράς
        Button btnBuy = new Button(requireContext());
        btnBuy.setText("🪙 " + cost);
        btnBuy.setBackgroundColor(Color.parseColor("#5E6AD2"));
        btnBuy.setTextColor(Color.parseColor("#FFFFFF"));

        GradientDrawable btnShape = new GradientDrawable();
        btnShape.setColor(Color.parseColor("#5E6AD2"));
        btnShape.setCornerRadius(24f);
        btnBuy.setBackground(btnShape);

        // Όταν πατηθεί το κουμπί αγοράς, καλεί τη μέθοδο processPurchase
        btnBuy.setOnClickListener(v -> processPurchase(name, icon, cost, prefabName));

        //Ένωση όλων των στοιχείων και εμφάνιση στην οθόνη
        card.addView(tvIcon);
        card.addView(textContainer);
        card.addView(btnBuy);
        productsContainer.addView(card);
    }

    //Ελέγχει αν φτάνουν τα νομίσματα, τα αφαιρεί, και βάζει το αντικείμενοστην "αποθήκη" (inventory) του χρήστη.
    private void processPurchase(String itemName, String icon, int cost, String prefabName) {
        if (mAuth.getCurrentUser() == null) return;
        String userId = mAuth.getCurrentUser().getUid();

        //Έλεγχος επάρκειας υπολοίπου
        if (currentCoins < cost) {
            Toast.makeText(requireContext(), "Δεν έχεις αρκετά νομίσματα!", Toast.LENGTH_SHORT).show();
            return;
        }
        //Αφαίρεση νομισμάτων και ενημέρωση Firebase
        long newCoins = currentCoins - cost;
        db.collection("users").document(userId).update("coins", newCoins)
                .addOnSuccessListener(aVoid -> {
                    //Αν η αφαίρεση πετύχει, φτιάχνουμε το αντικείμενο για την αποθήκη
                    Map<String, Object> inventoryItem = new HashMap<>();
                    inventoryItem.put("name", itemName);
                    inventoryItem.put("icon", icon);
                    inventoryItem.put("isPlaced", false);// Το false σημαίνει ότι βρίσκεται στην αποθήκη, όχι στον πλανήτη
                    inventoryItem.put("prefabName", prefabName);// Το κλειδί για να το βρει το Unity

                    //Αποθήκευση στον φάκελο "inventory"
                    db.collection("users").document(userId).collection("inventory")
                            .add(inventoryItem)
                            .addOnSuccessListener(documentReference -> {

                            });
                })
                .addOnFailureListener(e -> {
                    if (isAdded()) {
                        Toast.makeText(requireContext(), "Σφάλμα αγοράς", Toast.LENGTH_SHORT).show();
                    }
                });
    }
}