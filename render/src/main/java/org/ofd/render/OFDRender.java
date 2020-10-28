package org.ofd.render;


import org.ofd.render.dir.OFDDir;
import org.ofdrw.core.basicStructure.ofd.DocBody;
import org.ofdrw.core.basicStructure.ofd.OFD;
import org.ofdrw.core.basicStructure.ofd.docInfo.CT_DocInfo;
import org.ofdrw.core.basicType.ST_Loc;

import java.io.IOException;
import java.net.URISyntaxException;
import java.text.SimpleDateFormat;
import java.time.LocalDate;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class OFDRender {
    private OFDDir ofdDir;

    public OFDRender() {
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
        ofdDir.setOfd(ofd);
    }


}
