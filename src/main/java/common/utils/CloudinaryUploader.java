package common.utils;

import java.io.File;
import java.io.IOException;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

/**
 * Upload ảnh lên Cloudinary (unsigned upload với upload preset).
 */
public class CloudinaryUploader {
    private static final String CLOUD_NAME = "drskvdpz2";
    private static final String CLOUDINARY_UPLOAD_PRESET = "manh_le_duy";
    private static final String cloudURL = "https://api.cloudinary.com/v1_1/" + CLOUD_NAME + "/image/upload";

    public static void main(String[] args) {
        System.out.println("Working Directory: " + System.getProperty("user.dir"));
        System.out.println("CLOUD_NAME: " + CLOUD_NAME);
        System.out.println("CLOUDINARY_UPLOAD_PRESET: " + CLOUDINARY_UPLOAD_PRESET);
    }

    /**
     * Upload file ảnh lên Cloudinary.
     *
     * @param file File ảnh cần upload
     * @return URL của ảnh trên Cloudinary, null nếu lỗi
     */
    public static String upload(File file) {
        try {
            // Kiểm tra cấu hình trước khi upload
            if (CLOUD_NAME == null || CLOUD_NAME.isEmpty()) {
                String workingDir = System.getProperty("user.dir");
                System.err.println("LỖI: Không tìm thấy cấu hình CLOUD_NAME.");
                System.err.println("Đang chạy tại: " + workingDir);
                System.err.println("Hãy đảm bảo file .env tồn tại trong thư mục trên.");
                return null;
            }
            if (CLOUDINARY_UPLOAD_PRESET == null || CLOUDINARY_UPLOAD_PRESET.isEmpty()) {
                System.err.println("LỖI: CLOUDINARY_UPLOAD_PRESET chưa được cấu hình. Hãy kiểm tra file .env");
                return null;
            }

            System.out.println("Đang upload lên Cloudinary (Cloud: " + CLOUD_NAME + ")...");

            // Đọc file thành bytes
            byte[] fileBytes = Files.readAllBytes(file.toPath());
            String fileName = file.getName();
            String ext = fileName.substring(fileName.lastIndexOf(".") + 1);

            // Tạo multipart body
            String boundary = "---" + System.currentTimeMillis();
            String lineEnd = "\r\n";
            String twoHyphens = "--";

            StringBuilder sb = new StringBuilder();
            sb.append(twoHyphens).append(boundary).append(lineEnd);
            sb.append("Content-Disposition: form-data; name=\"file\"; filename=\"")
              .append(fileName).append("\"").append(lineEnd);
            sb.append("Content-Type: image/").append(ext).append(lineEnd);
            sb.append(lineEnd);

            byte[] headerBytes = sb.toString().getBytes(StandardCharsets.UTF_8);

            // Footer
            StringBuilder footerSb = new StringBuilder();
            footerSb.append(lineEnd);
            footerSb.append(twoHyphens).append(boundary).append(lineEnd);
            footerSb.append("Content-Disposition: form-data; name=\"upload_preset\"").append(lineEnd);
            footerSb.append(lineEnd);
            footerSb.append(CLOUDINARY_UPLOAD_PRESET).append(lineEnd);

            footerSb.append(twoHyphens).append(boundary).append(twoHyphens).append(lineEnd);

            byte[] footerBytes = footerSb.toString().getBytes(StandardCharsets.UTF_8);

            // Tổng size
            int totalSize = headerBytes.length + fileBytes.length + footerBytes.length;
            byte[] body = new byte[totalSize];
            System.arraycopy(headerBytes, 0, body, 0, headerBytes.length);
            System.arraycopy(fileBytes, 0, body, headerBytes.length, fileBytes.length);
            System.arraycopy(footerBytes, 0, body, headerBytes.length + fileBytes.length, footerBytes.length);

            // Gửi request
            URL url = new URL("https://api.cloudinary.com/v1_1/" + CLOUD_NAME + "/image/upload");
            HttpURLConnection conn = (HttpURLConnection) url.openConnection();
            conn.setRequestMethod("POST");
            conn.setDoOutput(true);
            conn.setRequestProperty("Content-Type", "multipart/form-data; boundary=" + boundary);
            conn.setRequestProperty("Content-Length", String.valueOf(totalSize));
            conn.getOutputStream().write(body);

            // Đọc response
            int code = conn.getResponseCode();
            if (code == 200) {
                String response = new String(conn.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
                JsonObject json = JsonParser.parseString(response).getAsJsonObject();
                return json.get("secure_url").getAsString(); // lấy URL
            } else {
                String error = new String(conn.getErrorStream().readAllBytes(), StandardCharsets.UTF_8);
                System.err.println("Cloudinary upload error: " + code + " - " + error);
                return null;
            }

        } catch (IOException e) {
            e.printStackTrace();
            return null;
        }
    }
}