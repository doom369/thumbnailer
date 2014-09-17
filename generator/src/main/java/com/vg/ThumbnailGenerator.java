package com.vg;

import java.awt.image.BufferedImage;
import java.nio.file.Path;

public class ThumbnailGenerator {

    public static BufferedImage generate(Path moviePath, int startFn, int count) throws Exception {
        int[] frameNumbers = frameNumbers(startFn, count);

        try (ThumbStream thumbsStream = new ThumbStream(moviePath.toFile())) {
            return thumbsStream.createPreviewThumbnails(frameNumbers).toBufferedImage();
        }
    }


    private static int[] frameNumbers(final int startFn, final int count) {
        int[] frameNumbers = new int[count];
        for (int i = 0; i < count; i++) {
            frameNumbers[i] = startFn + i;
        }
        return frameNumbers;
    }
}
