package com.vg.utils;

import java.io.IOException;

public class SystemUtil {

    public static Process spawn(String... args) throws IOException {
        return Runtime.getRuntime().exec(args);
    }

}
