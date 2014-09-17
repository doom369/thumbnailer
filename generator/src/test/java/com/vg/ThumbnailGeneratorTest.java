package com.vg;

import com.vg.utils.ImageUtil;
import com.vg.utils.SystemUtil;
import org.bridj.BridJ;
import org.ffmpeg.avcodec.AvcodecLibrary;
import org.ffmpeg.avformat.AvformatLibrary;
import org.junit.Test;

import java.awt.image.BufferedImage;
import java.nio.file.Files;
import java.nio.file.Path;

import static com.vg.BaseTest.resolveTestResource;

public class ThumbnailGeneratorTest {

    static {
        //can be passed via -Djava.library.path=path"
        BridJ.addLibraryPath("/Users/dima/livews/thumbnailer/ffmpeg-wrapper/src/main/resources/0.7/mac");
        AvformatLibrary.av_register_all();
        AvcodecLibrary.avcodec_register_all();
    }

    @Test
    public void testGenerateSingleThumbForH264Video() throws Exception {
        Path moviePath = resolveTestResource("/thumbs.mov");

        BufferedImage bi = ThumbnailGenerator.generate(moviePath, 0, 1);

        Path tmp = Files.createTempFile("my", "jpg");
        ImageUtil.output(bi, tmp);

        SystemUtil.spawn("open", tmp.toString());
    }

}
