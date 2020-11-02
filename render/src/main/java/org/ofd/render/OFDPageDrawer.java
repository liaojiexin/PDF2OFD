package org.ofd.render;

import org.apache.pdfbox.contentstream.PDFGraphicsStreamEngine;
import org.apache.pdfbox.cos.COSName;
import org.apache.pdfbox.io.IOUtils;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.common.PDStream;
import org.apache.pdfbox.pdmodel.font.*;
import org.apache.pdfbox.pdmodel.font.encoding.GlyphList;
import org.apache.pdfbox.pdmodel.graphics.image.PDImage;
import org.apache.pdfbox.pdmodel.graphics.state.PDGraphicsState;
import org.apache.pdfbox.pdmodel.graphics.state.PDTextState;
import org.apache.pdfbox.pdmodel.interactive.annotation.PDAnnotation;
import org.apache.pdfbox.util.Matrix;
import org.bouncycastle.crypto.Digest;
import org.bouncycastle.crypto.digests.MD5Digest;
import org.ofdrw.core.OFDElement;
import org.ofdrw.core.basicStructure.pageObj.layer.CT_Layer;
import org.ofdrw.core.basicStructure.pageObj.layer.block.ImageObject;
import org.ofdrw.core.basicType.ST_Array;
import org.ofdrw.core.basicType.ST_ID;
import org.ofdrw.core.basicType.ST_RefID;
import org.ofdrw.core.graph.pathObj.CT_Path;
import org.ofdrw.core.pageDescription.clips.Area;
import org.ofdrw.core.pageDescription.clips.CT_Clip;
import org.ofdrw.core.pageDescription.clips.Clips;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.imageio.ImageIO;
import java.awt.geom.GeneralPath;
import java.awt.geom.Point2D;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;

public class OFDPageDrawer extends PDFGraphicsStreamEngine {

    private final Logger logger = LoggerFactory.getLogger(this.getClass());
    private GeneralPath linePath = new GeneralPath();
    private int clipWindingRule = -1;

    private OFDCreator ofdCreator;
    private final GlyphList glyphList;
    private CT_Layer ctLayer;
    private float scale;
    private PDPage page;

    public CT_Layer getCtLayer() {
        return ctLayer;
    }

    protected OFDPageDrawer(int idx, PDPage page, OFDCreator ofdCreator, float scale) throws IOException {
        super(page);
        this.page = page;
        this.ofdCreator = ofdCreator;
        // load additional glyph list for Unicode mapping
        String path = "/org/apache/pdfbox/resources/glyphlist/additional.txt";
        InputStream input = GlyphList.class.getResourceAsStream(path);
        glyphList = new GlyphList(GlyphList.getAdobeGlyphList(), input);
        ctLayer = this.ofdCreator.createLayer();
        this.scale = scale;
    }

    public void drawPage() throws IOException {
        processPage(this.getPage());
    }


    @Override
    public void appendRectangle(Point2D p0, Point2D p1, Point2D p2, Point2D p3) throws IOException {
        linePath.moveTo((float) p0.getX(), (float) p0.getY());
        linePath.lineTo((float) p1.getX(), (float) p1.getY());
        linePath.lineTo((float) p2.getX(), (float) p2.getY());
        linePath.lineTo((float) p3.getX(), (float) p3.getY());
        linePath.closePath();
    }

    @Override
    public void drawImage(PDImage pdImage) throws IOException {
        ByteArrayOutputStream bosImage = new ByteArrayOutputStream();
        String suffix = "png";
        ImageIO.write(pdImage.getImage(), suffix, bosImage);
        String name = String.format("%s.%s", bcMD5(bosImage.toByteArray()), suffix);
        ofdCreator.putImage(name, bosImage.toByteArray(), suffix);

        Matrix ctmNew = this.getGraphicsState().getCurrentTransformationMatrix();
        float imageXScale = ctmNew.getScalingFactorX();
        float imageYScale = ctmNew.getScalingFactorY();
        double x = ctmNew.getTranslateX() * scale;
        double y = (page.getCropBox().getHeight() - ctmNew.getTranslateY() - imageYScale) * scale;
        double w = imageXScale * scale;
        double h = imageYScale * scale;

        ImageObject imageObject = new ImageObject(ofdCreator.getNextRid());
        imageObject.setBoundary(x, y, w, h);
        imageObject.setResourceID(new ST_RefID(ST_ID.getInstance(ofdCreator.getImageMap().get(name))));
        imageObject.setCTM(ST_Array.getInstance(String.format("%.0f 0 0 %.0f 0 0", w, h)));
        ctLayer.add(imageObject);
    }

    private String bcMD5(byte[] imageBytes) {
        Digest digest = new MD5Digest();
        digest.update(imageBytes, 0, imageBytes.length);
        byte[] md5Bytes = new byte[digest.getDigestSize()];
        digest.doFinal(md5Bytes, 0);
        return org.bouncycastle.util.encoders.Hex.toHexString(md5Bytes);
    }

    @Override
    public void clip(int windingRule) throws IOException {
        clipWindingRule = windingRule;
    }

    @Override
    public void moveTo(float x, float y) throws IOException {
        linePath.moveTo(x, y);
    }

    @Override
    public void lineTo(float x, float y) throws IOException {
        linePath.lineTo(x, y);
    }

    @Override
    public void curveTo(float x1, float y1, float x2, float y2, float x3, float y3) throws IOException {
        linePath.curveTo(x1, y1, x2, y2, x3, y3);
    }

    @Override
    public Point2D getCurrentPoint() throws IOException {
        return linePath.getCurrentPoint();
    }

    @Override
    public void closePath() throws IOException {
        linePath.closePath();
    }

    @Override
    public void endPath() throws IOException {
        if (clipWindingRule != -1) {
            linePath.setWindingRule(clipWindingRule);
            getGraphicsState().intersectClippingPath(linePath);
            clipWindingRule = -1;
        }
        linePath.reset();
    }

    @Override
    public void strokePath() throws IOException {
        linePath.reset();
    }

    @Override
    public void fillPath(int i) throws IOException {
        linePath.reset();
    }

    @Override
    public void fillAndStrokePath(int windingRule) throws IOException {
        GeneralPath path = (GeneralPath) this.linePath.clone();
        this.fillPath(windingRule);
        this.linePath = path;
        this.strokePath();
    }

    @Override
    public void shadingFill(COSName cosName) throws IOException {

    }

    @Override
    protected void showText(byte[] string) throws IOException {
        PDGraphicsState state = this.getGraphicsState();
        PDTextState textState = state.getTextState();
        PDFont font = textState.getFont();
        if (font == null) {
            font = PDType1Font.HELVETICA;
        }
        if (ofdCreator.getFontMap().get(font.getName()) == null) {

//            InputStream is = null;
//            if (font instanceof PDTrueTypeFont) {
//                PDTrueTypeFont f = (PDTrueTypeFont) font;
//                is = f.getTrueTypeFont().getOriginalData();
//            } else if (font instanceof PDType0Font) {
//                PDType0Font type0Font = (PDType0Font) font;
//                if (type0Font.getDescendantFont() instanceof PDCIDFontType2) {
//                    PDCIDFontType2 ff = (PDCIDFontType2) type0Font.getDescendantFont();
//                    is = ff.getTrueTypeFont().getOriginalData();
//                } else if (type0Font.getDescendantFont() instanceof PDCIDFontType0) {
//                    // a Type0 CIDFont contains CFF font
//                    PDCIDFontType0 cidType0Font = (PDCIDFontType0) type0Font.getDescendantFont();
//                }
//            } else if (font instanceof PDType1Font) {
//                PDType1Font f = (PDType1Font) font;
//            } else if (font instanceof PDType1CFont) {
//                PDType1CFont f = (PDType1CFont) font;
//            } else if (font instanceof PDType3Font) {
//                PDType3Font f = (PDType3Font) font;
//            }



            byte[] fontBytes = null;
            if (font instanceof PDTrueTypeFont) {
                fontBytes = getFontByte(font.getFontDescriptor(), font.getName());
            } else if (font instanceof PDType0Font) {
                PDCIDFont descendantFont = ((PDType0Font) font).getDescendantFont();
                fontBytes = getFontByte(descendantFont.getFontDescriptor(), font.getName());
            } else if (font instanceof PDType1CFont) {
                fontBytes = getFontByte(font.getFontDescriptor(), font.getName());
            }
            ofdCreator.putFont(font.getName(), font.getName(), fontBytes, ".otf");
        }

        InputStream in = new ByteArrayInputStream(string);
        StringBuilder builder = new StringBuilder();
        while (in.available() > 0) {
            // decode a character
            int before = in.available();
            int code = font.readCode(in);
            int codeLength = before - in.available();
            String unicode = font.toUnicode(code, glyphList);
            builder.append(unicode);
        }
        System.out.println(builder.toString());

    }

    private byte[] getFontByte(PDFontDescriptor fd, String name) throws IOException {
        byte[] fontBytes = null;
        if (fd != null) {
            PDStream ff2Stream = fd.getFontFile2();
            if (ff2Stream != null) {
                fontBytes = IOUtils.toByteArray(ff2Stream.createInputStream());
            } else {
                ff2Stream = fd.getFontFile();
                if (ff2Stream != null) {
                    fontBytes = IOUtils.toByteArray(ff2Stream.createInputStream());
                } else {
                    ff2Stream = fd.getFontFile3();
                    if (ff2Stream != null) {
                        fontBytes = IOUtils.toByteArray(ff2Stream.createInputStream());
                    }
                }
            }
        }
        return fontBytes;
    }
}
