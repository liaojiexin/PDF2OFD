package org.ofd.render;

import org.apache.commons.io.FileUtils;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.pdmodel.font.PDFont;

import java.io.File;
import java.io.IOException;
import java.util.*;

public class OFDRender {

    public static byte[] convertPdfToOfd(byte[] pdfBytes) {
        long start;
        long end;

        String tempFilePath = generateTempFilePath();
        PDDocument doc = null;
        try {
            FileUtils.writeByteArrayToFile(new File(tempFilePath), pdfBytes);
            doc = PDDocument.load(new File(tempFilePath));
            start = System.currentTimeMillis();
            OFDCreator ofdCreator = new OFDCreator();
            for (int i = 0; i < doc.getNumberOfPages(); i++) {
                OFDPageDrawer ofdPageDrawer = new OFDPageDrawer(doc.getPage(i), ofdCreator);
                ofdPageDrawer.drawPage();
            }
            end = System.currentTimeMillis();
            return ofdCreator.jar();
        } catch (Exception e) {
            e.printStackTrace();
        } finally {
            try {
                if (doc != null) {
                    doc.close();
                }
                FileUtils.forceDeleteOnExit(new File(tempFilePath));
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
        return null;
    }

    protected static String generateTempFilePath() {
        return System.getProperty("java.io.tmpdir") + "/" + UUID.randomUUID().toString();
    }
}
