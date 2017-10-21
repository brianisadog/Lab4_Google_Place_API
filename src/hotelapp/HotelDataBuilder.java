package hotelapp;

import concurrent.WorkQueue;
import org.json.simple.JSONArray;
import org.json.simple.JSONObject;
import org.json.simple.parser.JSONParser;
import org.json.simple.parser.ParseException;

import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.IOException;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;

/** Class HotelDataBuilder. Loads hotel info from input files to ThreadSafeHotelData (using multithreading). */
public class HotelDataBuilder {
    private ThreadSafeHotelData hdata; // the "big" ThreadSafeHotelData that will contain all hotel and reviews info
    private WorkQueue wq;
    private volatile int numTasks;

    /** Constructor for class HotelDataBuilder.
     *  @param data
     */
    public HotelDataBuilder(ThreadSafeHotelData data) {
        hdata = data;
        wq = new WorkQueue(1);
        numTasks = 0;
    }

    /** Constructor for class HotelDataBuilder that takes ThreadSafeHotelData and
     * the number of threads to create as a parameter.
     * @param data
     * @param numThreads
     */
    public HotelDataBuilder(ThreadSafeHotelData data, int numThreads) {
        hdata = data;
        wq = new WorkQueue(numThreads);
        numTasks = 0;
    }


    /**
     * Read the json file with information about the hotels and load it into the
     * appropriate data structure(s).
     * @param jsonFilename
     */
    public void loadHotelInfo(String jsonFilename) {
        JSONParser parser = new JSONParser();

        try {
            JSONObject obj = (JSONObject)parser.parse(new FileReader(jsonFilename));
            JSONArray arr = (JSONArray)obj.get("sr");
            for (JSONObject res : (Iterable<JSONObject>) arr) {
                JSONObject ll = (JSONObject) res.get("ll"); //ll is another json object

                hdata.addHotel(res.get("id").toString(), res.get("f").toString()
                        , res.get("ci").toString(), res.get("pr").toString(), res.get("ad").toString()
                        , Double.parseDouble(ll.get("lat").toString()), Double.parseDouble(ll.get("lng").toString()));
            }
        }
        catch  (FileNotFoundException e) {
            System.out.println("Exception while running the loadHotelInfo: Could not find file: " + jsonFilename);
        }
        catch (ParseException e) {
            System.out.println("Exception while running the loadHotelInfo: Can not parse a given json file.");
        }
        catch (IOException e) {
            System.out.println("Exception while running the loadHotelInfo: General IO Exception in readJSON");
        }
    }

    /** Loads reviews from json files. Recursively processes subfolders.
     *  Each json file with reviews should be processed concurrently (you need to create a new runnable job for each
     *  json file that you encounter)
     *
     *  Submit the Runnable inner class to the ExecutorService variable, so that one of the threads from the pool of
     *  threads will start executing it.
     *
     *  @param dir
     */
    public void loadReviews(Path dir) {
        try (DirectoryStream<Path> filesList = Files.newDirectoryStream(dir)) {
            for (Path file: filesList) {
                if (file.toString().contains(".json")) {
                    try {
                        wq.execute(new LoadPerReview(file.toString()));
                    }
                    catch (Exception e) {
                        System.out.println("Exception while running the loadReviews: " + e);
                    }
                }
                else {
                    loadReviews(file); //recursively find all .json files
                }
            }
        }
        catch (IOException e) {
            System.out.println("Exception while running the loadReviews: " + e);
        }
    }

    /** Prints all hotel info to the file.
     * 	Calls hdata's printToFile method.
     * 	Shutdown the Executor after we load all the reviews.
     */
    public void printToFile(Path filename) {
        waitUntilFinished();

        try {
            hdata.printToFile(filename);
        } catch (Exception e) {
            System.out.println("Exception while running the printToFile: " + e);
        }
    }

    /**
     * Blocking method that will increase the number of tasks when a task start to run.
     */
    private synchronized void incrementTasks() {
        numTasks++;
        notifyAll();
    }

    /**
     * Blocking method that will decrease the number of tasks when a task finish.
     */
    private synchronized void decrementTasks() {
        numTasks--;
        notifyAll();
    }

    /**
     * Blocking method that will wait until all the tasks done running.
     */
    private synchronized void waitUntilFinished() {
        while(numTasks > 0) {
            try {
                wait();
            }
            catch (InterruptedException e) {
                System.out.println("InterruptedException while running the waitUntilFinished: " + e);
            }
        }
    }

    /**
     * Inner class that implements Runnable for ExecutorService to execute.
     *
     * Run() method allows multi-thread loads reviews from json file simultaneously.
     */
    public class LoadPerReview implements Runnable {
        private String jsonFilename;

        /**
         * Constructor
         * @param jsonFilename
         */
        public LoadPerReview(String jsonFilename) {
            this.jsonFilename = jsonFilename;
            incrementTasks();
        }

        /**
         * To create a local hotel data and load the review into local data. After done the loading,
         * merge local data into big data and set the average rating of this hotel.
         */
        public void run() {
            ThreadSafeHotelData localData = new ThreadSafeHotelData();
            JSONParser parser = new JSONParser();

            try {
                localData.loadHotelInfo("input/hotels.json");
                JSONObject obj = (JSONObject)parser.parse(new FileReader(jsonFilename));

                //get to the review unit
                JSONObject reviewDetails = (JSONObject)obj.get("reviewDetails");
                JSONObject reviewCollection = (JSONObject)reviewDetails.get("reviewCollection");
                JSONArray reviewArray = (JSONArray)reviewCollection.get("review");

                //add each review into Review TreeMap
                for (JSONObject review : (Iterable<JSONObject>) reviewArray) {

                    localData.addReview(review.get("hotelId").toString(), review.get("reviewId").toString()			//String hotelId, String reviewId
                            , Integer.parseInt(review.get("ratingOverall").toString())							//int rating
                            , review.get("title").toString(), review.get("reviewText").toString()				//String reviewTitle, String review
                            , (review.get("isRecommended").toString().toUpperCase().equals("NO") ? false : true)//boolean isRecom
                            , review.get("reviewSubmissionTime").toString()										//String date
                            , review.get("userNickname").toString());											//String username
                }

                //merge local data into big data and set the average rating of this hotel
                hdata.mergeReviewMapAndSetRating(localData);
                decrementTasks();
            }
            catch (FileNotFoundException e) {
                System.out.println("Exception while running the LoadPerReview: Could not find file: " + jsonFilename);
            }
            catch (ParseException e) {
                System.out.println("Exception while running the LoadPerReview: Can not parse a given json file: " + jsonFilename);
            }
            catch (IOException e) {
                System.out.println("Exception while running the LoadPerReview: General IO Exception in readJSON: " + jsonFilename);
            }
        }
    }
}
