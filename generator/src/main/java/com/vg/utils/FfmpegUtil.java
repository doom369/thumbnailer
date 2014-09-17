package com.vg.utils;

import org.bridj.IntValuedEnum;
import org.bridj.Pointer;
import org.ffmpeg.avcodec.*;
import org.ffmpeg.avformat.AVFormatContext;
import org.ffmpeg.avformat.AVStream;
import org.ffmpeg.avformat.AvformatLibrary;
import org.ffmpeg.avutil.AVMediaType;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

import static org.bridj.Pointer.*;


public class FfmpegUtil {



    public static void freePacket(AVPacket pkt) {
        if (pkt != null) {
            AvcodecLibrary.av_free_packet(Pointer.pointerTo(pkt));
        }
    }



    public static AVFormatContext openFormat(File file) {
        Pointer<Pointer<AVFormatContext>> ppFormatCtx = pointerToPointer(null);
        Pointer<Byte> filename = pointerToCString(file.getAbsolutePath());
        int open_input = AvformatLibrary.avformat_open_input(ppFormatCtx, filename, null, null);
        if (open_input != 0) {
            throw new IllegalArgumentException("cant open file " + file.getAbsolutePath());
        }

        AVFormatContext formatCtx = new AVFormatContext(ppFormatCtx.get());
        if (findStreamInfo(formatCtx) < 0) {
            throw new IllegalStateException("Couldn't find stream information.");
        }
        return formatCtx;
    }



    public static AVStream findVideoStream(final AVFormatContext formatContext) {

        for (AVStream stream : FfmpegUtil.streams(formatContext)) {
            AVCodecContext codec = new AVCodecContext(stream.codec());
            if (codec.codec_type().iterator().next().equals(AVMediaType.AVMEDIA_TYPE_VIDEO)) {
                return stream;
            }
        }
        return null;
    }

    public static List<AVStream> streams(final AVFormatContext formatContext) {
        Pointer<Pointer<AVStream>> streams2 = formatContext.streams();
        int nb_streams = formatContext.nb_streams();
        List<AVStream> list = new ArrayList<>();
        for (int i = 0; i < nb_streams; i++) {
            Pointer<AVStream> pointer = streams2.get(i);
            AVStream avStream = pointer.get();
            list.add(avStream);
        }
        return list;
    }

    public static int findStreamInfo(AVFormatContext formatContext) {
        try {
            FfmpegUtil.openCloseLock.lock();
            Pointer<AVFormatContext> pctx = pointerTo(formatContext);
            System.out.println("findStreamInfo pctx: " + pctx);
            return AvformatLibrary.avformat_find_stream_info(pctx, null);
        } finally {
            FfmpegUtil.openCloseLock.unlock();

        }
    }

    public static AVCodec openCodec(AVCodecContext codecCtx) {
        try {
            FfmpegUtil.openCloseLock.lock();
            IntValuedEnum<CodecID> codec_id = codecCtx.codec_id();
            if (codec_id.value() == 0) {
                throw new IllegalStateException("Couldn't find stream information");
            }
            AVCodec codec = AvcodecLibrary.avcodec_find_decoder(codec_id).get();
            if (codec == null) {
                throw new IllegalStateException("Codec not found for codec_id  " + codec_id);
            }
            if (AvcodecLibrary.avcodec_open2(pointerTo(codecCtx), pointerTo(codec), null) < 0) {
                throw new IllegalStateException("Could not open codec.");
            }
            return codec;
        } finally {
            FfmpegUtil.openCloseLock.unlock();
        }
    }

    public static void closeCodec(AVCodecContext codecCtx) {
        try {
            FfmpegUtil.openCloseLock.lock();
            AvcodecLibrary.avcodec_close(pointerTo(codecCtx));
        } finally {
            FfmpegUtil.openCloseLock.unlock();
        }
    }

    public static Lock openCloseLock = new ReentrantLock();
}
