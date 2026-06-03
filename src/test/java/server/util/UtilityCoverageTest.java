package server.util;

import com.example.btl_n3.Main;
import common.utils.JsonUtils;

import common.utils.TimeUtils;
import common.utils.ValidationUtils;
import org.junit.jupiter.api.Test;
import server.config.DatabaseConnection;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.lang.reflect.Type;
import java.nio.charset.StandardCharsets;
import java.sql.SQLException;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

public class UtilityCoverageTest {

   
    @Test
    public void generateHashedPasswordsMainPrintsHashedFormat() {
        PrintStream originalOut = System.out;
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        try {
            System.setOut(new PrintStream(out));
            GenerateHashedPasswords.main(new String[0]);
        } finally {
            System.setOut(originalOut);
        }

        String output = out.toString();
        assertTrue(output.contains("admin123 ->"));
        assertTrue(output.contains("seller123 ->"));
        assertTrue(output.contains("bidder123 ->"));
        assertTrue(output.contains(":"));
    }

    @Test
    public void passwordUtilHashAndVerifyEdgeCases() {
        String hash = PasswordUtil.hash("secret123");
        assertTrue(hash.contains(":"));
        assertTrue(PasswordUtil.verify("secret123", hash));
        assertFalse(PasswordUtil.verify("wrong", hash));

        assertFalse(PasswordUtil.verify("x", null));
        assertFalse(PasswordUtil.verify("x", "plain-text"));
        assertThrows(IllegalArgumentException.class, () -> PasswordUtil.verify("x", "@@@:###"));
    }

    @Test
    public void migratePasswordsMainHandlesDatabaseFailureGracefully() {
        String oldUrl = System.getProperty("db.url");
        String oldUser = System.getProperty("db.user");
        String oldPass = System.getProperty("db.password");

        PrintStream originalErr = System.err;
        ByteArrayOutputStream err = new ByteArrayOutputStream();
        try {
            System.setProperty("db.url", "jdbc:mysql://127.0.0.1:1/auction_db?connectTimeout=200&socketTimeout=200");
            System.setProperty("db.user", "root");
            System.setProperty("db.password", "");
            System.setErr(new PrintStream(err));

            assertDoesNotThrow(() -> MigratePasswords.main(new String[0]));
        } finally {
            if (oldUrl == null) {
                System.clearProperty("db.url");
            } else {
                System.setProperty("db.url", oldUrl);
            }
            if (oldUser == null) {
                System.clearProperty("db.user");
            } else {
                System.setProperty("db.user", oldUser);
            }
            if (oldPass == null) {
                System.clearProperty("db.password");
            } else {
                System.setProperty("db.password", oldPass);
            }
            System.setErr(originalErr);
        }

        assertTrue(err.toString().contains("Migration failed"));
    }

    @Test
    public void jsonUtilsTypeOverloadParsesListsAndRejectsInvalidJson() {
        Type listType = new com.google.gson.reflect.TypeToken<List<Integer>>() {
        }.getType();

        List<Integer> numbers = JsonUtils.fromJson("[1,2,3]", listType);
        assertEquals(List.of(1, 2, 3), numbers);

        Map<?, ?> data = JsonUtils.fromJson("{\"k\":\"v\"}", Map.class);
        assertEquals("v", data.get("k"));

        assertThrows(IllegalArgumentException.class, () -> JsonUtils.fromJson("not-json", listType));
    }

    @Test
    public void databaseConnectionThrowsWithInvalidJdbcUrl() {
        String oldUrl = System.getProperty("db.url");
        try {
            System.setProperty("db.url", "jdbc:invalid:test");
            assertThrows(SQLException.class, DatabaseConnection::getConnection);
        } finally {
            if (oldUrl == null) {
                System.clearProperty("db.url");
            } else {
                System.setProperty("db.url", oldUrl);
            }
        }
    }

    @Test
    public void mainStopPrintsExpectedMessage() {
        PrintStream originalOut = System.out;
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        try {
            System.setOut(new PrintStream(out, true, StandardCharsets.UTF_8));
            new Main().stop();
        } finally {
            System.setOut(originalOut);
        }

        assertTrue(out.toString(StandardCharsets.UTF_8).contains("Application stopped"));
    }
}
