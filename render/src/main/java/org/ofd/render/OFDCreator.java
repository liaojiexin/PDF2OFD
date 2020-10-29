package org.ofd.render;

import org.ofd.render.config.OfdResIdDefine;
import org.ofd.render.dir.DocDir;
import org.ofd.render.dir.OFDDir;
import org.ofdrw.core.basicStructure.doc.CT_CommonData;
import org.ofdrw.core.basicStructure.doc.CT_PageArea;
import org.ofdrw.core.basicStructure.doc.Document;
import org.ofdrw.core.basicStructure.ofd.DocBody;
import org.ofdrw.core.basicStructure.ofd.OFD;
import org.ofdrw.core.basicStructure.ofd.docInfo.CT_DocInfo;
import org.ofdrw.core.basicStructure.res.Res;
import org.ofdrw.core.basicStructure.res.resources.ColorSpaces;
import org.ofdrw.core.basicStructure.res.resources.Fonts;
import org.ofdrw.core.basicType.ST_Loc;
import org.ofdrw.core.basicType.ST_RefID;
import org.ofdrw.core.pageDescription.color.colorSpace.BitsPerComponent;
import org.ofdrw.core.pageDescription.color.colorSpace.CT_ColorSpace;
import org.ofdrw.core.pageDescription.color.colorSpace.OFDColorSpaceType;
import org.ofdrw.core.text.font.CT_Font;

import java.io.IOException;
import java.text.SimpleDateFormat;
import java.time.LocalDate;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class OFDCreator {
    private Map<String, String> fontMap;
    private OFDDir ofdDir;

    private DocDir docDir;

    private long localRID = OfdResIdDefine.RID_VarStart;

    public long getNextRid() {
        return ++localRID;
    }

    public long getCurrRid() {
        return localRID;
    }

    public Map<String, String> getFontMap() {
        return fontMap;
    }

    public void setFontMap(Map<String, String> fontMap) {
        this.fontMap = fontMap;
    }

    public OFDCreator() {
        fontMap = new HashMap<>();
        ofdDir = new OFDDir();

        //生成ofd.xml文件
        OFD ofd = new OFD();
        ofd.attribute("Version").setValue("1.1");

        DocBody docBody = new DocBody();
        docBody.setDocRoot(new ST_Loc("Doc_0/Document.xml"));

        CT_DocInfo docInfo = new CT_DocInfo();
        docInfo.setDocID(UUID.randomUUID());
        docInfo.setCreatorVersion("1.0");
        docInfo.setAuthor("OFD");
        docInfo.setCreationDate(LocalDate.now());
        docInfo.setCreator("OFD");
        docBody.setDocInfo(docInfo);
        ofd.addDocBody(docBody);
        ofdDir.add(this.genDocDir());
        ofdDir.setOfd(ofd);
    }

    private DocDir genDocDir()  {
        SimpleDateFormat df = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");

        docDir = new DocDir();
        docDir.setPublicRes(genPublicRes());
        docDir.setDocumentRes(genDocumentRes());
        Document doc = genDoc();
        docDir.setDocument(doc);

        return docDir;
    }

    private Res genPublicRes() {
        Res ret = new Res();
        ret.setBaseLoc(new ST_Loc("Res"));
        CT_ColorSpace clrSpace = new CT_ColorSpace(OFDColorSpaceType.RGB, OfdResIdDefine.RID_ColorSpace);
        clrSpace.setBitsPerComponent(BitsPerComponent.BIT_8);
        ColorSpaces clrSpaces = new ColorSpaces();
        clrSpaces.addColorSpace(clrSpace);
        ret.addResource(clrSpaces);

        Fonts fonts = new Fonts();
        ret.addResource(fonts);

        return ret;
    }

    private Res genDocumentRes() {
        Res ret = new Res();
        ret.setBaseLoc(new ST_Loc("Res"));
        return ret;
    }

    public Document genDoc() {
        Document doc = new Document();

        //1. 创建commonData
        CT_CommonData commonData = new CT_CommonData();
        commonData.setMaxUnitID(getCurrRid());
        commonData.setDefaultCS(new ST_RefID(OfdResIdDefine.RID_ColorSpace));

        //1.1页面区域
        CT_PageArea pageArea = new CT_PageArea();
        pageArea.setPhysicalBox(0, 0, 210, 297);
        pageArea.setApplicationBox(0, 0, 210, 297);
        pageArea.setContentBox(0, 0, 210, 297);
        commonData.setPageArea(pageArea);
        commonData.setPublicRes(new ST_Loc("PublicRes.xml"));
        commonData.setDocumentRes(new ST_Loc("DocumentRes.xml"));
        doc.setCommonData(commonData);

        return doc;
    }

    public long putFont(String familyName, String fontName, byte[] fontBytes, String suffix) {
        long currentId = this.getNextRid();
        CT_Font fntKt = new CT_Font();
        fntKt.setFamilyName(familyName);
        fntKt.setFontName(fontName);
        fntKt.setID(currentId);
        if (fontBytes != null) {
            fntKt.setFontFile(new ST_Loc(fontName + suffix));
            docDir.addResource(fontName + suffix, fontBytes);
        }
        Fonts fonts = this.ofdDir.getDocDefault().getPublicRes().getFonts().get(0);
        if (fonts != null) {
            this.ofdDir.getDocDefault().getPublicRes().getFonts().get(0).addFont(fntKt);
        }
        return currentId;
    }

    public byte[] jar() throws IOException {

        Map<String, byte[]> virtualFileMap = new ConcurrentHashMap<>();
        byte[] ofdByte = ofdDir.jar(virtualFileMap);
        return ofdByte;
    }
}
