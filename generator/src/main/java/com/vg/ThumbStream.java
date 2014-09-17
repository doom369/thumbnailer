package com.vg;

import com.vg.utils.FfmpegUtil;
import org.bridj.Pointer;
import org.ffmpeg.avcodec.*;
import org.ffmpeg.avformat.AVFormatContext;
import org.ffmpeg.avformat.AVStream;
import org.ffmpeg.swscale.SwscaleLibrary;

import java.io.Closeable;
import java.io.File;
import java.io.IOException;
import java.nio.ByteBuffer;

import static java.nio.ByteBuffer.allocateDirect;
import static org.bridj.Pointer.pointerTo;
import static org.ffmpeg.avcodec.AvcodecLibrary.avcodec_decode_video2;
import static org.ffmpeg.avcodec.AvcodecLibrary.avpicture_fill;
import static org.ffmpeg.avformat.AvformatLibrary.*;
import static org.ffmpeg.avutil.AvutilLibrary.AV_TIME_BASE;
import static org.ffmpeg.avutil.AvutilLibrary.av_free;
import static org.ffmpeg.avutil.PixelFormat.PIX_FMT_RGB24;


public class ThumbStream implements Closeable {

    private AVPacket packet;
    private AVFormatContext formatCtx;
    private AVCodecContext codecCtx;
    private AVFrame frame;
    private AVFrame frameRGB;
    private AVStream stream;
    // Duration of one frame in AV_TIME_BASE units
    private int frameDuration;

    public ThumbStream(File file) throws IOException {
        this.packet = new AVPacket();
        this.formatCtx = FfmpegUtil.openFormat(file);
        //this.packetDuration = this.packet.duration();
        this.stream = FfmpegUtil.findVideoStream(this.formatCtx);
        //this.time_base = this.stream.time_base();
        this.codecCtx = initCodecContext(this.stream);
        this.frameDuration = (this.codecCtx.time_base().num() * AV_TIME_BASE) / this.codecCtx.time_base().den();
    }

    private AVFrame getFrame() {
        if (frame == null) {
            frame = AvcodecLibrary.avcodec_alloc_frame().get();
        }
        return frame;
    }

    private AVFrame getFrameRGB() {
        if (frameRGB == null) {
            frameRGB = AvcodecLibrary.avcodec_alloc_frame().get();
        }
        return frameRGB;
    }

    private int getWidth() {
        return codecCtx.width();
    }

    private int getHeight() {
        return codecCtx.height();
    }

    private AVCodecContext getCodecCtx() {
        return codecCtx;
    }

    private static AVCodecContext initCodecContext(AVStream stream) {
        AVCodecContext codecCtx = new AVCodecContext(stream.codec());
        FfmpegUtil.openCodec(codecCtx);
        if (codecCtx.width() <= 0 || codecCtx.height() <= 0) {
            throw new IllegalStateException("Dimensions are wrong.");
        }
        return codecCtx;
    }

    private void seek(long requiredPts) {
        if (av_seek_frame(pointerTo(formatCtx), -1, requiredPts, AVSEEK_FLAG_ANY) < 0) {
            throw new IllegalStateException("av_seek_frame failed to " + requiredPts);
        }
    }

    private void convertToRgb(AVFrame srcFrame, Rgb24 dst) {
        AVFrame rgb = getFrameRGB();
        avpicture_fill(pointerTo(rgb).as(AVPicture.class), Pointer.pointerToBytes(dst.getData()), PIX_FMT_RGB24, dst.getWidth(), dst.getHeight());
        scale(srcFrame, rgb);
    }

    private Pointer swsContext = null;
    private Rgb24 thumb;

    private int scale(AVFrame srcFrame, AVFrame dstFrame) {
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


    public Rgb24 createPreviewThumbnails(int[] frames) throws IOException {
        int w = getWidth();
        int h = getHeight();
        thumb = thumb != null && thumb.getWidth() == w && thumb.getHeight() == h ? thumb : new Rgb24(w, h, allocateDirect(Rgb24.sizeof(w, h)));
        Rgb24 resultRgb = new Rgb24(w * frames.length, h);

        Pointer<Integer> frameFinished = Pointer.allocateInt();
        for (int i = 0; i < frames.length; i++) {
            long expectedPts = frames[i] * frameDuration;
            seek(expectedPts);

            frameFinished.setInt(0);
            FfmpegUtil.freePacket(packet);
            while (av_read_frame(pointerTo(formatCtx), pointerTo(packet)) >= 0 && frameFinished.get() == 0) {
                if (packet.stream_index() == this.stream.index()) {
                    avcodec_decode_video2(pointerTo(getCodecCtx()), pointerTo(getFrame()), frameFinished, pointerTo(packet));
                }
                FfmpegUtil.freePacket(packet);
            }

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

    @Override
    public void close() {
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