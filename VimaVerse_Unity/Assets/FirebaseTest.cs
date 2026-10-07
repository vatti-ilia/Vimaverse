using UnityEngine;
using Firebase.Firestore;
using Firebase.Extensions;

public class FirebaseTest : MonoBehaviour
{
    //Δοκιμάζει τη σύνδεση με το Google Firebase και διαβάζει τα νομίσματα ενός συγκεκριμένου χρήστη για τεστ.
    void Start()
    {
        Debug.Log("Γίνεται προσπάθεια σύνδεσης στο Firebase...");

        // Έλεγχος αν οι βιβλιοθήκες του Firebase είναι εγκατεστημένες σωστά
        Firebase.FirebaseApp.CheckAndFixDependenciesAsync().ContinueWithOnMainThread(task =>
        {
            //Αν όλα είναι εντάξει, προχωράμε
            if (task.Result == Firebase.DependencyStatus.Available)
            {
                Debug.Log("Το Firebase είναι έτοιμο! Ψάχνουμε τη βάση...");

                // Σύνδεση με το Firestore Database
                FirebaseFirestore db = FirebaseFirestore.DefaultInstance;

                //Δημιουργία στόχου (Ψάχνουμε τον φάκελο users και ένα συγκεκριμένο ID)
                DocumentReference docRef = db.Collection("users").Document("my_user_id");

                //Ανάκτηση των δεδομένων (Snapshot)
                docRef.GetSnapshotAsync().ContinueWithOnMainThread(task2 =>
                {
                    DocumentSnapshot snapshot = task2.Result;
                    if (snapshot.Exists)
                    {
                        Debug.Log("Επιτυχία! Το έγγραφο βρέθηκε. ID: " + snapshot.Id);

                        // Αν έχεις ήδη ένα πεδίο "coins", μπορείς να δεις την τιμή του:
                        // Debug.Log("Νομίσματα: " + snapshot.GetValue<int>("coins"));
                    }
                    else
                    {
                        Debug.Log("Η σύνδεση είναι τέλεια, αλλά το συγκεκριμένο έγγραφο δεν βρέθηκε στη βάση.");
                    }
                });
            }
            else
            {
                //Αν υπάρχει σφάλμα εγκατάστασης 
                Debug.LogError("Σφάλμα συστήματος Firebase: " + task.Result);
            }
        });
    }
}