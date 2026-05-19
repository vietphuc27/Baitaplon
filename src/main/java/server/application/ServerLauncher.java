package server.application;

public class ServerLauncher {
    public static void main(String[] args) {
        AuctionServer server = new AuctionServer(2026, 20);

        // 3. Đăng ký Shutdown Hook (CỰC KỲ QUAN TRỌNG TRONG THỰC TẾ)
        // giúp server kịp đóng cổng, giải phóng ThreadPool tránh lỗi treo port.
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            System.out.println("\n[Hệ thống] Đang thực hiện đóng server an toàn...");
            server.stop();
        }));

        // 4. Bắt đầu chạy
        try {
            server.start();
        } catch (Exception e) {
            System.err.println("[Lỗi] Không thể khởi động Server: " + e.getMessage());
            System.exit(1); // Thoát chương trình với mã lỗi
        }
    }
}
