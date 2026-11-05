package com.horizonkk.chuwudai.storage;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.zip.*;

public final class PayloadCodec {
    private PayloadCodec() {}
    public static String compress(String value) {
        try {
            ByteArrayOutputStream out=new ByteArrayOutputStream();
            try(DeflaterOutputStream zip=new DeflaterOutputStream(out)) { zip.write(value.getBytes(StandardCharsets.UTF_8)); }
            return Base64.getEncoder().encodeToString(out.toByteArray());
        } catch(IOException e) { throw new IllegalStateException(e); }
    }
    public static String decompress(String value) {
        if(value.isEmpty()) return "";
        try(InflaterInputStream in=new InflaterInputStream(new ByteArrayInputStream(Base64.getDecoder().decode(value)))) {
            byte[] bytes=in.readNBytes(16*1024*1024+1);
            if(bytes.length>16*1024*1024) throw new IOException("Container payload exceeds 16 MiB");
            return new String(bytes,StandardCharsets.UTF_8);
        } catch(IOException|IllegalArgumentException e) { throw new StorageAccessException(StorageAccessException.Reason.CORRUPT,"容器数据无法解压",e); }
    }
}
