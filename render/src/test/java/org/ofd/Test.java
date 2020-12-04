package org.ofd;

import org.apache.commons.io.FileUtils;
import org.ofd.render.OFDRender;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Objects;

public class Test {
    private final Logger logger = LoggerFactory.getLogger(this.getClass());

    @org.junit.Test
    public void convertToOfdByStream() {
        Path input = Paths.get("src/test/resources/n.pdf");
        Path output = Paths.get("target/n-from-pdf.ofd");
        for (int i = 0; i < 1; i++) {
            try {
                OFDRender.convertPdfToOfd(Files.newInputStream(input), Files.newOutputStream(output));
            } catch (Exception e) {
                logger.error("test convert failed", e);
            }
        }
    }

    @org.junit.Test
    public void convertToOfd() {
        Path input = Paths.get("src/test/resources/sc.pdf");
        Path output = Paths.get("target/sc-from-pdf.ofd");
        for (int i = 0; i < 1; i++) {
            try {
                byte[] pdfBytes = FileUtils.readFileToByteArray(input.toFile());
                byte[] ofdBytes = OFDRender.convertPdfToOfd(pdfBytes);
                if (Objects.nonNull(ofdBytes)) {
                    FileUtils.writeByteArrayToFile(output.toFile(), ofdBytes);
                    logger.info("pdf convert to ofd done, pdf file save to {}", output.toAbsolutePath().toString());
                } else {
                    logger.error("pdf convert to ofd failed");
                }
            } catch (Exception e) {
                logger.error("test convert failed", e);
            }
        }
    }

}
