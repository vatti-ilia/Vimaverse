using UnityEngine;
using System.Collections.Generic;

// Αποθηκεύει τα δεδομένα κάθε 3D αντικειμένου 
public class ShopItem
{
    public string prefabName;
    public GameObject prefab;
    public float yOffset = 0f;
}

public class DragAndDropBuilder : MonoBehaviour
{
    public ShopItem[] availableItems; 
    public Transform planet; 

    public float minPickSize = 0.4f;   // Ελάχιστο μέγεθος για να πατιούνται εύκολα τα μικρά δέντρα
    public float longPressTime = 0.5f; // Πόση ώρα πρέπει να κρατήσεις πατημένο το δάχτυλο (0.5 δευτερόλεπτα)

    private float touchTime = 0f; // Χρονόμετρο πατήματος
    private bool isTouching = false; // Αν ο χρήστης ακουμπάει κάτι
    private GameObject touchedObject = null; // Το αντικείμενο που πατήθηκε
    public bool isVisitMode = false; // Αν είναι true, ο πλανήτης "κλειδώνει" και δεν κουνιέται τίποτα

    // Διαχειρίζεται τα αγγίγματα στην οθόνη και μετράει τον χρόνο πατήματος για να ξεκινήσει το Drag & Drop.
    void Update()
    {
        //  Ο χρήστης μόλις ακούμπησε την οθόνη. Βρίσκουμε τι πατήθηκε και ξεκινάμε το χρονόμετρο.
        if (Input.GetMouseButtonDown(0))
        {
            GameObject picked = PickPlacedObject(Input.mousePosition);
            if (picked != null)
            {
                isTouching = true;
                touchedObject = picked;
                touchTime = 0f;
            }
        }

        // Ο χρήστης κρατάει το δάχτυλο πατημένο.
        if (Input.GetMouseButton(0) && isTouching && !isVisitMode)
        {
            touchTime += Time.deltaTime; // Αυξάνουμε τον χρόνο

            // Αν πέρασε το όριο του παρατεταμένου πατήματος (Long Press)
            if (touchTime > longPressTime && touchedObject != null)
            {
                string itemData = touchedObject.name; // Παίρνουμε τα κρυμμένα IDs του αντικειμένου

                // Στέλνουμε σήμα στο Android (Java) ότι πιάσαμε ένα αντικείμενο για να ξεκινήσει το Drag.
#if UNITY_ANDROID && !UNITY_EDITOR
            using (AndroidJavaClass unityPlayer = new AndroidJavaClass("com.unity3d.player.UnityPlayer"))
            {
                AndroidJavaObject currentActivity = unityPlayer.GetStatic<AndroidJavaObject>("currentActivity");
                currentActivity.Call("onUnityItemPickedUp", itemData);
            }
#endif
                // Διαγράφουμε το 3D μοντέλο γιατί τώρα το έχει αναλάβει το Android!
                Destroy(touchedObject);
                isTouching = false;
                touchedObject = null;
            }
        }

        //  Ο χρήστης σήκωσε το δάχτυλο (σταματάμε να μετράμε).
        if (Input.GetMouseButtonUp(0))
        {
            isTouching = false;
            touchedObject = null;
        }
    }

    // Φτιάχνει ένα αόρατο κουτί (Collider) γύρω από το 3D μοντέλο για να αντιλαμβάνεται τα αγγίγματα.
    void AddPickCollider(GameObject root)
    {
        Renderer[] renderers = root.GetComponentsInChildren<Renderer>();
        if (renderers.Length == 0) return;

        Bounds local = new Bounds();
        bool first = true;
        foreach (Renderer r in renderers)
        {
            Bounds b = r.bounds;
            Vector3 c = b.center, e = b.extents;
            for (int i = 0; i < 8; i++)
            {
                Vector3 corner = c + new Vector3(
                    (i & 1) == 0 ? -e.x : e.x,
                    (i & 2) == 0 ? -e.y : e.y,
                    (i & 4) == 0 ? -e.z : e.z);
                Vector3 lp = root.transform.InverseTransformPoint(corner);
                if (first) { local = new Bounds(lp, Vector3.zero); first = false; }
                else local.Encapsulate(lp);
            }
        }

        // Ρυθμίζουμε να μην είναι ποτέ μικρότερο από το ελάχιστο μέγεθος (minPickSize)
        Vector3 scale = root.transform.lossyScale;
        Vector3 size = local.size;
        size.x = Mathf.Max(size.x, minPickSize / Mathf.Max(0.0001f, Mathf.Abs(scale.x)));
        size.y = Mathf.Max(size.y, minPickSize / Mathf.Max(0.0001f, Mathf.Abs(scale.y)));
        size.z = Mathf.Max(size.z, minPickSize / Mathf.Max(0.0001f, Mathf.Abs(scale.z)));

        BoxCollider box = root.AddComponent<BoxCollider>();
        box.center = local.center;
        box.size = size;
    }

    // Ρίχνει μια νοητή ακτίνα (Ray) για να βρει ποιο 3D αντικείμενο ακούμπησε ο χρήστης.
    GameObject PickPlacedObject(Vector3 screenPos)
    {
        Ray ray = Camera.main.ScreenPointToRay(screenPos);
        RaycastHit[] hits = Physics.RaycastAll(ray, 1000f, ~0, QueryTriggerInteraction.Collide);
        System.Array.Sort(hits, (a, b) => a.distance.CompareTo(b.distance));

        foreach (RaycastHit h in hits)
        {
            // Αγνοεί τον ίδιο τον πλανήτη
            if (h.collider.gameObject == planet.gameObject) return null;

            Transform root = GetPlacedRoot(h.collider.transform);
            if (root != null) return root.gameObject;
        }
        return null;
    }

    // Ελέγχει αν το αντικείμενο που πατήθηκε ανήκει όντως πάνω στον πλανήτη.
    Transform GetPlacedRoot(Transform t)
    {
        while (t != null && t.parent != planet)
        {
            if (t == planet) return null;
            t = t.parent;
        }
        return t;
    }

    // Αυτή η μέθοδος καλείται από το Android όταν ο χρήστης ρίξει (Drop) κάτι στον πλανήτη.
    public void Spawn3DTreeFromAndroid(string message)
    {
        // Χωρίζει το μήνυμα για να βρει τα IDs και τις συντεταγμένες
        string[] parts = message.Split(',');
        if (parts.Length < 5) return;

        string placedItemId = parts[0];
        string inventoryItemId = parts[1];
        string prefabKey = parts[2];

        GameObject prefabToSpawn = null;
        float offset = 0f;

        // Βρίσκει το σωστό 3D γραφικό από τη λίστα (π.χ. "Tree1")
        foreach (ShopItem item in availableItems)
        {
            if (item.prefabName == prefabKey)
            {
                prefabToSpawn = item.prefab;
                offset = item.yOffset;
                break;
            }
        }

        // Αν δεν το βρει, βάζει το πρώτο διαθέσιμο για ασφάλεια
        if (prefabToSpawn == null && availableItems.Length > 0)
        {
            prefabToSpawn = availableItems[0].prefab;
            offset = availableItems[0].yOffset;
        }
        if (prefabToSpawn == null) return;

        Vector3 spawnPos = new Vector3(0, planet.localScale.y / 2f, 0);
        Vector3 surfaceNormal = Vector3.up;

        try
        {
            // Μετατρέπει τις 2D συντεταγμένες του κινητού σε 3D συντεταγμένες
            float androidX = float.Parse(parts[3], System.Globalization.CultureInfo.InvariantCulture);
            float androidY = float.Parse(parts[4], System.Globalization.CultureInfo.InvariantCulture);
            float unityY = Screen.height - androidY;

            Ray ray = Camera.main.ScreenPointToRay(new Vector3(androidX, unityY, 0));

            // Βρίσκει το σημείο της επιφάνειας του πλανήτη
            RaycastHit[] hits = Physics.RaycastAll(ray);
            foreach (RaycastHit h in hits)
            {
                if (h.collider.gameObject == planet.gameObject)
                {
                    spawnPos = h.point;
                    surfaceNormal = h.normal; // Η κατεύθυνση του χώματος
                    break;
                }
            }
        }
        catch (System.Exception e) { Debug.Log(e.Message); }

        // Εμφανίζει το 3D μοντέλο πάνω στον πλανήτη, ευθυγραμμισμένο με την κλίση του εδάφους.
        GameObject newObject = Instantiate(prefabToSpawn, spawnPos, Quaternion.FromToRotation(Vector3.up, surfaceNormal));
        newObject.transform.SetParent(planet);

        // Το βυθίζει στο χώμα (αν χρειάζεται)
        newObject.transform.position += newObject.transform.up * offset;

        // Κρύβει τα IDs στο όνομα του αντικειμένου και του βάζει Collider.
        newObject.name = placedItemId + "," + inventoryItemId + "," + prefabKey;
        AddPickCollider(newObject);
    }

    // Διαγράφει ένα αντικείμενο αν ο χρήστης το έριξε στον Κάδο.
    public void DeleteTree(string treeName)
    {
        GameObject tree = GameObject.Find(treeName);
        if (tree != null)
        {
            Destroy(tree);
        }
    }

    // Ενεργοποιεί/Απενεργοποιεί τη Λειτουργία Επίσκεψης (ώστε να μην πειράζεις τον πλανήτη του φίλου σου).
    public void SetVisitMode(string state)
    {
        isVisitMode = (state == "true");
    }

    // Διαγράφει όλα τα φυτεμένα αντικείμενα (χρήσιμο για να αδειάσει η οθόνη πριν πας σε άλλο πλανήτη).
    public void ClearPlanet(string empty)
    {
        foreach (Transform child in planet)
        {
            // Μόνο τα αντικείμενα που φυτέψαμε εμείς έχουν κόμμα στο όνομά τους
            if (child.name.Contains(","))
            {
                Destroy(child.gameObject);
            }
        }
    }
}