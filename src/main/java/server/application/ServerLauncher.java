package server.application;

import server.config.DatabaseMigration;

public class ServerLauncher {
    public static void main(String[] args) {
        AuctionServer server = new AuctionServer(2026, 20);

        // 1. Run database migrations (add new columns for new features)
        DatabaseMigration.migrate();

        // 2. Đăng ký Shutdown Hook (CỰC KỲ QUAN TRỌNG TRONG THỰC TẾ)
        // giúp server kịp đóng cổng, giải phóng ThreadPool tránh lỗi treo port.
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            System.out.println("\n[Hệ thống] Đang thực hiện đóng server an toàn...");
            server.stop();
        }));

        // 3. Bắt đầu chạy
        try {
            server.start();
        } catch (Exception e) {
            System.err.println("[Lỗi] Không thể khởi động Server: " + e.getMessage());
            System.exit(1); // Thoát chương trình với mã lỗi
        }
    }
}
