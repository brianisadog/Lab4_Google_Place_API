package hotelapp;

/**
 * Custom exception class for entering invalid hotel rating
 */
public class InvalidRatingException extends Exception {

    /**
     * Override the InvalidRatingException method
     * @param message
     *          - Exception message
     */
    public InvalidRatingException(String message) {
        super(message);
        System.out.println("The value of the hotel rating is out of range");
    }

}
