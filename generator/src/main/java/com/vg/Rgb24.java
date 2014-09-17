package com.vg;

import javax.imageio.ImageIO;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.awt.image.DataBufferByte;
import java.io.*;
import java.nio.ByteBuffer;

public class Rgb24 {

    private final int width;
    private final int height;
    private final ByteBuffer buf;

    public Rgb24(int width, int height) {
        this(width, height, ByteBuffer.allocate(width * height * 3));
    }

    public Rgb24(int width, int height, ByteBuffer buf) {
        this.width = width;
        this.height = height;
        this.buf = buf;
    }

    public int getWidth() {
        return width;
    }

    public int getHeight() {
        return height;
    }

    public ByteBuffer getData() {
        return (ByteBuffer) buf.duplicate().clear();
    }

    public static int size(Rectangle bounds) {
        return bounds.width * bounds.height * 3;
    }

    public static int sizeof(int w, int h) {
        return w * h * 3;
    }

    public Rectangle getBounds() {
        return new Rectangle(0, 0, width, height);
    }

    public Dimension getDimension() {
        return new Dimension(width, height);
    }

    public int stride() {
        return width * 3;
    }

    public static Rgb24 fromBufferedImage(BufferedImage bImg) {
        final int w = bImg.getWidth();
        final int h = bImg.getHeight();
        Rgb24 rgbImage = new Rgb24(w, h);
        ByteBuffer dstData = rgbImage.getData();
        if (bImg.getType() == BufferedImage.TYPE_BYTE_GRAY) {
            ByteBuffer srcData = ByteBuffer.wrap(((DataBufferByte) bImg.getRaster().getDataBuffer()).getData());
            while (srcData.hasRemaining()) {
                byte b = srcData.get();
                dstData.put(b);
                dstData.put(b);
                dstData.put(b);
            }
        } else {
            for (int y = 0; y < h; y++) {
                for (int x = 0; x < w; x++) {
                    int rgb = bImg.getRGB(x, y);
                    int r = (rgb >> 16) & 0xff;
                    int g = (rgb >> 8) & 0xff;
                    int b = rgb & 0xff;
                    dstData.put((byte) r);
                    dstData.put((byte) g);
                    dstData.put((byte) b);
                }
            }
        }

        return rgbImage;
    }

    public BufferedImage toBufferedImage() {
        BufferedImage img = new BufferedImage(this.getWidth(), this.getHeight(), BufferedImage.TYPE_3BYTE_BGR);
        ByteBuffer src = this.getData();
        ByteBuffer dst = ByteBuffer.wrap(((DataBufferByte) img.getRaster().getDataBuffer()).getData());
        while (src.hasRemaining()) {
            byte r = src.get();
            byte g = src.get();
            byte b = src.get();
            dst.put(b);
            dst.put(g);
            dst.put(r);
        }
        return img;
    }

    public void writeJPEG(String path) throws IOException {
        writeJPEG(new File(path));
    }

    public void writeJPEG(File file) throws IOException {
        OutputStream out = new BufferedOutputStream(new FileOutputStream(file));
        try {
            this.writeJPEG(out);
        } finally {
            out.close();
        }

    }

    public void writeJPEG(OutputStream out) throws IOException {
        ImageIO.write(this.toBufferedImage(), "jpg", out);
    }

    public void writePNG(OutputStream out) throws IOException {
        ImageIO.write(this.toBufferedImage(), "png", out);
    }

    public void writePNG(String path) throws IOException {
        OutputStream out = new BufferedOutputStream(new FileOutputStream(path));
        try {
            this.writePNG(out);
        } finally {
            out.close();
        }
    }

}
