package com.example.vimaverse;

import android.Manifest;
import android.content.ClipData;
import android.content.ClipDescription;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.os.Bundle;
import android.view.DragEvent;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.RelativeLayout;
import android.widget.TextView;
import android.widget.Toast;
import android.graphics.PointF;
import android.os.Build;
import android.widget.Button;
import java.util.Map;
import java.util.HashMap;
import java.util.List;
import java.util.ArrayList;
import androidx.annotation.Keep;
import androidx.activity.EdgeToEdge;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.fragment.app.Fragment;

import com.google.firebase.firestore.FieldValue;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.android.material.bottomnavigation.BottomNavigationView;
import com.google.android.material.floatingactionbutton.FloatingActionButton;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.QueryDocumentSnapshot;

import com.unity3d.player.UnityPlayerForActivityOrService;
import com.unity3d.player.IUnityPlayerLifecycleEvents;

public class MainActivity extends AppCompatActivity {

    // Δήλωση όλων των μεταβλητών που θα χρειαστούμε
    private TextView tvStepCount, tvTrash;
    private LinearLayout inventoryList,  storageRoomContainer;
    private RelativeLayout planetArea;
    private FirebaseAuth mAuth;
    private FirebaseFirestore db;
    private static final int ACTIVITY_RECOGNITION_REQUEST_CODE = 100;
    private List<PointF> occupiedPositions = new ArrayList<>();
    private UnityPlayerForActivityOrService mUnityPlayer;

    // Ξεκινάει το Unity, φορτώνει το UI, ελέγχει τα δικαιώματα (βήματα) και ρυθμίζει τα μενού και τα κουμπιά.
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        //Προετοιμασία οθόνης
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_main);

        //Λέμε στο Unity ποια είναι η τρέχουσα οθόνη (για να στέλνει μηνύματα εδώ)
        com.unity3d.player.UnityPlayer.currentActivity = this;

        //Αρχικοποίηση Firebase
        mAuth = FirebaseAuth.getInstance();
        db = FirebaseFirestore.getInstance();

        //Ρύθμιση αποστάσεων για να μην κρύβονται στοιχεία πίσω από την κάμερα/μπαταρία του κινητού
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main), (v, insets) -> {
            Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom);
            return insets;
        });

        // Σύνδεση των στοιχείων UI με τον κώδικα
        tvStepCount = findViewById(R.id.tvStepCount);
        inventoryList = findViewById(R.id.inventoryList);
        planetArea = findViewById(R.id.planetArea);
        tvTrash = findViewById(R.id.tvTrash);

        storageRoomContainer = findViewById(R.id.storageRoomContainer);
        storageRoomContainer.setVisibility(View.GONE);

        //Δημιουργία και ενσωμάτωση του 3D παραθύρου του Unity
        mUnityPlayer = new UnityPlayerForActivityOrService(this, new IUnityPlayerLifecycleEvents() {
            @Override public void onUnityPlayerUnloaded() { }
            @Override public void onUnityPlayerQuitted() { }
        });

        RelativeLayout.LayoutParams params = new RelativeLayout.LayoutParams(
                RelativeLayout.LayoutParams.MATCH_PARENT,
                RelativeLayout.LayoutParams.MATCH_PARENT
        );
        // Βάζουμε το Unity στο φόντο
        planetArea.addView(mUnityPlayer.getFrameLayout(), 0, params);
        mUnityPlayer.getFrameLayout().requestFocus();

        //Φόρτωση δεδομένων και ρυθμίσεις Drag & Drop
        loadInventory();
        loadPlacedItems();
        setupPlanetDropZone();
        setupTrashDropZone();

        //Ρύθμιση του κάτω μενού (Bottom Navigation)
        BottomNavigationView bottomNav = findViewById(R.id.bottom_navigation);
        if(bottomNav.getMenu().size() > 2) {
            bottomNav.getMenu().getItem(2).setEnabled(false);// Κλείδωμα του κενού κουμπιού στη μέση
        }

        bottomNav.setOnItemSelectedListener(item -> {
            int itemId = item.getItemId();
            if (itemId == R.id.nav_calendar) {
                loadFragment(new CalendarFragment());
                return true;
            } else if (itemId == R.id.nav_friends) {
                loadFragment(new FriendsFragment());
                return true;
            } else if (itemId == R.id.nav_shop) {
                loadFragment(new ShopFragment());
                return true;
            } else if (itemId == R.id.nav_settings) {
                loadFragment(new SettingsFragment());
                return true;
            }
            return false;
        });
        //Ρύθμιση του στρογγυλού κουμπιού (Πυξίδα/Πλανήτης)
        FloatingActionButton fab = findViewById(R.id.fabPlanet);
        fab.setOnClickListener(v -> {
            findViewById(R.id.fragment_container).setVisibility(View.GONE);// Κρύβει τα άλλα μενού
            if (storageRoomContainer.getVisibility() == View.VISIBLE) {
                storageRoomContainer.setVisibility(View.GONE);// Κλείνει την αποθήκη αν είναι ανοιχτή
            } else {
                storageRoomContainer.setVisibility(View.VISIBLE);// Την ανοίγει αν είναι κλειστή
            }
        });

        //Ζωντανή σύνδεση με το Firebase για να βλέπουμε τα σημερινά βήματα
        if (mAuth.getCurrentUser() != null) {
            String currentUserId = mAuth.getCurrentUser().getUid();
            db.collection("users").document(currentUserId)
                    .addSnapshotListener((documentSnapshot, error) -> {
                        if (documentSnapshot != null && documentSnapshot.exists()) {

                            Long todaySteps = documentSnapshot.getLong("today_steps");
                            if (todaySteps != null && tvStepCount != null) {
                                tvStepCount.setText(String.valueOf(todaySteps));
                            } else if (tvStepCount != null) {
                                tvStepCount.setText("0");
                            }
                        }
                    });
        }

        //Ζητάμε δικαιώματα και ξεκινάμε την υπηρεσία καταγραφής βημάτων
        checkPermission();
        Intent serviceIntent = new Intent(this, StepService.class);
        ContextCompat.startForegroundService(this, serviceIntent);

    }

    // Σταματάει το Unity όταν η εφαρμογή μπαίνει στο παρασκήνιο
     @Override
    protected void onPause() {
        super.onPause();
        if (mUnityPlayer != null) mUnityPlayer.pause();
    }

    // "Ξυπνάει" το Unity όταν η εφαρμογή επιστρέφει στην οθόνη
    @Override
    protected void onResume() {
        super.onResume();
        if (mUnityPlayer != null) mUnityPlayer.resume();
    }

    // Ενημερώνει το Unity αν ο χρήστης αλληλεπιδρά με την εφαρμογή ή αν άνοιξε κάποιο άλλο μενού/ειδοποίηση από πάνω
    @Override
    public void onWindowFocusChanged(boolean hasFocus) {
        super.onWindowFocusChanged(hasFocus);
        if (mUnityPlayer != null) mUnityPlayer.windowFocusChanged(hasFocus);
    }

    // Κλείνει οριστικά το Unity και αδειάζει τη μνήμη όταν τερματιστεί η εφαρμογή
    @Override
    protected void onDestroy() {
        if (mUnityPlayer != null) mUnityPlayer.destroy();
        super.onDestroy();
    }

    // Ζητάει από τον χρήστη την άδεια να μετράει τα βήματά του και να στέλνει ειδοποιήσεις, ανάλογα με την έκδοση του Android.
    private void checkPermission() {

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACTIVITY_RECOGNITION) != PackageManager.PERMISSION_GRANTED ||
                    ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {

                ActivityCompat.requestPermissions(this, new String[]{
                        Manifest.permission.ACTIVITY_RECOGNITION,
                        Manifest.permission.POST_NOTIFICATIONS
                }, ACTIVITY_RECOGNITION_REQUEST_CODE);
            }
        } else {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACTIVITY_RECOGNITION) != PackageManager.PERMISSION_GRANTED) {
                ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.ACTIVITY_RECOGNITION}, ACTIVITY_RECOGNITION_REQUEST_CODE);
            }
        }
    }

    // Κατεβάζει από το Firebase όλα τα αντικείμενα που έχει αγοράσει ο χρήστης και ΔΕΝ τα έχει βάλει ακόμα στον πλανήτη (isPlaced = false).
    private void loadInventory() {
        if (mAuth.getCurrentUser() == null) return;
        String userId = mAuth.getCurrentUser().getUid();

        db.collection("users").document(userId).collection("inventory")
                // Φέρε μόνο όσα είναι στην αποθήκη
                .whereEqualTo("isPlaced", false)
                .addSnapshotListener((queryDocumentSnapshots, error) -> {
                    if (error != null || queryDocumentSnapshots == null) return;
                    // Καθαρίζει την παλιά λίστα
                    inventoryList.removeAllViews();
                    for (QueryDocumentSnapshot document : queryDocumentSnapshots) {
                        String icon = document.getString("icon"); // Για τώρα κρατάμε το emoji για να φαίνεται κάτι
                        String prefabName = document.getString("prefabName"); // Το νέο μας Κλειδί!
                        String itemId = document.getId();
                        if (prefabName != null) addDraggableItemToUI(icon, prefabName, itemId, inventoryList, false);
                    }
                });
    }
    //Φτιάχνει ένα γραφικό (emoji/εικονίδιο) για την αποθήκη και του δίνει την ικανότητα να μπορεί ο χρήστης να το σέρνει (Drag).
    private void addDraggableItemToUI(String icon, String prefabName, String itemId, View parent, boolean isAlreadyPlaced) {
        TextView tvItem = new TextView(this);
        if (icon != null) tvItem.setText(icon);
        tvItem.setTextSize(36f);

        // Κρύβουμε ΌΛΑ τα δεδομένα στο Tag, χωρισμένα με κόμμα (Κατηγορία, ID, PrefabName)
        tvItem.setTag("INV," + itemId + "," + prefabName);

        if (!isAlreadyPlaced) {
            tvItem.setPadding(16, 16, 16, 16);
            ((LinearLayout) parent).addView(tvItem);
        }
        // Όταν πατηθεί παρατεταμένα, ξεκινάει η "σκιά" (DragShadow) για το σέρσιμο
        tvItem.setOnLongClickListener(v -> {
            ClipData.Item item = new ClipData.Item((CharSequence) v.getTag());
            ClipData dragData = new ClipData((CharSequence) v.getTag(), new String[]{ClipDescription.MIMETYPE_TEXT_PLAIN}, item);
            View.DragShadowBuilder myShadow = new View.DragShadowBuilder(tvItem);
            v.startDragAndDrop(dragData, myShadow, tvItem, 0);
            return true;
        });
    }
    //Ακούει (Listener) πότε ο χρήστης αφήνει (Drop) ένα αντικείμενο πάνω στον πλανήτη.
    private void setupPlanetDropZone() {
        planetArea.setOnDragListener((v, event) -> {
            switch (event.getAction()) {
                case DragEvent.ACTION_DRAG_STARTED:
                    tvTrash.setVisibility(View.VISIBLE);// Εμφανίζει τον κάδο
                    return true;

                case DragEvent.ACTION_DRAG_ENDED:
                    tvTrash.setVisibility(View.GONE);// Κρύβει τον κάδο
                    if (!event.getResult()) {
                        View draggedViewEnd = (View) event.getLocalState();
                        if (draggedViewEnd != null && draggedViewEnd.getTag() != null) {
                            String tagEnd = (String) draggedViewEnd.getTag();
                            if (tagEnd.startsWith("PLN,")) loadPlacedItems();
                        }
                    }
                    return true;

                case DragEvent.ACTION_DROP:// Όταν το αντικείμενο προσγειωθεί!
                    View draggedView = (View) event.getLocalState();
                    String tag = (String) draggedView.getTag();
                    float dropX = event.getX() - (draggedView.getWidth() / 2f);// Υπολογίζει τις συντεταγμένες
                    float dropY = event.getY() - (draggedView.getHeight() / 2f);
                    // Αν ήρθε από την ΑΠΟΘΗΚΗ (Νέο αντικείμενο)
                    if (tag != null && tag.startsWith("INV,")) {
                        String[] parts = tag.split(",");
                        String inventoryItemId = parts[1];
                        String prefabName = parts[2];
                        placeItemOnFirebase(prefabName, inventoryItemId, dropX, dropY);
                    }
                    // Αν ήρθε από τον ΠΛΑΝΗΤΗ (Μετακίνηση υπάρχοντος αντικειμένου)
                    else if (tag != null && tag.startsWith("PLN,")) {
                        String[] parts = tag.split(",");
                        String placedItemId = parts[1];
                        String inventoryItemId = parts[2];
                        String prefabName = parts[3];

                        db.collection("users").document(mAuth.getCurrentUser().getUid())
                                .collection("placed_items").document(placedItemId)
                                .update("x", dropX, "y", dropY);
                        drawItemOnPlanet(prefabName, placedItemId, inventoryItemId, dropX, dropY);
                    }
                    return true;
            }
            return false;
        });
    }
    //Όταν ένα νέο αντικείμενο μπαίνει στον πλανήτη, ελέγχει αν πέφτει πάνω σε άλλο (Collision) και αν όχι, το σώζει στη βάση δεδομένων.
    private void placeItemOnFirebase(String prefabName, String inventoryItemId, float x, float y) {
        if (db == null || mAuth.getCurrentUser() == null) return;
        String userId = mAuth.getCurrentUser().getUid();
        // Έλεγχος απόστασης για να μην επικαλύπτονται τα δέντρα (Distance Check)
        for (PointF pos : occupiedPositions) {
            double distance = Math.sqrt(Math.pow(pos.x - x, 2) + Math.pow(pos.y - y, 2));
            if (distance < 30) {
                Toast.makeText(this, "Πολύ κοντά σε άλλο αντικείμενο!", Toast.LENGTH_SHORT).show();
                return;
            }
        }
        occupiedPositions.add(new PointF(x, y));

        // Δημιουργία του εγγράφου για τη βάση δεδομένων
        Map<String, Object> itemData = new HashMap<>();
        itemData.put("prefabName", prefabName); // Αποθηκεύουμε το PrefabName στο Firestore
        itemData.put("inventoryItemId", inventoryItemId);
        itemData.put("x", x);
        itemData.put("y", y);
        itemData.put("timestamp", FieldValue.serverTimestamp());
        //Αποθήκευση στα Placed Items και αλλαγή του isPlaced = true
        db.collection("users").document(userId).collection("placed_items")
                .add(itemData)
                .addOnSuccessListener(documentReference -> {
                    drawItemOnPlanet(prefabName, documentReference.getId(), inventoryItemId, x, y);
                    db.collection("users").document(userId)
                            .collection("inventory").document(inventoryItemId)
                            .update("isPlaced", true);
                });
    }
    //Διαχειρίζεται το "σύρσιμο" αντικειμένου προς τον Κάδο Απορριμμάτων.
    private void setupTrashDropZone() {
        tvTrash.setOnDragListener((v, event) -> {
            switch (event.getAction()) {
                case DragEvent.ACTION_DRAG_STARTED: return true;
                case DragEvent.ACTION_DRAG_ENTERED: v.setBackgroundColor(Color.RED); break;
                case DragEvent.ACTION_DRAG_EXITED: v.setBackgroundColor(Color.parseColor("#FF7E67")); break;
                case DragEvent.ACTION_DROP:// Όταν το αφήσει μέσα στον κάδο!
                    View draggedView = (View) event.getLocalState();
                    String tag = (String) draggedView.getTag();
                    String userId = mAuth.getCurrentUser().getUid();

                    if (tag != null && tag.startsWith("PLN,")) {
                        String[] parts = tag.split(",");
                        if (parts.length >= 4) {
                            String placedItemId = parts[1];
                            String inventoryItemId = parts[2];
                            String prefabName = parts[3];

                            // Διαγραφή από Firebase και επιστροφή στην αποθήκη
                            db.collection("users").document(userId).collection("placed_items").document(placedItemId).delete();
                            db.collection("users").document(userId).collection("inventory").document(inventoryItemId).update("isPlaced", false);

                            // Εντολή στο Unity να εξαφανίσει το 3D μοντέλο αμέσως
                            String unityObjectName = placedItemId + "," + inventoryItemId + "," + prefabName;
                            mUnityPlayer.UnitySendMessage("DragAndDropBuilder", "DeleteTree", unityObjectName);

                            Toast.makeText(MainActivity.this, "Το αντικείμενο επέστρεψε στην αποθήκη!", Toast.LENGTH_SHORT).show();
                        }
                    }
                    v.setBackgroundColor(Color.parseColor("#FF7E67"));
                    return true;
            }
            return false;
        });
    }
    //Κατεβάζει από το Firebase όλα τα αντικείμενα που ήδη υπάρχουν στον πλανήτη και τα στέλνει στο Unity για να τα ζωγραφίσει.
    private void loadPlacedItems() {
        if (db == null || mAuth.getCurrentUser() == null) return;
        String userId = mAuth.getCurrentUser().getUid();

        db.collection("users").document(userId).collection("placed_items")
                .get()
                .addOnSuccessListener(queryDocumentSnapshots -> {
                    occupiedPositions.clear();
                    for (DocumentSnapshot doc : queryDocumentSnapshots) {
                        if (doc.exists()) {
                            String prefabName = doc.getString("prefabName"); // Διαβάζουμε το PrefabName
                            String inventoryItemId = doc.getString("inventoryItemId");
                            Double xObj = doc.getDouble("x");
                            Double yObj = doc.getDouble("y");


                            if (prefabName == null) continue;// Ασφάλεια για παλιά δεδομένα

                            if (inventoryItemId != null && xObj != null && yObj != null) {
                                float x = xObj.floatValue();
                                float y = yObj.floatValue();
                                occupiedPositions.add(new PointF(x, y));// Ενημερώνει τη λίστα με τις "πιασμένες" θέσεις
                                drawItemOnPlanet(prefabName, doc.getId(), inventoryItemId, x, y);
                            }
                        }
                    }
                });
    }

    //Επικοινωνεί με το Unity στέλνοντας μήνυμα να δημιουργήσει ένα 3D μοντέλο.
    private void drawItemOnPlanet(String prefabName, String placedItemId, String inventoryItemId, float x, float y) {
        if (mUnityPlayer != null) {
            // Φτιάχνει ένα τεράστιο κείμενο-string που περιέχει όλες τις πληροφορίες
            String message = placedItemId + "," + inventoryItemId + "," + prefabName + "," + x + "," + y;
            // Το στέλνει στη μέθοδο Spawn3DTreeFromAndroid της C#
            mUnityPlayer.UnitySendMessage("DragAndDropBuilder", "Spawn3DTreeFromAndroid", message);
        }
    }

    //Όταν ο χρήστης κρατάει πατημένο (Long Press) ένα 3D δέντρο, το Unity στέλνειτα στοιχεία εδώ για να ξεκινήσει το Android το σέρσιμο (Drag).
    @Keep
    @SuppressWarnings("unused")
     public void onUnityItemPickedUp(String itemData) {
        runOnUiThread(() -> {
            String[] parts = itemData.split(",");
            if (parts.length < 3) return;

            String placedItemId = parts[0];
            String inventoryItemId = parts[1];
            String prefabName = parts[2];

            // Φτιάχνουμε ένα ΑΟΡΑΤΟ 1x1 pixel view! Αφού το Drag είναι 3D, δεν χρειαζόμαστε καν 2D Σκιά!
            View hiddenView = new View(MainActivity.this);
            hiddenView.setTag("PLN," + placedItemId + "," + inventoryItemId + "," + prefabName);

            hiddenView.measure(View.MeasureSpec.makeMeasureSpec(1, View.MeasureSpec.EXACTLY),
                    View.MeasureSpec.makeMeasureSpec(1, View.MeasureSpec.EXACTLY));
            hiddenView.layout(0, 0, 1, 1);

            ClipData.Item item = new ClipData.Item((CharSequence) hiddenView.getTag());
            ClipData dragData = new ClipData((CharSequence) hiddenView.getTag(), new String[]{ClipDescription.MIMETYPE_TEXT_PLAIN}, item);
            View.DragShadowBuilder myShadow = new View.DragShadowBuilder(hiddenView);

            // Ξεκινάει τη διαδικασία Drag στο Android
            planetArea.startDragAndDrop(dragData, myShadow, hiddenView, 0);
        });
    }

    //Εύκολος τρόπος για να αλλάζουμε τα μενού (Κατάστημα, Ρυθμίσεις, Φίλοι).
    private void loadFragment(Fragment fragment) {
        findViewById(R.id.fragment_container).setVisibility(View.VISIBLE);
        getSupportFragmentManager().beginTransaction().replace(R.id.fragment_container, fragment).commit();
    }

    //Κρύβει τα μενού σου, κλειδώνει το Drag & Drop στο Unity, και κατεβάζει τα αντικείμενα του Φίλου που επισκέπτεσαι!
    public void visitFriendPlanet(String friendId, String friendName) {
        // Αλλάζουμε το UI: Κρύβουμε τα μενού
        findViewById(R.id.bottomAppBar).setVisibility(View.GONE);
        findViewById(R.id.fabPlanet).setVisibility(View.GONE);
        findViewById(R.id.fragment_container).setVisibility(View.GONE);
        findViewById(R.id.storageRoomContainer).setVisibility(View.GONE);

        // Εντοπίζουμε και ρυθμίζουμε το Κουμπί Επιστροφής με τη σωστή γλώσσα
        Button btnReturnHome = findViewById(R.id.btnReturnHome);
        if (btnReturnHome != null) {
            android.content.SharedPreferences prefs = getSharedPreferences("VimaVersePrefs", android.content.Context.MODE_PRIVATE);
            String lang = prefs.getString("appLanguage", "el");
            android.content.res.Configuration conf = new android.content.res.Configuration(getResources().getConfiguration());
            conf.setLocale(new java.util.Locale(lang));
            android.content.Context localizedContext = createConfigurationContext(conf);

            btnReturnHome.setText(localizedContext.getString(R.string.return_from) + " " + friendName.toUpperCase());
            btnReturnHome.setVisibility(View.VISIBLE);

            // Ρύθμιση της λειτουργίας του κουμπιού
            btnReturnHome.setOnClickListener(v -> returnToMyPlanet());
        }

        // Εντολές στο Unity: Κλείδωσε τον πλανήτη και καθάρισέ τον
        if (mUnityPlayer != null) {
            mUnityPlayer.UnitySendMessage("DragAndDropBuilder", "SetVisitMode", "true");
            mUnityPlayer.UnitySendMessage("DragAndDropBuilder", "ClearPlanet", "");
        }

        // Διαβάζουμε τα αντικείμενα του ΦΙΛΟΥ από το Firebase
        db.collection("users").document(friendId).collection("placed_items")
                .get()
                .addOnSuccessListener(queryDocumentSnapshots -> {
                    for (DocumentSnapshot doc : queryDocumentSnapshots) {
                        if (doc.exists()) {
                            String prefabName = doc.getString("prefabName");
                            String inventoryItemId = doc.getString("inventoryItemId");
                            Double xObj = doc.getDouble("x");
                            Double yObj = doc.getDouble("y");

                            if (prefabName != null && inventoryItemId != null && xObj != null && yObj != null) {
                                drawItemOnPlanet(prefabName, doc.getId(), inventoryItemId, xObj.floatValue(), yObj.floatValue());
                            }
                        }
                    }
                });
    }
    //Επιστρέφει την οθόνη στην κανονική της κατάσταση (στον πλανήτη σου).
    private void returnToMyPlanet() {
        // Επαναφορά UI
        findViewById(R.id.bottomAppBar).setVisibility(View.VISIBLE);
        findViewById(R.id.fabPlanet).setVisibility(View.VISIBLE);
        findViewById(R.id.btnReturnHome).setVisibility(View.GONE);

        // Ξεκλείδωμα και Καθαρισμός Unity
        if (mUnityPlayer != null) {
            mUnityPlayer.UnitySendMessage("DragAndDropBuilder", "SetVisitMode", "false");
            mUnityPlayer.UnitySendMessage("DragAndDropBuilder", "ClearPlanet", "");
        }

        // Ξαναφορτώνουμε τα δικά μας αντικείμενα
        loadPlacedItems();
    }
    public void forceMenuColorUpdate(boolean isDark) {
        // Αλλαγή στο Μενού (BottomAppBar)
        com.google.android.material.bottomappbar.BottomAppBar bottomAppBar = findViewById(R.id.bottomAppBar);
        if (bottomAppBar != null) {
            int menuColor = isDark ? Color.parseColor("#1A1D29") : Color.WHITE;
            bottomAppBar.setBackgroundTintList(android.content.res.ColorStateList.valueOf(menuColor));
        }

        // Αλλαγή στο Φόντο (CoordinatorLayout)
        View mainLayout = findViewById(R.id.main);
        if (mainLayout != null) {
            int bgColor = isDark ? Color.parseColor("#121212") : Color.parseColor("#F5F5F5");
            mainLayout.setBackgroundColor(bgColor);
        }

        // Αλλαγή στο Κουμπί του Πλανήτη (FAB)
        com.google.android.material.floatingactionbutton.FloatingActionButton fab = findViewById(R.id.fabPlanet);
        if (fab != null) {
            int fabBgColor = isDark ? Color.parseColor("#5E6AD2") : Color.parseColor("#5E6AD2");
            fab.setBackgroundTintList(android.content.res.ColorStateList.valueOf(fabBgColor));

            int fabIconColor = isDark ? Color.WHITE : Color.WHITE;
            fab.setImageTintList(android.content.res.ColorStateList.valueOf(fabIconColor));
        }
    }
    //Αλλάζει ζωντανά τη γλώσσα (Ελληνικά, Αγγλικά, Ιταλικά) στα κείμενα της κεντρικής οθόνης όταν ο χρήστης την αλλάζει από τις ρυθμίσεις.
    public void updateLanguageTexts() {
        // Διαβάζουμε ποια γλώσσα έχει επιλέξει ο χρήστης
        android.content.SharedPreferences prefs = getSharedPreferences("VimaVersePrefs", android.content.Context.MODE_PRIVATE);
        String lang = prefs.getString("appLanguage", "el");

        // Φτιάχνουμε ένα προσωρινό "μεταφραστή" (Context) για αυτήν ακριβώς τη γλώσσα
        android.content.res.Configuration conf = new android.content.res.Configuration(getResources().getConfiguration());
        conf.setLocale(new java.util.Locale(lang));
        android.content.Context localizedContext = createConfigurationContext(conf);

        // Εφαρμόζουμε τις λέξεις διαβάζοντας απευθείας από τον μεταφραστή (localizedContext)
        TextView tvStorage = findViewById(R.id.tvStorageLabel);
        if (tvStorage != null) {
            tvStorage.setText(localizedContext.getString(R.string.storage_label));
        }

        TextView tvTrash = findViewById(R.id.tvTrash);
        if (tvTrash != null) {
            tvTrash.setText(localizedContext.getString(R.string.trash_button));
        }
    }
}