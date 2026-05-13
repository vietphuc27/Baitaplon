package common.utils;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

public final class FormatUtils {
    private static final DateTimeFormatter DATE_TIME_FORMATTER = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm");
    private static final DateTimeFormatter DATE_TIME_SECONDS_FORMATTER = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm:ss");

    private FormatUtils() {
    }

    public static String formatCurrency(double value) {
        return String.format("%,.0f VND", value);
    }

    public static String formatDateTime(LocalDateTime value) {
        return value == null ? "-" : value.format(DATE_TIME_FORMATTER);
    }

    public static String formatDateTimeWithSeconds(LocalDateTime value) {
        return value == null ? "-" : value.format(DATE_TIME_SECONDS_FORMATTER);
    }
}
