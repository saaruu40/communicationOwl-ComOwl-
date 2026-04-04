import com.github.sarxos.webcam.Webcam;
public class test_webcam {
    public static void main(String[] args) {
        System.out.println("Checking webcam...");
        Webcam webcam = Webcam.getDefault();
        if (webcam != null) {
            System.out.println("Found: " + webcam.getName());
            webcam.open();
            System.out.println("Opened!");
            webcam.close();
            System.out.println("Closed!");
        } else {
            System.out.println("No webcam.");
        }
    }
}
