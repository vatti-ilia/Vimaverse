package com.example.vimaverse;

import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.fragment.app.Fragment;

import com.google.android.material.card.MaterialCardView;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.QueryDocumentSnapshot;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

public class FriendsFragment extends Fragment {

    private EditText etSearchUsername;
    private Button btnSearch, btnAddFriend;
    private LinearLayout friendsListContainer;
    private MaterialCardView resultCard;
    private TextView tvResultUsername;
    private FirebaseFirestore db;
    private FirebaseAuth mAuth;
    private String currentSearchedUserId = "";
    private String currentSearchedUsername = "";

    // Ομαδοποιούμε τα δεδομένα των φίλων
    private static class FriendItem {
        String name;
        long steps;
        String friendId;

        FriendItem(String name, long steps, String friendId) {
            this.name = name;
            this.steps = steps;
            this.friendId = friendId;
        }
    }

    //Φορτώνει τη διεπαφή, ρυθμίζει την αναζήτηση χρηστών (μέσω Username) και την προσθήκη νέου φίλου.
    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        //Προετοιμασία UI
        View view = inflater.inflate(R.layout.fragment_friends, container, false);

        etSearchUsername = view.findViewById(R.id.etSearchUsername);
        btnSearch = view.findViewById(R.id.btnSearch);
        btnAddFriend = view.findViewById(R.id.btnAddFriend);
        resultCard = view.findViewById(R.id.resultCard);
        tvResultUsername = view.findViewById(R.id.tvResultUsername);
        friendsListContainer = view.findViewById(R.id.friendsListContainer);

        db = FirebaseFirestore.getInstance();
        mAuth = FirebaseAuth.getInstance();

        // Λογική Αναζήτησης (Κουμπί Search)
        btnSearch.setOnClickListener(v -> {
            String searchQuery = etSearchUsername.getText().toString().trim();
            if (searchQuery.isEmpty()) return;

            resultCard.setVisibility(View.GONE);// Κρύβει την παλιά κάρτα αποτελεσμάτων

            // Ψάχνει στον φάκελο users του Firebase αν υπάρχει αυτό το username
            db.collection("users").whereEqualTo("username", searchQuery).get()
                    .addOnSuccessListener(queryDocumentSnapshots -> {
                        if (!queryDocumentSnapshots.isEmpty()) {
                            // Αν το βρει, εμφανίζει την κάρτα με το αποτέλεσμα
                            for (QueryDocumentSnapshot document : queryDocumentSnapshots) {
                                currentSearchedUserId = document.getId();
                                currentSearchedUsername = document.getString("username");
                                tvResultUsername.setText(currentSearchedUsername);
                                resultCard.setVisibility(View.VISIBLE);
                                break;// Σταματάει στο πρώτο αποτέλεσμα
                            }
                        } else {
                            Toast.makeText(requireContext(), "Δεν βρέθηκε τέτοιο Username!", Toast.LENGTH_SHORT).show();
                        }
                    });
        });

        //Λογική Προσθήκης Φίλου
        btnAddFriend.setOnClickListener(v -> {
            if (mAuth.getCurrentUser() == null || currentSearchedUserId.isEmpty()) return;
            String myUserId = mAuth.getCurrentUser().getUid();

            //Ασφάλεια: Δεν μπορείς να προσθέσεις τον εαυτό σου
            if (myUserId.equals(currentSearchedUserId)) {
                Toast.makeText(requireContext(), "Δεν μπορείς να προσθέσεις τον εαυτό σου!", Toast.LENGTH_SHORT).show();
                return;
            }

            //Αποθήκευση του φίλου στον υποφάκελο "friends" του χρήστη
            Map<String, Object> friendData = new HashMap<>();
            friendData.put("username", currentSearchedUsername);
            friendData.put("friendId", currentSearchedUserId);

            db.collection("users").document(myUserId).collection("friends")
                    .document(currentSearchedUserId).set(friendData)
                    .addOnSuccessListener(aVoid -> {
                        Toast.makeText(requireContext(), "Προστέθηκε στους φίλους!", Toast.LENGTH_SHORT).show();
                        resultCard.setVisibility(View.GONE);
                        loadMyFriends();// Ανανεώνει τη λίστα
                    });
        });
        // Φορτώνει τους φίλους που έχει ήδη ο χρήστης
        loadMyFriends();

        return view;
    }

    //Κατεβάζει τη λίστα με τους φίλους σου, διαβάζει τα σημερινά τους βήματα,τους ταξινομεί (leaderboard) και τους εμφανίζει!
    private void loadMyFriends() {
        if (mAuth.getCurrentUser() == null) return;
        String myUserId = mAuth.getCurrentUser().getUid();

        //Παίρνει όλους τους φίλους που έχει προσθέσει ο χρήστης
        db.collection("users").document(myUserId).collection("friends").get()
                .addOnSuccessListener(queryDocumentSnapshots -> {
                    int totalFriends = queryDocumentSnapshots.size();

                    if (totalFriends == 0) {
                        friendsListContainer.removeAllViews();
                        return;
                    }

                    List<FriendItem> friendItemsList = new ArrayList<>();
                    AtomicInteger loadedCount = new AtomicInteger(0);// Μετρητής για ασύγχρονα κατεβάσματα

                    //Για κάθε φίλο, πηγαίνει στο πραγματικό του προφίλ για να δει τα βήματά του
                    for (QueryDocumentSnapshot document : queryDocumentSnapshots) {
                        String friendId = document.getString("friendId");
                        String friendName = document.getString("username");

                        db.collection("users").document(friendId).get()
                                .addOnSuccessListener(friendDoc -> {
                                    long steps = 0;
                                    if (friendDoc.exists()) {
                                        Long todaySteps = friendDoc.getLong("today_steps");
                                        steps = (todaySteps != null) ? todaySteps : 0;
                                    }

                                    friendItemsList.add(new FriendItem(friendName, steps, friendId));

                                    //Μόλις κατέβουν όλοι οι φίλοι (loadedCount == totalFriends)
                                    if (loadedCount.incrementAndGet() == totalFriends) {
                                        //Ταξινόμηση (Sort) από τον πρώτο σε βήματα (b) προς τον τελευταίο (a)
                                        Collections.sort(friendItemsList, (a, b) -> Long.compare(b.steps, a.steps));
                                        friendsListContainer.removeAllViews();
                                        //Δημιουργία των καρτών κατάταξης (Leaderboard)
                                        int rank = 1;
                                        for (FriendItem item : friendItemsList) {
                                            //Περνάμε 4 arguments, μαζί με το item.friendId
                                            addMiniPlanetCard(item.name, item.steps, rank, item.friendId);
                                            rank++;
                                        }
                                    }
                                });
                    }
                });
    }

    //Δημιουργεί δυναμικά την οπτική κάρτα για κάθε φίλο. Βάζει εικονίδια
    private void addMiniPlanetCard(String username, long steps, int rank, String friendId) {
        // Ελέγχουμε αν είμαστε σε Dark Mode
        boolean isDark = requireContext().getSharedPreferences("VimaVersePrefs", android.content.Context.MODE_PRIVATE)
                .getBoolean("darkMode", false);

        //Δημιουργία του εξωτερικού Layout της κάρτας
        LinearLayout card = new LinearLayout(requireContext());
        card.setOrientation(LinearLayout.HORIZONTAL);
        card.setPadding(40, 40, 40, 40);
        card.setGravity(android.view.Gravity.CENTER_VERTICAL);

        android.graphics.drawable.GradientDrawable gd = new android.graphics.drawable.GradientDrawable();
        gd.setColor(isDark ? android.graphics.Color.parseColor("#2C2C2C") : android.graphics.Color.WHITE);
        gd.setCornerRadius(40f);
        card.setBackground(gd);

        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        params.setMargins(0, 0, 0, 24);
        card.setLayoutParams(params);

        //Εικονίδιο κατάταξης (Χρυσό, Ασημένιο, Χάλκινο μετάλλιο)
        String rankIcon = "🪐";
        if (rank == 1) rankIcon = "🥇";
        else if (rank == 2) rankIcon = "🥈";
        else if (rank == 3) rankIcon = "🥉";
        else rankIcon = rank + ".";

        //Δημιουργία κειμένων (Όνομα, Βήματα)
        LinearLayout textLayout = new LinearLayout(requireContext());
        textLayout.setOrientation(LinearLayout.VERTICAL);
        textLayout.setLayoutParams(new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));

        TextView tvName = new TextView(requireContext());
        tvName.setText(rankIcon + "  " + username);
        // Αν είναι Dark Mode βάζουμε λευκά γράμματα, αλλιώς σκούρα
        tvName.setTextColor(isDark ? android.graphics.Color.WHITE : android.graphics.Color.parseColor("#2B2D42"));
        tvName.setTextSize(18f);
        tvName.setTypeface(null, android.graphics.Typeface.BOLD);

        TextView tvSteps = new TextView(requireContext());
        tvSteps.setText(steps + " " + getString(R.string.friend_steps));        tvSteps.setTextColor(android.graphics.Color.parseColor("#5E6AD2"));
        tvSteps.setTextSize(16f);
        tvSteps.setTypeface(null, android.graphics.Typeface.BOLD);

        textLayout.addView(tvName);
        textLayout.addView(tvSteps);

        //Δημιουργία Κουμπιού Επίσκεψης (Πύραυλος 🚀)
        Button btnVisit = new Button(requireContext());
        btnVisit.setText("🚀");
        btnVisit.setTextSize(20f);
        btnVisit.setBackgroundColor(android.graphics.Color.TRANSPARENT);
        //Όταν πατηθεί, στέλνει μήνυμα στο MainActivity να ξεκινήσει τη Λειτουργία Επίσκεψης (Visit Mode)
        btnVisit.setOnClickListener(v -> {
            if (getActivity() instanceof MainActivity) {
                ((MainActivity) getActivity()).visitFriendPlanet(friendId, username);
            }
        });

        //Ενώνει τα πάντα και τα προσθέτει στη λίστα
        card.addView(textLayout);
        card.addView(btnVisit);
        friendsListContainer.addView(card);
    }
}