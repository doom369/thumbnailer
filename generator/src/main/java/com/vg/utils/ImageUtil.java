package com.vg.utils;

import javax.imageio.IIOImage;
import javax.imageio.ImageIO;
import javax.imageio.ImageWriteParam;
import javax.imageio.ImageWriter;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Iterator;

public class ImageUtil {

    public static void writeJpeg(BufferedImage image, float quality, OutputStream out) throws IOException {
        Iterator<ImageWriter> imageWritersByFormatName = ImageIO.getImageWritersByFormatName("jpeg");
        ImageWriter writer = imageWritersByFormatName.next();
        ImageWriteParam iwp = writer.getDefaultWriteParam();
        iwp.setCompressionMode(ImageWriteParam.MODE_EXPLICIT);
        iwp.setCompressionQuality(quality);
        writer.setOutput(ImageIO.createImageOutputStream(out));
        IIOImage iioimage = new IIOImage(image, null, null);
        writer.write(null, iioimage, iwp);
        writer.dispose();
    }

    public static void output(BufferedImage image, Path outputPath) throws IOException {
        try (OutputStream out = Files.newOutputStream(outputPath)) {
            ByteArrayOutputStream byteout = new ByteArrayOutputStream();
            writeJpeg(image, 0.5f, byteout);
            out.write(byteout.toByteArray());
        }
    }


}
