package server.service;

import com.cloudinary.Cloudinary;
import com.cloudinary.ProgressCallback;
import com.cloudinary.Uploader;
import com.cloudinary.strategies.AbstractUploaderStrategy;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLConnection;
import java.net.URLStreamHandler;
import java.net.URLStreamHandlerFactory;
import java.nio.charset.StandardCharsets;
import java.util.ArrayDeque;
import java.util.Base64;
import java.util.Deque;
import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class CloudinaryServiceTest {
    private static final String PROP_SIGNED_CLOUD_NAME = "cloudinary.cloud.name";
    private static final String PROP_SIGNED_API_KEY = "cloudinary.api.key";
    private static final String PROP_SIGNED_API_SECRET = "cloudinary.api.secret";
    private static final String PROP_UNSIGNED_CLOUD_NAME = "cloudinary.unsigned.cloud.name";
    private static final String PROP_UPLOAD_PRESET = "cloudinary.upload.preset";

    private static final Deque<FakeHttpsResponse> HTTPS_RESPONSES = new ArrayDeque<>();
    private static volatile boolean fakeHttpsInstalled;

    private final Map<String, String> oldProperties = new HashMap<>();

    @BeforeAll
    static void installHttpsHandlerOnce() {
        try {
            URL.setURLStreamHandlerFactory(new FakeHttpsHandlerFactory());
            fakeHttpsInstalled = true;
        } catch (Error ignored) {
            fakeHttpsInstalled = false;
        }
    }

    @BeforeEach
    void saveAndClearRelevantProperties() {
        remember(PROP_SIGNED_CLOUD_NAME);
        remember(PROP_SIGNED_API_KEY);
        remember(PROP_SIGNED_API_SECRET);
        remember(PROP_UNSIGNED_CLOUD_NAME);
        remember(PROP_UPLOAD_PRESET);

        System.clearProperty(PROP_SIGNED_CLOUD_NAME);
        System.clearProperty(PROP_SIGNED_API_KEY);
        System.clearProperty(PROP_SIGNED_API_SECRET);
        System.clearProperty(PROP_UNSIGNED_CLOUD_NAME);
        System.clearProperty(PROP_UPLOAD_PRESET);
        synchronized (HTTPS_RESPONSES) {
            HTTPS_RESPONSES.clear();
        }
    }

    @AfterEach
    void restoreRelevantProperties() {
        restore(PROP_SIGNED_CLOUD_NAME);
        restore(PROP_SIGNED_API_KEY);
        restore(PROP_SIGNED_API_SECRET);
        restore(PROP_UNSIGNED_CLOUD_NAME);
        restore(PROP_UPLOAD_PRESET);
        synchronized (HTTPS_RESPONSES) {
            HTTPS_RESPONSES.clear();
        }
    }

    @Test
    void constructorUsesDefaultsAndBuildsSignedClientWhenConfigured() throws Exception {
        CloudinaryService defaults = new CloudinaryService();
        assertEquals("drskvdpz2", field(defaults, "unsignedCloudName"));
        assertEquals("manh_le_duy", field(defaults, "unsignedUploadPreset"));
        assertEquals(false, field(defaults, "signedUploadEnabled"));
        assertNull(field(defaults, "cloudinary"));

        enableSignedProperties();
        System.setProperty(PROP_UNSIGNED_CLOUD_NAME, " custom-cloud ");
        System.setProperty(PROP_UPLOAD_PRESET, " custom-preset ");

        CloudinaryService configured = new CloudinaryService();
        assertEquals(true, field(configured, "signedUploadEnabled"));
        assertNotNull(field(configured, "cloudinary"));
        assertEquals("custom-cloud", field(configured, "unsignedCloudName"));
        assertEquals("custom-preset", field(configured, "unsignedUploadPreset"));
    }

    @Test
    void helperMethodsNormalizeAndSanitizeAsExpected() throws Exception {
        CloudinaryService service = new CloudinaryService();

        String prefixed = (String) invoke(service, "normalizeBase64", new Class<?>[] { String.class }, "AQID");
        assertTrue(prefixed.startsWith("data:image/jpeg;base64,"));

        String unchanged = (String) invoke(service, "normalizeBase64", new Class<?>[] { String.class },
                "data:image/png;base64,AAAA");
        assertEquals("data:image/png;base64,AAAA", unchanged);

        assertThrows(IllegalArgumentException.class,
                () -> invoke(service, "normalizeBase64", new Class<?>[] { String.class }, "   "));

        assertEquals("auction-item",
                invoke(service, "resolveSafeAssetName", new Class<?>[] { String.class }, (Object) null));
        assertEquals("x-y-z",
                invoke(service, "resolveSafeAssetName", new Class<?>[] { String.class }, "../x:y z??"));
        assertEquals("auction-item",
                invoke(service, "resolveSafeAssetName", new Class<?>[] { String.class }, "..."));
    }

    @Test
    void validateImagePayloadCoversAllMajorBranches() throws Exception {
        CloudinaryService service = new CloudinaryService();

        assertThrows(IllegalArgumentException.class,
                () -> invoke(service, "validateImagePayload", new Class<?>[] { String.class }, "invalid"));

        assertThrows(IllegalArgumentException.class,
                () -> invoke(service, "validateImagePayload", new Class<?>[] { String.class },
                        "data:text/plain;base64,QQ=="));

        assertThrows(IllegalArgumentException.class,
                () -> invoke(service, "validateImagePayload", new Class<?>[] { String.class },
                        "data:image/tiff;base64,QQ=="));

        assertThrows(IllegalArgumentException.class,
                () -> invoke(service, "validateImagePayload", new Class<?>[] { String.class },
                        "data:image/png;base64,@@@@"));

        assertThrows(IllegalArgumentException.class,
                () -> invoke(service, "validateImagePayload", new Class<?>[] { String.class },
                        "data:image/png;base64,"));

        byte[] oversized = new byte[(15 * 1024 * 1024) + 1];
        String oversizedDataUri = "data:image/jpeg;base64," + Base64.getEncoder().encodeToString(oversized);
        assertThrows(IllegalArgumentException.class,
                () -> invoke(service, "validateImagePayload", new Class<?>[] { String.class }, oversizedDataUri));

        String valid = "data:image/webp;base64," + Base64.getEncoder().encodeToString(new byte[] { 1, 2, 3 });
        assertDoesNotThrow(() -> invoke(service, "validateImagePayload", new Class<?>[] { String.class }, valid));
    }

    @Test
    void parsingAndExtractionHelpersHandleSuccessAndFallbackCases() throws Exception {
        CloudinaryService service = new CloudinaryService();

        assertEquals("folder/item", invoke(service, "extractPublicIdFromUrl", new Class<?>[] { String.class },
                "https://res.cloudinary.com/demo/image/upload/v123/folder/item.png"));
        assertEquals("folder/item", invoke(service, "extractPublicIdFromUrl", new Class<?>[] { String.class },
                "https://res.cloudinary.com/demo/image/upload/v123/folder/item"));
        assertNull(invoke(service, "extractPublicIdFromUrl", new Class<?>[] { String.class },
                "https://res.cloudinary.com/demo/image/no-upload/v123/folder/item.png"));
        assertNull(invoke(service, "extractPublicIdFromUrl", new Class<?>[] { String.class },
                "https://res.cloudinary.com/demo/image/upload/v123"));
        assertNull(invoke(service, "extractPublicIdFromUrl", new Class<?>[] { String.class }, (Object) null));

        assertEquals("Khong nhan duoc noi dung loi tu Cloudinary",
                invoke(service, "extractCloudinaryError", new Class<?>[] { String.class }, ""));
        assertEquals("upload failed",
                invoke(service, "extractCloudinaryError", new Class<?>[] { String.class },
                        "{\"error\":{\"message\":\"upload failed\"}}"));
        assertEquals("raw-error",
                invoke(service, "extractCloudinaryError", new Class<?>[] { String.class }, "raw-error"));
    }

    @Test
    void responseAndFormHelpersEncodeAndReadStreamsCorrectly() throws Exception {
        CloudinaryService service = new CloudinaryService();
        StringBuilder body = new StringBuilder();

        invoke(service, "appendFormField", new Class<?>[] { StringBuilder.class, String.class, String.class },
                body, "a b", "x+y");
        invoke(service, "appendFormField", new Class<?>[] { StringBuilder.class, String.class, String.class },
                body, "k2", "v2");
        assertEquals("a+b=x%2By&k2=v2", body.toString());

        FakeHttpURLConnection successConnection = new FakeHttpURLConnection("ok-body", "err-body", 200);
        String successBody = (String) invoke(service, "readResponseBody",
                new Class<?>[] { HttpURLConnection.class, boolean.class }, successConnection, false);
        assertEquals("ok-body", successBody);

        FakeHttpURLConnection errorConnection = new FakeHttpURLConnection("ok-body", "err-body", 500);
        String errorBody = (String) invoke(service, "readResponseBody",
                new Class<?>[] { HttpURLConnection.class, boolean.class }, errorConnection, true);
        assertEquals("err-body", errorBody);

        FakeHttpURLConnection nullErrorStreamConnection = new FakeHttpURLConnection("ok-body", null, 500);
        String emptyErrorBody = (String) invoke(service, "readResponseBody",
                new Class<?>[] { HttpURLConnection.class, boolean.class }, nullErrorStreamConnection, true);
        assertEquals("", emptyErrorBody);
    }

    @Test
    void uploadImageWrapsValidationAndConfigurationFailures() throws Exception {
        CloudinaryService service = new CloudinaryService();
        RuntimeException emptyPayloadError = assertThrows(RuntimeException.class,
                () -> service.uploadImage("   ", "item-1"));
        assertTrue(emptyPayloadError.getMessage().contains("Lỗi upload ảnh lên Cloudinary"));
        assertInstanceOf(IllegalArgumentException.class, emptyPayloadError.getCause());

        setField(service, "unsignedCloudName", " ");
        setField(service, "unsignedUploadPreset", "");
        RuntimeException missingConfigError = assertThrows(RuntimeException.class,
                () -> service.uploadImage("data:image/png;base64,AQID", "item-2"));
        assertInstanceOf(IllegalStateException.class, missingConfigError.getCause());
    }

    @Test
    void signedUploadSuccessAndDeleteImageSuccessPath() throws Exception {
        enableSignedProperties();
        CloudinaryService service = new CloudinaryService();

        Uploader uploader = new Uploader(
                new Cloudinary(new HashMap<>()),
                new StubUploaderStrategy(
                        Map.of("secure_url", "https://cdn.example/signed.png"),
                        Map.of("result", "ok"),
                        null,
                        null));

        StubCloudinary stubCloudinary = new StubCloudinary(uploader);
        setField(service, "cloudinary", stubCloudinary);

        String signedUrl = service.uploadImage("data:image/png;base64,AQID", "folder/item");
        assertEquals("https://cdn.example/signed.png", signedUrl);

        String fromBytes = service.uploadImageBytes(new byte[] { 1, 2, 3 }, "folder/item");
        assertEquals("https://cdn.example/signed.png", fromBytes);

        assertTrue(service.deleteImage("https://res.cloudinary.com/demo/image/upload/v1/folder/item.png"));
    }

    @Test
    void signedUploadFallbackAndUnsignedUploadBranchesUseFakeHttps() throws Exception {
        Assumptions.assumeTrue(fakeHttpsInstalled,
                "Không thể cài URLStreamHandlerFactory trong JVM này để fake HTTPS.");

        enableSignedProperties();
        CloudinaryService service = new CloudinaryService();
        Uploader uploader = new Uploader(
                new Cloudinary(new HashMap<>()),
                new StubUploaderStrategy(
                        null,
                        Map.of("result", "not-ok"),
                        new RuntimeException("signed upload failure"),
                        null));
        setField(service, "cloudinary", new StubCloudinary(uploader));

        enqueueHttpsResponse(200, "{\"secure_url\":\"https://cdn.example/unsigned.png\"}", null);
        String unsignedUrl = service.uploadImage("data:image/png;base64,AQID", "folder/item");
        assertEquals("https://cdn.example/unsigned.png", unsignedUrl);

        enqueueHttpsResponse(400, null, "{\"error\":{\"message\":\"bad request\"}}");
        RuntimeException errorResponse = assertThrows(RuntimeException.class,
                () -> service.uploadImage("data:image/png;base64,AQID", "folder/item"));
        assertTrue(errorResponse.getMessage().contains("Lỗi upload ảnh lên Cloudinary"));

        enqueueHttpsResponse(200, "{}", null);
        RuntimeException missingSecureUrl = assertThrows(RuntimeException.class,
                () -> service.uploadImage("data:image/png;base64,AQID", "folder/item"));
        assertTrue(missingSecureUrl.getMessage().contains("Lỗi upload ảnh lên Cloudinary"));
    }

    @Test
    void deleteImageReturnsFalseForUnsupportedOrFailingFlows() throws Exception {
        CloudinaryService disabledSignedService = new CloudinaryService();
        assertFalse(disabledSignedService.deleteImage("https://res.cloudinary.com/demo/image/upload/v1/x.png"));

        enableSignedProperties();
        CloudinaryService enabledSignedService = new CloudinaryService();
        Uploader uploader = new Uploader(
                new Cloudinary(new HashMap<>()),
                new StubUploaderStrategy(
                        Map.of("secure_url", "https://cdn.example/signed.png"),
                        null,
                        null,
                        new RuntimeException("destroy failed")));
        setField(enabledSignedService, "cloudinary", new StubCloudinary(uploader));

        assertFalse(enabledSignedService.deleteImage(null));
        assertFalse(enabledSignedService.deleteImage("   "));
        assertFalse(enabledSignedService.deleteImage("https://res.cloudinary.com/demo/image/not-upload/v1/x.png"));
        assertFalse(enabledSignedService.deleteImage("https://res.cloudinary.com/demo/image/upload/v1/folder/item.png"));
    }

    @Test
    void extractSecureUrlAndTextHelpersCoverEdgeCases() throws Exception {
        CloudinaryService service = new CloudinaryService();

        Map<String, Object> ok = new HashMap<>();
        ok.put("secure_url", "https://cdn.example/img.png");
        assertEquals("https://cdn.example/img.png",
                invoke(service, "extractSecureUrl", new Class<?>[] { Map.class }, ok));

        Map<String, Object> blank = new HashMap<>();
        blank.put("secure_url", " ");
        assertThrows(RuntimeException.class,
                () -> invoke(service, "extractSecureUrl", new Class<?>[] { Map.class }, blank));
        assertThrows(RuntimeException.class,
                () -> invoke(service, "extractSecureUrl", new Class<?>[] { Map.class }, (Object) null));

        assertEquals(true, invokeStatic("hasText", new Class<?>[] { String.class }, " abc "));
        assertEquals(false, invokeStatic("hasText", new Class<?>[] { String.class }, "   "));
        assertEquals(null, invokeStatic("getOptionalConfig", new Class<?>[] { String.class, String.class },
                "missing.property", "MISSING_ENV"));
    }

    private void enableSignedProperties() {
        System.setProperty(PROP_SIGNED_CLOUD_NAME, "demo-cloud");
        System.setProperty(PROP_SIGNED_API_KEY, "demo-key");
        System.setProperty(PROP_SIGNED_API_SECRET, "demo-secret");
    }

    private static void enqueueHttpsResponse(int responseCode, String body, String errorBody) {
        synchronized (HTTPS_RESPONSES) {
            HTTPS_RESPONSES.addLast(new FakeHttpsResponse(responseCode, body, errorBody));
        }
    }

    private void remember(String key) {
        oldProperties.put(key, System.getProperty(key));
    }

    private void restore(String key) {
        String value = oldProperties.get(key);
        if (value == null) {
            System.clearProperty(key);
        } else {
            System.setProperty(key, value);
        }
    }

    private Object field(Object target, String fieldName) throws Exception {
        Field field = CloudinaryService.class.getDeclaredField(fieldName);
        field.setAccessible(true);
        return field.get(target);
    }

    private void setField(Object target, String fieldName, Object value) throws Exception {
        Field field = CloudinaryService.class.getDeclaredField(fieldName);
        field.setAccessible(true);
        field.set(target, value);
    }

    private Object invokeStatic(String methodName, Class<?>[] parameterTypes, Object... args) throws Exception {
        Method method = CloudinaryService.class.getDeclaredMethod(methodName, parameterTypes);
        method.setAccessible(true);
        try {
            return method.invoke(null, args);
        } catch (InvocationTargetException e) {
            Throwable cause = e.getCause();
            if (cause instanceof RuntimeException runtimeException) {
                throw runtimeException;
            }
            if (cause instanceof Error error) {
                throw error;
            }
            throw new RuntimeException(cause);
        }
    }

    private Object invoke(Object target, String methodName, Class<?>[] parameterTypes, Object... args) throws Exception {
        Method method = CloudinaryService.class.getDeclaredMethod(methodName, parameterTypes);
        method.setAccessible(true);
        try {
            return method.invoke(target, args);
        } catch (InvocationTargetException e) {
            Throwable cause = e.getCause();
            if (cause instanceof RuntimeException runtimeException) {
                throw runtimeException;
            }
            if (cause instanceof Error error) {
                throw error;
            }
            throw new RuntimeException(cause);
        }
    }

    private static final class StubCloudinary extends Cloudinary {
        private final Uploader uploader;

        StubCloudinary(Uploader uploader) {
            super(new HashMap<>());
            this.uploader = uploader;
        }

        @Override
        public Uploader uploader() {
            return uploader;
        }
    }

    private static final class StubUploaderStrategy extends AbstractUploaderStrategy {
        private final Map<String, Object> uploadResult;
        private final Map<String, Object> destroyResult;
        private final RuntimeException uploadError;
        private final RuntimeException destroyError;

        StubUploaderStrategy(Map<String, Object> uploadResult,
                             Map<String, Object> destroyResult,
                             RuntimeException uploadError,
                             RuntimeException destroyError) {
            this.uploadResult = uploadResult;
            this.destroyResult = destroyResult;
            this.uploadError = uploadError;
            this.destroyError = destroyError;
        }

        @Override
        public Map callApi(String action,
                           Map<String, Object> params,
                           Map options,
                           Object file,
                           ProgressCallback progressCallback) throws IOException {
            if ("upload".equals(action)) {
                if (uploadError != null) {
                    throw uploadError;
                }
                return uploadResult;
            }
            if ("destroy".equals(action)) {
                if (destroyError != null) {
                    throw destroyError;
                }
                return destroyResult;
            }
            return Map.of();
        }
    }

    private static final class FakeHttpsHandlerFactory implements URLStreamHandlerFactory {
        @Override
        public URLStreamHandler createURLStreamHandler(String protocol) {
            if (!"https".equals(protocol)) {
                return null;
            }
            return new URLStreamHandler() {
                @Override
                protected URLConnection openConnection(URL url) throws IOException {
                    if (!"api.cloudinary.com".equalsIgnoreCase(url.getHost())) {
                        throw new IOException("Unexpected HTTPS host in test: " + url.getHost());
                    }
                    FakeHttpsResponse response;
                    synchronized (HTTPS_RESPONSES) {
                        response = HTTPS_RESPONSES.pollFirst();
                    }
                    if (response == null) {
                        throw new IOException("No fake HTTPS response queued for " + url);
                    }
                    return new FakeHttpsURLConnection(url, response);
                }
            };
        }
    }

    private static final class FakeHttpsResponse {
        private final int code;
        private final String body;
        private final String errorBody;

        private FakeHttpsResponse(int code, String body, String errorBody) {
            this.code = code;
            this.body = body;
            this.errorBody = errorBody;
        }
    }

    private static final class FakeHttpsURLConnection extends HttpURLConnection {
        private final InputStream inputStream;
        private final InputStream errorStream;
        private final int responseCode;
        private final ByteArrayOutputStream outputStream = new ByteArrayOutputStream();

        FakeHttpsURLConnection(URL url, FakeHttpsResponse response) {
            super(url);
            this.responseCode = response.code;
            this.inputStream = response.body == null ? null
                    : new ByteArrayInputStream(response.body.getBytes(StandardCharsets.UTF_8));
            this.errorStream = response.errorBody == null ? null
                    : new ByteArrayInputStream(response.errorBody.getBytes(StandardCharsets.UTF_8));
        }

        @Override
        public void disconnect() {
        }

        @Override
        public boolean usingProxy() {
            return false;
        }

        @Override
        public void connect() {
        }

        @Override
        public InputStream getInputStream() {
            return inputStream;
        }

        @Override
        public InputStream getErrorStream() {
            return errorStream;
        }

        @Override
        public int getResponseCode() {
            return responseCode;
        }

        @Override
        public OutputStream getOutputStream() {
            return outputStream;
        }
    }

    private static final class FakeHttpURLConnection extends HttpURLConnection {
        private final InputStream inputStream;
        private final InputStream errorStream;
        private final int responseCode;
        private final ByteArrayOutputStream outputStream = new ByteArrayOutputStream();

        FakeHttpURLConnection(String inputBody, String errorBody, int responseCode) throws Exception {
            super(new URL("http://localhost"));
            this.inputStream = inputBody == null ? null
                    : new ByteArrayInputStream(inputBody.getBytes(StandardCharsets.UTF_8));
            this.errorStream = errorBody == null ? null
                    : new ByteArrayInputStream(errorBody.getBytes(StandardCharsets.UTF_8));
            this.responseCode = responseCode;
        }

        @Override
        public void disconnect() {
        }

        @Override
        public boolean usingProxy() {
            return false;
        }

        @Override
        public void connect() {
        }

        @Override
        public InputStream getInputStream() {
            return inputStream;
        }

        @Override
        public InputStream getErrorStream() {
            return errorStream;
        }

        @Override
        public int getResponseCode() {
            return responseCode;
        }

        @Override
        public OutputStream getOutputStream() {
            return outputStream;
        }
    }
}
