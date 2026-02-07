package com.muyu.blog.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.HexFormat;

@Slf4j
@Service
public class TencentOcrService {

    private static final String HOST = "ocr.tencentcloudapi.com";
    private static final String ENDPOINT = "https://ocr.tencentcloudapi.com";
    private static final String SERVICE = "ocr";
    private static final String VERSION = "2018-11-19";
    private static final String ACTION_GENERAL_ACCURATE_OCR = "GeneralAccurateOCR";
    private static final String ALGORITHM = "TC3-HMAC-SHA256";
    private static final String SIGNED_HEADERS = "content-type;host";

    @Value("${tencent.ocr.secret-id}")
    private String secretId;

    @Value("${tencent.ocr.secret-key}")
    private String secretKey;

    @Value("${tencent.ocr.region:ap-guangzhou}")
    private String region;

    /**
     * 临时密钥才需要（可选）。
     */
    @Value("${tencent.ocr.token:}")
    private String token;

    @Value("${tencent.ocr.language:zh-CN}")
    private String language;

    /**
     * 按示例：PDF base64 + 指定页码调用 GeneralAccurateOCR。
     * pdfBase64 建议传“纯 base64”（不含 data: 前缀）；但为了贴合你给的示例，这里会自动补 data 前缀。
     */
    public String generalAccurateOcrPdfBase64(String pdfBase64, int pdfPageNumber) {
        if (secretId == null || secretId.isBlank() || secretKey == null || secretKey.isBlank()) {
            throw new IllegalStateException("缺少腾讯云 OCR 凭证，请在 application.properties 配置 tencent.ocr.secret-id / tencent.ocr.secret-key");
        }
        if (pdfBase64 == null) {
            pdfBase64 = "";
        }

        long timestamp = Instant.now().getEpochSecond();
        LocalDate date = Instant.ofEpochSecond(timestamp).atZone(ZoneOffset.UTC).toLocalDate();
        String dateStr = date.toString(); // YYYY-MM-DD (UTC)

        String imageBase64 = pdfBase64.startsWith("data:") ? pdfBase64 : ("data:application/pdf;base64," + pdfBase64);
        String payload = "{\"IsPdf\":true,\"PdfPageNumber\":" + pdfPageNumber + ",\"ImageBase64\":\"" + escapeJson(imageBase64) + "\"}";

        String authorization = buildAuthorization(payload, timestamp, dateStr);

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.set("Host", HOST);
        headers.set("X-TC-Action", ACTION_GENERAL_ACCURATE_OCR);
        headers.set("X-TC-Timestamp", String.valueOf(timestamp));
        headers.set("X-TC-Version", VERSION);
        headers.set("X-TC-Region", region);
        headers.set("X-TC-Language", language);
        if (token != null && !token.isBlank()) {
            headers.set("X-TC-Token", token);
        }
        headers.set("Authorization", authorization);

        RestTemplate restTemplate = new RestTemplate();
        HttpEntity<String> entity = new HttpEntity<>(payload, headers);
        ResponseEntity<String> resp = restTemplate.postForEntity(ENDPOINT, entity, String.class);
        return resp.getBody();
    }

    /**
     * 图片（jpg/png）base64 调用 GeneralAccurateOCR。
     * imageBase64 建议传“纯 base64”（不含 data: 前缀）；内部会自动补 data 前缀。
     *
     * @param mime 例如 image/png 或 image/jpeg（为空则默认 image/jpeg）
     */
    public String generalAccurateOcrImageBase64(String imageBase64, String mime) {
        if (secretId == null || secretId.isBlank() || secretKey == null || secretKey.isBlank()) {
            throw new IllegalStateException("缺少腾讯云 OCR 凭证，请在 application.properties 配置 tencent.ocr.secret-id / tencent.ocr.secret-key");
        }
        if (imageBase64 == null) {
            imageBase64 = "";
        }
        String m = (mime == null || mime.isBlank()) ? "image/jpeg" : mime.trim();

        long timestamp = Instant.now().getEpochSecond();
        LocalDate date = Instant.ofEpochSecond(timestamp).atZone(ZoneOffset.UTC).toLocalDate();
        String dateStr = date.toString();

        String dataUriPrefix = "data:" + m + ";base64,";
        String b64 = imageBase64.startsWith("data:") ? imageBase64 : (dataUriPrefix + imageBase64);
        String payload = "{\"ImageBase64\":\"" + escapeJson(b64) + "\"}";

        String authorization = buildAuthorization(payload, timestamp, dateStr);

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.set("Host", HOST);
        headers.set("X-TC-Action", ACTION_GENERAL_ACCURATE_OCR);
        headers.set("X-TC-Timestamp", String.valueOf(timestamp));
        headers.set("X-TC-Version", VERSION);
        headers.set("X-TC-Region", region);
        headers.set("X-TC-Language", language);
        if (token != null && !token.isBlank()) {
            headers.set("X-TC-Token", token);
        }
        headers.set("Authorization", authorization);

        RestTemplate restTemplate = new RestTemplate();
        HttpEntity<String> entity = new HttpEntity<>(payload, headers);
        ResponseEntity<String> resp = restTemplate.postForEntity(ENDPOINT, entity, String.class);
        return resp.getBody();
    }

    private String buildAuthorization(String payload, long timestamp, String dateStr) {
        try {
            String canonicalRequest = buildCanonicalRequest(payload);
            String credentialScope = dateStr + "/" + SERVICE + "/tc3_request";
            String stringToSign = ALGORITHM + "\n"
                    + timestamp + "\n"
                    + credentialScope + "\n"
                    + sha256Hex(canonicalRequest);

            byte[] secretDate = hmacSha256(("TC3" + secretKey).getBytes(StandardCharsets.UTF_8), dateStr);
            byte[] secretService = hmacSha256(secretDate, SERVICE);
            byte[] secretSigning = hmacSha256(secretService, "tc3_request");
            String signature = HexFormat.of().formatHex(hmacSha256(secretSigning, stringToSign));

            return ALGORITHM
                    + " Credential=" + secretId + "/" + credentialScope
                    + ", SignedHeaders=" + SIGNED_HEADERS
                    + ", Signature=" + signature;
        } catch (Exception e) {
            log.error("Build Tencent OCR Authorization failed", e);
            throw new RuntimeException("Build Tencent OCR Authorization failed: " + e.getMessage(), e);
        }
    }

    private String buildCanonicalRequest(String payload) throws Exception {
        String httpRequestMethod = "POST";
        String canonicalUri = "/";
        String canonicalQueryString = "";
        String canonicalHeaders =
                "content-type:application/json\n" +
                "host:" + HOST + "\n";
        String hashedRequestPayload = sha256Hex(payload);
        return httpRequestMethod + "\n"
                + canonicalUri + "\n"
                + canonicalQueryString + "\n"
                + canonicalHeaders + "\n"
                + SIGNED_HEADERS + "\n"
                + hashedRequestPayload;
    }

    private static byte[] hmacSha256(byte[] key, String msg) throws Exception {
        Mac mac = Mac.getInstance("HmacSHA256");
        mac.init(new SecretKeySpec(key, "HmacSHA256"));
        return mac.doFinal(msg.getBytes(StandardCharsets.UTF_8));
    }

    private static String sha256Hex(String s) throws Exception {
        MessageDigest md = MessageDigest.getInstance("SHA-256");
        byte[] digest = md.digest(s.getBytes(StandardCharsets.UTF_8));
        return HexFormat.of().formatHex(digest);
    }

    private static String escapeJson(String s) {
        // 足够用于 base64/data-uri 场景
        return s.replace("\\", "\\\\").replace("\"", "\\\"");
    }
}


