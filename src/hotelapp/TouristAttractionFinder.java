package hotelapp;

import org.json.simple.JSONArray;
import org.json.simple.JSONObject;
import org.json.simple.parser.JSONParser;
import org.json.simple.parser.ParseException;
import java.io.*;
import javax.net.ssl.SSLSocket;
import javax.net.ssl.SSLSocketFactory;
import java.nio.file.*;
import java.util.*;

/** Class TouristAttractionFinder
 * @author BrianSung
 */
public class TouristAttractionFinder {

    private static final String host = "maps.googleapis.com";
    private static final String path = "/maps/api/place/textsearch/json";
    private static final String KEY = "AIzaSyBcvV5tPZBJSfXxnuhlnzCs8Rs0lXY8N0A";
    private static final int PORT = 443;
    private ThreadSafeHotelData hdata;
    private Map<String, List<TouristAttraction>> taMap;

    /** Constructor for TouristAttractionFinder
     *
     * @param hdata
     */
    public TouristAttractionFinder(ThreadSafeHotelData hdata) {
        this.hdata = hdata;
        this.taMap = new HashMap<>();
    }


    /**
     * Creates a secure socket to communicate with googleapi's server that
     * provides Places API, sends a GET request (to find attractions close to
     * the hotel within a given radius), and gets a response as a string.
     * Removes headers from the response string and parses the remaining json to
     * get Attractions info. Adds attractions to the ThreadSafeHotelData.
     */
    public void fetchAttractions(int radiusInMiles) {
        int meters = (int)(1609.344 * radiusInMiles);
        StringBuffer buf;
        SSLSocket socket = null;
        PrintWriter out = null;
        BufferedReader in = null;

        try {
            for (String hotelId : hdata.getHotels()) {
                SSLSocketFactory factory = (SSLSocketFactory) SSLSocketFactory.getDefault();
                socket = (SSLSocket) factory.createSocket(host, PORT);
                buf = new StringBuffer();
                taMap.put(hotelId, new ArrayList<>());
                String[] hotelDetail = hdata.getHotelDetailById(hotelId);

                // output stream for the secure socket
                out = new PrintWriter(new OutputStreamWriter(socket.getOutputStream()));
                StringBuilder pathAndQuery = new StringBuilder();
                pathAndQuery.append(path);
                pathAndQuery.append("?");
                pathAndQuery.append("query=tourist%20attractions+in+");
                pathAndQuery.append(hotelDetail[0].replaceAll(" ", "%20"));
                pathAndQuery.append("&location=").append(hotelDetail[1]);
                pathAndQuery.append(",").append(hotelDetail[2]);
                pathAndQuery.append("&radius=").append(meters);
                pathAndQuery.append("&language=en");
                pathAndQuery.append("&key=").append(KEY);
                String request = getRequest(host, pathAndQuery.toString());

                out.println(request); // send a request to the server
                out.flush();

                // input stream for the secure socket.
                in = new BufferedReader(new InputStreamReader(socket.getInputStream()));

                // use input stream to read server's response
                String line;
                while ((line = in.readLine()) != null) {
                    buf.append(line);
                }

                // parse into json and put into TreeSet and Hashmap data structure
                String result = buf.toString();
                JSONParser parser = new JSONParser();
                JSONObject jObj = (JSONObject) parser.parse(result.substring(result.indexOf("{"))); // remove header
                JSONArray jArr = (JSONArray) jObj.get("results");
                for (JSONObject obj : (Iterable<JSONObject>) jArr) {
                    String id = obj.get("id").toString();
                    String name = obj.get("name").toString();
                    double rating = (obj.get("rating") == null) ? 0.0 : Double.parseDouble(obj.get("rating").toString());
                    String address = obj.get("formatted_address").toString();

                    taMap.get(hotelId).add(new TouristAttraction(id, name, rating, address));
                }
            }
        }
        catch (ParseException e) {
            System.out.println("Can not parse a given string into Json.");
        }
        catch (IOException e) {
            System.out.println("An IOException occured while writing to the socket stream or reading from the stream: " + e);
        }
        catch (Exception e) {
            System.out.println("Exception happened during fetchAttractions: " + e);
        }
        finally {
            try {
                // close the streams and the socket
                out.close();
                in.close();
                socket.close();
            } catch (IOException e) {
                System.out.println("An IOException occured while trying to close the streams or the socket: " + e);
            }
        }

    }


    /** Print attractions near the hotels to a file.
     * The format is described in the lab description.
     * Format:
     * Attractions near hotelName, id
     * TouristAttraction
     *
     * TouristAttraction
     *
     * TouristAttraction
     *
     * ++++++++++++++++++++
     * Attractions near hotelName, id
     * TouristAttraction
     *
     * TouristAttraction
     *
     * TouristAttraction
     *
     * ++++++++++++++++++++
     *
     * @param filename
     */
    public void printAttractions(Path filename) {
        if (taMap.size() > 0) {

            try (PrintWriter pw = new PrintWriter(filename.toString(), "UTF-8")) {

                for (String hotelId : hdata.getHotels()) {
                    pw.println("Attractions near " + hdata.getHotelDetailById(hotelId)[3] + ", " + hotelId);

                    List<TouristAttraction> result = taMap.get(hotelId);
                    if (result.size() > 0) {
                        for (TouristAttraction ta : result) {
                            pw.println(ta.toString());
                            pw.println();
                        }
                    }

                    pw.println("++++++++++++++++++++");
                }

                pw.flush();
            }
            catch (IOException e) {
                System.out.println("IOException while running the printToFile: " + e);
            }
            catch (Exception e) {
                System.out.println("Exception happened during fetchAttractions: " + e);
            }
        }
    }

    /**
     * A method that creates a GET request for the given host and resource
     * @param host
     * @param pathResourceQuery
     * @return String
     *          - HTTP GET request returned as a string
     */
    private static String getRequest(String host, String pathResourceQuery) {
        String request = "GET " + pathResourceQuery + " HTTP/1.1" + System.lineSeparator() // GET
                // request
                + "Host: " + host + System.lineSeparator() // Host header required for HTTP/1.1
                + "Connection: close" + System.lineSeparator() // make sure the server closes the
                // connection after we fetch one page
                + System.lineSeparator();
        return request;
    }
}
