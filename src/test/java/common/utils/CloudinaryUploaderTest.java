package common.utils;

import org.junit.jupiter.api.Test;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CloudinaryUploaderTest {

    @Test
    void constructorAndMainCanRun() {
        assertNotNull(new CloudinaryUploader());

        PrintStream originalOut = System.out;
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        try {
            System.setOut(new PrintStream(out, true, StandardCharsets.UTF_8));
            CloudinaryUploader.main(new String[0]);
        } finally {
            System.setOut(originalOut);
        }

        String output = out.toString(StandardCharsets.UTF_8);
        assertTrue(output.contains("Working Directory:"));
        assertTrue(output.contains("CLOUD_NAME: drskvdpz2"));
        assertTrue(output.contains("CLOUDINARY_UPLOAD_PRESET: manh_le_duy"));
    }

    @Test
    void uploadReturnsNullWhenFileDoesNotExist() {
        File missing = new File("target/non-existing-image.png");

        PrintStream originalErr = System.err;
        ByteArrayOutputStream err = new ByteArrayOutputStream();
        try {
            System.setErr(new PrintStream(err, true, StandardCharsets.UTF_8));
            assertNull(CloudinaryUploader.upload(missing));
        } finally {
            System.setErr(originalErr);
        }

        assertTrue(err.toString(StandardCharsets.UTF_8).contains("NoSuchFileException"));
    }

    @Test
    void uploadReturnsNullWhenHttpsProxyIsUnreachable() throws Exception {
        File image = File.createTempFile("cloudinary-uploader-", ".png");
        image.deleteOnExit();
        Files.write(image.toPath(), new byte[] { 1, 2, 3, 4, 5 });

        String oldProxyHost = System.getProperty("https.proxyHost");
        String oldProxyPort = System.getProperty("https.proxyPort");
        PrintStream originalErr = System.err;
        ByteArrayOutputStream err = new ByteArrayOutputStream();
        try {
            System.setProperty("https.proxyHost", "127.0.0.1");
            System.setProperty("https.proxyPort", "1");
            System.setErr(new PrintStream(err, true, StandardCharsets.UTF_8));

            assertNull(CloudinaryUploader.upload(image));
        } finally {
            restoreProperty("https.proxyHost", oldProxyHost);
            restoreProperty("https.proxyPort", oldProxyPort);
            System.setErr(originalErr);
        }

        String errorOutput = err.toString(StandardCharsets.UTF_8);
        assertTrue(errorOutput.contains("ConnectException")
                || errorOutput.contains("SocketException")
                || errorOutput.contains("IOException"));
    }

    private void restoreProperty(String key, String value) {
        if (value == null) {
            System.clearProperty(key);
        } else {
            System.setProperty(key, value);
        }
    }
}
