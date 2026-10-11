package com.phonghub.adapter.in.web;

import com.google.zxing.BarcodeFormat;
import com.google.zxing.WriterException;
import com.google.zxing.client.j2se.MatrixToImageWriter;
import com.google.zxing.common.BitMatrix;
import com.google.zxing.qrcode.QRCodeWriter;
import com.phonghub.domain.model.RoomAsset;
import jakarta.servlet.http.HttpServletRequest;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import org.springframework.http.CacheControl;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.util.UriComponentsBuilder;

/** Sinh ảnh QR cho tài sản khi cần; không lưu ảnh. Nội dung là đường dẫn tới trang phòng (cần đăng nhập). */
public final class AssetQr {

    private static final int SIZE = 256;

    private AssetQr() {}

    public static ResponseEntity<byte[]> response(RoomAsset asset, HttpServletRequest request) {
        String target = UriComponentsBuilder.fromUriString(baseUrl(request))
            .path("/rooms/{roomId}")
            .queryParam("asset", asset.assetCode())
            .buildAndExpand(asset.roomId())
            .encode()
            .toUriString();
        try {
            BitMatrix matrix = new QRCodeWriter().encode(target, BarcodeFormat.QR_CODE, SIZE, SIZE);
            ByteArrayOutputStream png = new ByteArrayOutputStream();
            MatrixToImageWriter.writeToStream(matrix, "PNG", png);
            return ResponseEntity.ok()
                .cacheControl(CacheControl.noStore())
                .contentType(MediaType.IMAGE_PNG)
                .body(png.toByteArray());
        } catch (WriterException | IOException ex) {
            throw new IllegalStateException("Unable to generate QR code", ex);
        }
    }

    private static String baseUrl(HttpServletRequest request) {
        int port = request.getServerPort();
        boolean defaultPort = port <= 0 || port == 80 || port == 443;
        UriComponentsBuilder builder = UriComponentsBuilder.newInstance()
            .scheme(request.getScheme())
            .host(request.getServerName());
        if (!defaultPort) {
            builder.port(port);
        }
        return builder.build().toUriString();
    }
}
