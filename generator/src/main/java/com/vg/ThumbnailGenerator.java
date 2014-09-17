package com.vg;

import java.awt.image.BufferedImage;
import java.nio.file.Path;

public class ThumbnailGenerator {

    public static BufferedImage generate(Path moviePath, int startFn, int count) throws Exception {
        int[] frameNumbers = frameNumbers(startFn, startFn + count, count);
        ThumbStream thumbsStream = ThumbStream.open(moviePath.toFile());

        return thumbsStream.createPreviewThumbnails(frameNumbers).toBufferedImage();
    }


    private static int[] frameNumbers(final int startFn, final int endFn, final int count) {
        double delta = 1. * (endFn - startFn + 1) / count;
        int[] frameNumbers = new int[count];

        for (int i = 0; i < count; i++) {
            frameNumbers[i] = (int) Math.round(startFn + (i * delta));
        }
        frameNumbers[count - 1] = endFn;
        frameNumbers[0] = startFn;
        return frameNumbers;
    }
}
