package org.ofd.render;

import org.apache.fontbox.util.BoundingBox;
import org.apache.pdfbox.contentstream.PDFGraphicsStreamEngine;
import org.apache.pdfbox.cos.COSName;
import org.apache.pdfbox.io.IOUtils;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.common.PDStream;
import org.apache.pdfbox.pdmodel.font.*;
import org.apache.pdfbox.pdmodel.font.encoding.GlyphList;
import org.apache.pdfbox.pdmodel.graphics.color.PDColor;
import org.apache.pdfbox.pdmodel.graphics.color.PDColorSpace;
import org.apache.pdfbox.pdmodel.graphics.image.PDImage;
import org.apache.pdfbox.pdmodel.graphics.state.PDGraphicsState;
import org.apache.pdfbox.pdmodel.graphics.state.PDTextState;
import org.apache.pdfbox.util.Matrix;
import org.bouncycastle.crypto.Digest;
import org.bouncycastle.crypto.digests.MD5Digest;
import org.ofdrw.core.basicStructure.pageObj.layer.CT_Layer;
import org.ofdrw.core.basicStructure.pageObj.layer.block.ImageObject;
import org.ofdrw.core.basicStructure.pageObj.layer.block.PathObject;
import org.ofdrw.core.basicStructure.pageObj.layer.block.TextObject;
import org.ofdrw.core.basicType.ST_Array;
import org.ofdrw.core.basicType.ST_ID;
import org.ofdrw.core.basicType.ST_RefID;
import org.ofdrw.core.graph.pathObj.AbbreviatedData;
import org.ofdrw.core.pageDescription.color.color.CT_Color;
import org.ofdrw.core.text.TextCode;
import org.ofdrw.core.text.text.Weight;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.imageio.ImageIO;
import java.awt.geom.GeneralPath;
import java.awt.geom.PathIterator;
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

    private static final byte SEG_MOVETO = (byte) PathIterator.SEG_MOVETO;
    private static final byte SEG_LINETO = (byte) PathIterator.SEG_LINETO;
    private static final byte SEG_QUADTO = (byte) PathIterator.SEG_QUADTO;
    private static final byte SEG_CUBICTO = (byte) PathIterator.SEG_CUBICTO;
    private static final byte SEG_CLOSE = (byte) PathIterator.SEG_CLOSE;

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
        double x = 0, y = 0;
        float w = page.getCropBox().getWidth() * scale;
        float h = page.getCropBox().getHeight() * scale;

        double lineWidth = getGraphicsState().getLineWidth() * scale;
        ctLayer.add(getPathObject(x, y, w, h, lineWidth, getStrokeColor(), false));
        linePath.reset();
    }

    @Override
    public void fillPath(int i) throws IOException {
        double x = 0, y = 0;
        float w = page.getCropBox().getWidth() * scale;
        float h = page.getCropBox().getHeight() * scale;

        double lineWidth = getGraphicsState().getLineWidth() * scale;
        ctLayer.add(getPathObject(x, y, w, h, lineWidth, getStrokeColor(), true));
        linePath.reset();
    }

    private PathObject getPathObject(double x, double y, float w, float h, double lineWidth, CT_Color strokeColor, boolean fill) throws IOException {
        PathObject po = new PathObject(ST_ID.getInstance(String.valueOf(ofdCreator.getNextRid())));

        if (strokeColor != null) {
            po.setStroke(true);
            po.setStrokeColor(strokeColor);
        } else {
            po.setStroke(false);
        }

        po.setLineWidth(lineWidth);
        po.setBoundary(x, y, w, h);
        AbbreviatedData data = new AbbreviatedData();
        drawLine(linePath.getPathIterator(null), data, h);
        if (fill) {
            CT_Color nonStrokeColor = getNonStrokeColor();
            if (nonStrokeColor != null) {
                po.setFillColor(nonStrokeColor);
                po.setFill(true);
            }
        } else {
            po.setFill(false);
        }
        po.setAbbreviatedData(data);
        return po;
    }

    private CT_Color getNonStrokeColor() throws IOException {
        PDColor strokingColor = getGraphicsState().getNonStrokingColor();
        return convertRgbToColor(strokingColor);
    }

    private CT_Color getStrokeColor() throws IOException {
        PDColor strokingColor = getGraphicsState().getStrokingColor();
        return convertRgbToColor(strokingColor);
    }

    private CT_Color convertRgbToColor(PDColor color) throws IOException {
        if (color != null) {
            PDColorSpace colorSpace = color.getColorSpace();
            float[] rgb = colorSpace.toRGB(color.getComponents());
            int r = toRgbNumber(rgb[0]);
            int g = toRgbNumber(rgb[1]);
            int b = toRgbNumber(rgb[2]);
            return CT_Color.rgb(r, g, b);
        }
        return null;
    }

    private void drawLine(PathIterator pi, AbbreviatedData data, float height) throws IOException {
        double[] coords = new double[6];
        while (!pi.isDone()) {
            switch (pi.currentSegment(coords)) {
                case SEG_MOVETO:
                    data.moveTo(coords[0] * scale, height - coords[1] * scale);
                    break;
                case SEG_LINETO:
                    data.lineTo(coords[0] * scale, height - coords[1] * scale);
                    break;
                case SEG_CUBICTO:
                    data.B(coords[0] * scale, height - coords[1] * scale,
                            coords[2] * scale, height - coords[3] * scale,
                            coords[4] * scale, height - coords[5] * scale);
                    break;
                case SEG_CLOSE:
                    data.close();
                    closePath();
                    break;
                default:
                    break;
            }
            pi.next();
        }
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

    /**
     * 转换为rgb
     */
    private int toRgbNumber(float color) {
        return color < 0 ? 0 : (int) ((color > 1 ? 1 : color) * 255);
    }
}
