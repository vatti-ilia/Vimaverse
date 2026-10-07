using UnityEngine;

public class PlanetRotation : MonoBehaviour
{
    // Ελέγχει την ταχύτητα περιστροφής
    public float rotationSpeed = 5f;

    // Τρέχει διαρκώς. Αν ο χρήστης ακουμπάει την οθόνη (αλλά δεν πατάει δέντρο),περιστρέφει ολόκληρο τον πλανήτη.
    void Update()
    {
        //Ανιχνεύει το άγγιγμα ή το κλικ
        if (Input.GetMouseButton(0))
        {
            // Διαβάζει την κίνηση του δαχτύλου/ποντικιού
            float mouseX = Input.GetAxis("Mouse X");
            float mouseY = Input.GetAxis("Mouse Y");

            // Περιστρέφη τον πλανήτη με βάση την κίνηση
            // Χρησιμοποιούμε Space.World για να περιστρέφεται σωστά προς όλες τις κατευθύνσεις
            transform.Rotate(Vector3.up, -mouseX * rotationSpeed, Space.World);
            transform.Rotate(Vector3.right, mouseY * rotationSpeed, Space.World);
        }
    }
}