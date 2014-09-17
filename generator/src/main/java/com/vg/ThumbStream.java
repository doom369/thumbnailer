package com.vg;

import com.vg.utils.FfmpegUtil;
import org.bridj.Pointer;
import org.ffmpeg.avcodec.*;
import org.ffmpeg.avformat.AVFormatContext;
import org.ffmpeg.avformat.AVStream;
import org.ffmpeg.avformat.AvformatLibrary;
import org.ffmpeg.avutil.AVRational;
import org.ffmpeg.swscale.SwscaleLibrary;

import java.io.Closeable;
import java.io.File;
import java.io.IOException;
import java.nio.ByteBuffer;

import static java.lang.Math.min;
import static java.nio.ByteBuffer.allocateDirect;
import static org.bridj.Pointer.pointerTo;
import static org.ffmpeg.avcodec.AvcodecLibrary.avcodec_decode_video2;
import static org.ffmpeg.avcodec.AvcodecLibrary.avpicture_fill;
import static org.ffmpeg.avformat.AvformatLibrary.*;
import static org.ffmpeg.avutil.AvutilLibrary.av_free;
import static org.ffmpeg.avutil.PixelFormat.PIX_FMT_RGB24;


public class ThumbStream implements Closeable {

    int frameDuration = -1;
    AVRational time_base = null;
    AVPacket packet = new AVPacket();
    private AVFormatContext formatCtx;
    private AVCodecContext codecCtx;
    private AVFrame frame;
    private AVFrame frameRGB;
    private AVStream stream;
    int[] timeValues;
    //private SeekableInputStream in;

    public static ThumbStream open(File file) throws IOException {
        ThumbStream thumbs = new ThumbStream();

        thumbs.formatCtx = FfmpegUtil.openFormat(file);
        AvformatLibrary.av_read_frame(pointerTo(thumbs.formatCtx), pointerTo(thumbs.packet));
        thumbs.frameDuration = thumbs.packet.duration();
        thumbs.stream = FfmpegUtil.findVideoStream(thumbs.formatCtx);
        thumbs.time_base = thumbs.stream.time_base();
        //todo fix
        //MovieAtom moov = parseMoovAtomCopy(file);
        //moov = fixTimescale(moov);
        //thumbs.timeValues = moov.getVideoTrack().getTimevalues();
        thumbs.timeValues = new int[1];
        thumbs.timeValues[0] = 0;
        return thumbs;
    }

    AVFrame getFrame() {
        if (frame == null) {
            frame = AvcodecLibrary.avcodec_alloc_frame().get();
        }
        return frame;
    }

    AVFrame getFrameRGB() {
        if (frameRGB == null) {
            frameRGB = AvcodecLibrary.avcodec_alloc_frame().get();
        }
        return frameRGB;
    }

    int getWidth() {
        return getCodecCtx().width();
    }

    int getHeight() {
        return getCodecCtx().height();
    }

    AVCodecContext getCodecCtx() {
        if (codecCtx == null) {
            codecCtx = new AVCodecContext(stream.codec());
            FfmpegUtil.openCodec(codecCtx);
            if (codecCtx.width() <= 0 || codecCtx.height() <= 0) {
                throw new IllegalStateException("Dimensions are wrong.");
            }
        }
        return codecCtx;
    }

    void seek(long requiredPts) {
        av_seek_frame(pointerTo(formatCtx), stream.index(), requiredPts, AVSEEK_FLAG_BACKWARD);
    }

    void convertToRgb(AVFrame srcFrame, Rgb24 dst) {
        AVFrame rgb = getFrameRGB();
        avpicture_fill(pointerTo(rgb).as(AVPicture.class), Pointer.pointerToBytes(dst.getData()), PIX_FMT_RGB24, dst.getWidth(), dst.getHeight());
        scale(srcFrame, rgb);
    }

    private Pointer swsContext = null;
    private Rgb24 thumb;

    int scale(AVFrame srcFrame, AVFrame dstFrame) {
        return SwscaleLibrary.sws_scale(getSwsContext(), srcFrame.data(), srcFrame.linesize(), 0, srcFrame.height(), dstFrame.data(), dstFrame.linesize());
    }

    private Pointer getSwsContext() {
        if (swsContext == null) {
            int flags = SwscaleLibrary.SWS_FAST_BILINEAR | SwscaleLibrary.SWS_CPU_CAPS_MMX
                    | SwscaleLibrary.SWS_CPU_CAPS_MMX2 | SwscaleLibrary.SWS_CPU_CAPS_SSE2 | SwscaleLibrary.SWS_PRINT_INFO;
            int w = getWidth();
            int h = getHeight();
            swsContext = SwscaleLibrary.sws_getContext(w, h, getCodecCtx().pix_fmt(), w, h, PIX_FMT_RGB24, flags, null, null, null);
        }
        if (swsContext == null) {
            throw new RuntimeException("sws_getContext failed");
        }
        return swsContext;
    }

    private volatile boolean closed = false;

    public void close() {
        if (!closed) {
            closed = true;
            //if (in != null) {
            //    IOUtils.closeQuietly(in);
            //}
            if (frame != null) {
                av_free(pointerTo(frame));
            }
            if (frameRGB != null) {
                av_free(pointerTo(frameRGB));
            }
            if (codecCtx != null) {
                FfmpegUtil.closeCodec(codecCtx);
            }
            if (formatCtx != null) {
                av_close_input_file(pointerTo(formatCtx));
            }
            if (swsContext != null) {
                SwscaleLibrary.sws_freeContext(swsContext);
            }
        }
    }

    public Rgb24 createPreviewThumbnails(int[] frameNumbers) throws IOException {
        long[] timeValues = new long[frameNumbers.length];

        for (int i = 0; i < frameNumbers.length; i++) {
            timeValues[i] = this.timeValues[min(this.timeValues.length - 1, frameNumbers[i])];
        }
        return createPreviewThumbnails(timeValues);
    }

    public Rgb24 createPreviewThumbnails(long[] timeValues) throws IOException {
        if (closed) {
            throw new IllegalStateException("closed");
        }
        int w = getWidth();
        int h = getHeight();
        thumb = thumb != null && thumb.getWidth() == w && thumb.getHeight() == h ? thumb : new Rgb24(w, h, allocateDirect(Rgb24.sizeof(w, h)));
        Rgb24 resultRgb = new Rgb24(w * timeValues.length, h);

        Pointer<Integer> frameFinished = Pointer.allocateInt();
        for (int i = 0; i < timeValues.length; i++) {
            long expectedPts = timeValues[i] / time_base.num();
            seek(expectedPts);
            long actualPts = -1;
            inner: do {
                frameFinished.setInt(0);
                while (frameFinished.getInt() == 0) {
                    FfmpegUtil.freePacket(packet);
                    int rv = av_read_frame(pointerTo(formatCtx), pointerTo(packet));
                    if (packet.stream_index() == this.stream.index()) {
                        long seek = rv < 0 ? -1 : packet.pts();
                        if (seek == -1) {
                            //EOF or error
                            break inner;
                        }
                        actualPts = packet.pts();
                        avcodec_decode_video2(pointerTo(getCodecCtx()), pointerTo(getFrame()), frameFinished, pointerTo(packet));
                    }
                }
            } while (expectedPts != actualPts);
            // System.out.println(i + " req: " + pts + " actual: " + seek);
            convertToRgb(getFrame(), thumb);
            ByteBuffer dstData = resultRgb.getData();
            int dstPosition = i * thumb.stride();
            ByteBuffer srcData = thumb.getData();
            for (int y = 0; y < h; y++) {
                srcData.limit(srcData.position() + thumb.stride());
                dstData.position(dstPosition + (y * resultRgb.stride()));
                dstData.put(srcData);
            }
        }
        return resultRgb;
    }

    protected void finalize() throws Throwable {
        super.finalize();
        close();
    }

}