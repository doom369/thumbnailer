package com.vg;

import java.net.URISyntaxException;
import java.nio.file.Path;
import java.nio.file.Paths;

public abstract class BaseTest {

    public static Path resolveTestResource(String name) {
        try {
            return Paths.get(BaseTest.class.getResource(name).toURI());
        }  catch (URISyntaxException e) {
            throw new RuntimeException(e);
        }
    }

}
