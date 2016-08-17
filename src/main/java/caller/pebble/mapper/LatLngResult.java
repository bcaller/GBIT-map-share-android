/**
 * GBIT Map Share for Pebble
 * Copyright (c) Ben Caller 2016
 */

package caller.pebble.mapper;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class LatLngResult {
    private static final boolean LOG = false;
    // Don't bother converting to float only to re-serialize as String
    private String latitude;
    private String longitude;

    public LatLngResult(String latitude, String longitude) {
        this.latitude = latitude;
        this.longitude = longitude;
    }

    public String getLatitude() {
        return latitude;
    }

    public String getLongitude() {
        return longitude;
    }

    @Override
    public String toString() {
        return String.format("%s,%s", this.latitude, this.longitude);
    }

    public static LatLngResult getLatLngFromUrl(String googleMapsUrl) {
        try {
            HttpURLConnection conn;
            URL url = new URL(googleMapsUrl);
            int redirectsRemaining = 7;
            while(true) {
                // Redirects between HTTP and HTTPS are not auto-followed anyway
                HttpURLConnection.setFollowRedirects(false);
                conn = (HttpURLConnection) url.openConnection();
                conn.setInstanceFollowRedirects(false);

                final int responseCode = conn.getResponseCode();
                if(LOG)System.out.println("Response " + responseCode);
                if (responseCode == HttpURLConnection.HTTP_MOVED_PERM
                        || responseCode == HttpURLConnection.HTTP_MOVED_TEMP
                        || responseCode == HttpURLConnection.HTTP_SEE_OTHER) {
                    final String location = conn.getHeaderField("Location");
                    if(redirectsRemaining-- == 0) {
                        if(LOG)System.out.println("Redirect loop caught :(");
                        return null;
                    }
                    if(LOG)System.out.println("Redirect to " + location);
                    url = new URL(location);
                } else
                    break;
            }

            BufferedReader reader = new BufferedReader(new InputStreamReader(conn.getInputStream()));
            // Horrible scraping of latlng. When it next stops working use a proper library.
            Pattern pattern = Pattern.compile("cacheResponse[\\(\\[\\d\\.]+,([-\\.\\d]+),([-\\.\\d]+)]");
            String line;
            while ((line = reader.readLine()) != null)
            {
                final Matcher matcher = pattern.matcher(line);
                if(matcher.find()) {
                    return new LatLngResult(matcher.group(2), matcher.group(1));
                }
            }

        } catch (IOException e) {
            System.err.println(e.getMessage());
        }
        if(LOG)System.out.println("No latlng found for " + googleMapsUrl);
        return null;
    }
}
