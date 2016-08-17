package caller.pebble.mapper;

import org.junit.Before;
import org.junit.Test;
import static org.junit.Assert.*;

/**
 * To work on unit tests, switch the Test Artifact in the Build Variants view.
 */
public class LatLngResultTest {
    @Before
    public void setUp() throws Exception {

    }

    @Test
    public void testToString() throws Exception {
        LatLngResult res = new LatLngResult("-1", "4.321");
        assertEquals("-1,4.321", res.toString());
    }

    @Test
    public void testGetLatLngFromUrl_integration() throws Exception {
        // This is an integration test for Google Maps URLs
        LatLngResult res = LatLngResult.getLatLngFromUrl("https://goo.gl/maps/19in7Rbznww");
        assertNotNull("Google Maps URL didn't work", res);
        assertEquals(res.getLatitude().substring(0, 8), "29.11554");
        assertEquals(res.getLongitude(), "110.52658085");
    }

    @Test
    public void testGetLatLngFromUrl_silly_url() throws Exception {
        //FIXME: Mock
        LatLngResult res = LatLngResult.getLatLngFromUrl("https://httpbin.org/get");
        assertNull(res);
    }

    @Test
    public void testGetLatLngFromUrl_status() throws Exception {
        //FIXME: Mock
        LatLngResult res = LatLngResult.getLatLngFromUrl("https://httpbin.org/status/500");
        assertNull("Google Maps URL didn't work", res);
    }

}