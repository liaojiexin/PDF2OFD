package org.ofd;

import org.apache.commons.io.FileUtils;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.rendering.PDFRenderer;
import org.ofd.render.OFDRender;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.util.Objects;

public class Test {
    protected static final String basePath = Objects.requireNonNull(Test.class.getClassLoader().getResource("")).getPath();
    private final Logger logger = LoggerFactory.getLogger(this.getClass());

    @org.junit.Test
    public void convertToOfd() {
        String pdfFilePath = basePath + "sc.pdf";
        String ofdOutPath = basePath + "sc-from-pdf.ofd";
        for (int i = 0; i < 1; i++) {
            try {
                byte[] pdfBytes = FileUtils.readFileToByteArray(new File(pdfFilePath));
                byte[] ofdBytes = OFDRender.convertPdfToOfd(pdfBytes);
                if (Objects.nonNull(ofdBytes)) {
                    FileUtils.writeByteArrayToFile(new File(ofdOutPath), ofdBytes);
                    logger.info("pdf convert to ofd done, pdf file save to {}", ofdOutPath);
                } else {
                    logger.error("pdf convert to ofd failed");
                }
            } catch (Exception e) {
                logger.error("test convert failed", e);
            }
        }
    }

    @org.junit.Test
    public void testConvertToImage() throws IOException {
        PDDocument doc = PDDocument.load(new File("E:/source/PDF2OFD/render/src/test/resources/m.pdf"));
        BufferedImage image = new PDFRenderer(doc).renderImage(0, 300 / 72);
        File output = new File("image.png");
        ImageIO.write(image, "png", output);
        doc.close();
    }
}
