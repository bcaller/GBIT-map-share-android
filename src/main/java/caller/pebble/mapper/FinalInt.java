package caller.pebble.mapper;

/**
 * Created by Ben on 21/12/2015.
 */
public class FinalInt {
    private int value = 0;

    public FinalInt(int val) {
        value = val;
    }

    public int getValue() {
        return value;
    }

    public void setValue(int x) {
        value = x;
    }

    public void increment() {
        if(value==Integer.MAX_VALUE)
            value = 1;
        else
            value += 1;
    }
}
