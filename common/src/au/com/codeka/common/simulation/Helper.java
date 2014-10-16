package au.com.codeka.common.simulation;

public class Helper {
    public static boolean sameEmpire(String keyOne, String keyTwo) {
        if (keyOne == null && keyTwo == null) {
            return true;
        }
        if (keyOne == null || keyTwo == null) {
            return false;
        }
        return keyOne.equals(keyTwo);
    }
}
