package server.service;

import com.cloudinary.Cloudinary;
import com.cloudinary.utils.ObjectUtils;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;

/**
 * Service for uploading images to Cloudinary.
 *
 * Supported configuration:
 * Signed upload:
 *   -Dcloudinary.cloud.name / CLOUDINARY_CLOUD_NAME
 *   -Dcloudinary.api.key / CLOUDINARY_API_KEY
 *   -Dcloudinary.api.secret / CLOUDINARY_API_SECRET
 *
 * Unsigned upload:
 *   -Dcloudinary.unsigned.cloud.name / CLOUDINARY_UNSIGNED_CLOUD_NAME
 *   -Dcloudinary.upload.preset / CLOUDINARY_UPLOAD_PRESET
 */
public class CloudinaryService {
    private static final int MAX_IMAGE_BYTES = 15 * 1024 * 1024;

    private static final String DEFAULT_UNSIGNED_CLOUD_NAME = "drskvdpz2";
    private static final String DEFAULT_UNSIGNED_UPLOAD_PRESET = "manh_le_duy";

    private final Cloudinary cloudinary;
    private final String unsignedCloudName;
    private final String unsignedUploadPreset;
    private final boolean signedUploadEnabled;

    public CloudinaryService() {
        String cloudName = getOptionalConfig("cloudinary.cloud.name", "CLOUDINARY_CLOUD_NAME");
        String apiKey = getOptionalConfig("cloudinary.api.key", "CLOUDINARY_API_KEY");
        String apiSecret = getOptionalConfig("cloudinary.api.secret", "CLOUDINARY_API_SECRET");

        this.signedUploadEnabled = hasText(cloudName) && hasText(apiKey) && hasText(apiSecret);
        this.cloudinary = signedUploadEnabled
                ? new Cloudinary(ObjectUtils.asMap(
                        "cloud_name", cloudName,
                        "api_key", apiKey,
                        "api_secret", apiSecret))
                : null;

        this.unsignedCloudName = getConfig(
                "cloudinary.unsigned.cloud.name",
                "CLOUDINARY_UNSIGNED_CLOUD_NAME",
                DEFAULT_UNSIGNED_CLOUD_NAME);
        this.unsignedUploadPreset = getConfig(
                "cloudinary.upload.preset",
                "CLOUDINARY_UPLOAD_PRESET",
                DEFAULT_UNSIGNED_UPLOAD_PRESET);
    }

    public String uploadImage(String base64Image, String publicId) {
        try {
            String normalizedBase64 = normalizeBase64(base64Image);
            validateImagePayload(normalizedBase64);

            if (signedUploadEnabled) {
                try {
                    return uploadSigned(normalizedBase64, publicId);
                } catch (RuntimeException signedError) {
                    if (!canUseUnsignedFallback()) {
                        throw signedError;
                    }
                    System.err.println("Cloudinary signed upload failed, fallback to unsigned upload: "
                            + signedError.getMessage());
                }
            }

            if (canUseUnsignedFallback()) {
                return uploadUnsigned(normalizedBase64, publicId);
            }

            throw new IllegalStateException(
                    "Cloudinary chưa được cấu hình. Cần signed credentials hoặc upload preset cho unsigned upload.");
        } catch (Exception e) {
            throw new RuntimeException("Lỗi upload ảnh lên Cloudinary: " + e.getMessage(), e);
        }
    }

    public String uploadImageBytes(byte[] imageBytes, String publicId) {
        String base64 = Base64.getEncoder().encodeToString(imageBytes);
        return uploadImage(base64, publicId);
    }

    public boolean deleteImage(String imageUrl) {
        if (!signedUploadEnabled) {
            return false;
        }
        try {
            if (!hasText(imageUrl)) {
                return false;
            }
            String publicId = extractPublicIdFromUrl(imageUrl);
            if (publicId == null) {
                return false;
            }
            @SuppressWarnings("unchecked")
            Map<String, Object> result = cloudinary.uploader().destroy(publicId, ObjectUtils.emptyMap());
            return "ok".equals(result.get("result"));
        } catch (Exception e) {
            return false;
        }
    }

    private String uploadSigned(String normalizedBase64, String publicId) throws IOException {
        String safeAssetName = resolveSafeAssetName(publicId);
        Map<String, Object> params = new LinkedHashMap<>();
        params.put("asset_folder", "auction_items");
        params.put("resource_type", "image");
        params.put("public_id", safeAssetName);
        params.put("display_name", safeAssetName);
        params.put("filename_override", safeAssetName);

        @SuppressWarnings("unchecked")
        Map<String, Object> uploadResult = cloudinary.uploader().upload(normalizedBase64, params);
        return extractSecureUrl(uploadResult);
    }

    private String uploadUnsigned(String normalizedBase64, String publicId) throws IOException {
        String safeAssetName = resolveSafeAssetName(publicId);
        URL url = new URL("https://api.cloudinary.com/v1_1/" + unsignedCloudName + "/image/upload");
        HttpURLConnection connection = (HttpURLConnection) url.openConnection();
        connection.setRequestMethod("POST");
        connection.setDoOutput(true);
        connection.setRequestProperty("Content-Type", "application/x-www-form-urlencoded; charset=UTF-8");

        StringBuilder body = new StringBuilder();
        appendFormField(body, "file", normalizedBase64);
        appendFormField(body, "upload_preset", unsignedUploadPreset);
        appendFormField(body, "asset_folder", "auction_items");
        appendFormField(body, "public_id", safeAssetName);
        appendFormField(body, "filename_override", safeAssetName);

        byte[] requestBody = body.toString().getBytes(StandardCharsets.UTF_8);
        connection.setRequestProperty("Content-Length", String.valueOf(requestBody.length));
        try (OutputStream os = connection.getOutputStream()) {
            os.write(requestBody);
        }

        int responseCode = connection.getResponseCode();
        String responseBody = readResponseBody(connection, responseCode >= 400);
        if (responseCode >= 400) {
            throw new RuntimeException(extractCloudinaryError(responseBody));
        }

        JsonObject json = JsonParser.parseString(responseBody).getAsJsonObject();
        if (!json.has("secure_url") || json.get("secure_url").getAsString().isBlank()) {
            throw new RuntimeException("Cloudinary upload returned no secure_url");
        }
        return json.get("secure_url").getAsString();
    }

    private void appendFormField(StringBuilder body, String key, String value) throws IOException {
        if (body.length() > 0) {
            body.append('&');
        }
        body.append(URLEncoder.encode(key, StandardCharsets.UTF_8))
                .append('=')
                .append(URLEncoder.encode(value, StandardCharsets.UTF_8));
    }

    private String readResponseBody(HttpURLConnection connection, boolean errorStream) throws IOException {
        try (InputStream inputStream = errorStream ? connection.getErrorStream() : connection.getInputStream()) {
            if (inputStream == null) {
                return "";
            }
            return new String(inputStream.readAllBytes(), StandardCharsets.UTF_8);
        }
    }

    private String extractCloudinaryError(String responseBody) {
        if (!hasText(responseBody)) {
            return "Khong nhan duoc noi dung loi tu Cloudinary";
        }
        try {
            JsonObject root = JsonParser.parseString(responseBody).getAsJsonObject();
            if (root.has("error") && root.get("error").isJsonObject()) {
                JsonObject error = root.getAsJsonObject("error");
                if (error.has("message")) {
                    return error.get("message").getAsString();
                }
            }
        } catch (Exception ignored) {
        }
        return responseBody;
    }

    private String extractSecureUrl(Map<String, Object> uploadResult) {
        String secureUrl = uploadResult == null ? null : (String) uploadResult.get("secure_url");
        if (!hasText(secureUrl)) {
            throw new RuntimeException("Cloudinary upload returned no secure_url");
        }
        return secureUrl;
    }

    private boolean canUseUnsignedFallback() {
        return hasText(unsignedCloudName) && hasText(unsignedUploadPreset);
    }

    private String resolveSafeAssetName(String publicId) {
        String candidate = hasText(publicId) ? publicId.trim() : "auction-item";
        String sanitized = candidate.replace('\\', '-').replace('/', '-').replace(':', '-').replace(' ', '-');
        sanitized = sanitized.replaceAll("[^A-Za-z0-9._-]", "-");
        sanitized = sanitized.replaceAll("-{2,}", "-");
        sanitized = sanitized.replaceAll("^[.-]+|[.-]+$", "");
        return hasText(sanitized) ? sanitized : "auction-item";
    }

    private String normalizeBase64(String base64) {
        if (!hasText(base64)) {
            throw new IllegalArgumentException("Base64 image data cannot be empty");
        }
        if (base64.startsWith("data:image/")) {
            return base64;
        }
        return "data:image/jpeg;base64," + base64;
    }

    private void validateImagePayload(String dataUri) {
        int separatorIndex = dataUri.indexOf(',');
        if (separatorIndex <= 0) {
            throw new IllegalArgumentException("Du lieu anh khong hop le");
        }

        String metadata = dataUri.substring(0, separatorIndex).toLowerCase(Locale.ROOT);
        if (!metadata.startsWith("data:image/") || !metadata.contains(";base64")) {
            throw new IllegalArgumentException("Chi ho tro upload anh base64");
        }
        if (!(metadata.startsWith("data:image/jpeg")
                || metadata.startsWith("data:image/png")
                || metadata.startsWith("data:image/gif")
                || metadata.startsWith("data:image/bmp")
                || metadata.startsWith("data:image/webp"))) {
            throw new IllegalArgumentException("Chi ho tro anh JPG, PNG, GIF, BMP hoac WEBP");
        }

        String base64Content = dataUri.substring(separatorIndex + 1).trim();
        byte[] imageBytes;
        try {
            imageBytes = Base64.getDecoder().decode(base64Content);
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("Du lieu base64 cua anh khong hop le", e);
        }

        if (imageBytes.length == 0) {
            throw new IllegalArgumentException("Anh tai len dang rong");
        }
        if (imageBytes.length > MAX_IMAGE_BYTES) {
            throw new IllegalArgumentException("Anh phai nho hon 15MB");
        }
    }

    private String extractPublicIdFromUrl(String url) {
        try {
            String uploadMarker = "/upload/";
            int uploadIdx = url.indexOf(uploadMarker);
            if (uploadIdx < 0) {
                return null;
            }
            String afterUpload = url.substring(uploadIdx + uploadMarker.length());
            int versionEnd = afterUpload.indexOf('/');
            if (versionEnd < 0) {
                return null;
            }
            String pathWithExt = afterUpload.substring(versionEnd + 1);
            int extIdx = pathWithExt.lastIndexOf('.');
            if (extIdx > 0) {
                return pathWithExt.substring(0, extIdx);
            }
            return pathWithExt;
        } catch (Exception e) {
            return null;
        }
    }

    private static String getConfig(String propertyName, String envName, String defaultValue) {
        String value = getOptionalConfig(propertyName, envName);
        return hasText(value) ? value.trim() : defaultValue;
    }

    private static String getOptionalConfig(String propertyName, String envName) {
        String value = System.getProperty(propertyName);
        if (!hasText(value)) {
            value = System.getenv(envName);
        }
        return hasText(value) ? value.trim() : null;
    }

    private static boolean hasText(String value) {
        return value != null && !value.trim().isEmpty();
    }
}
