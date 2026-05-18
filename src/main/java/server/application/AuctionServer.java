package server.application;

import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

import server.manager.ConnectionManager;
import server.network.ClientHandler;
import server.network.RequestHandler;
import server.service.AuctionService;
import server.service.ItemService;

public class AuctionServer {
    //Dùng ThreadPool để xử lý hàng ngàn client cùng lúc.
    private static final int PORT = 2026;
    private static final int ThreadCount=20;
    private static final int AUCTION_REFRESH_INTERVAL_SECONDS = 30;


    private final int port;
    private final ScheduledExecutorService scheduler;
    private final ExecutorService threadPool;
    private final ConnectionManager connectionManager;
    private final AuctionService auctionService;
    private final RequestHandler requestHandler;

    private ServerSocket serverSocket;
    private volatile boolean running;

    private AuctionServer(){
        this(PORT, ThreadCount);

    }
    public AuctionServer(int port,int threadCount){
        if (port <=0){
            throw new IllegalArgumentException("Số cổng không hợp lệ");
        }
        if (threadCount<=0){
            throw new IllegalArgumentException("Số luồng không hợp lệ");
        }
        this.port=port;
        this.scheduler=Executors.newSingleThreadScheduledExecutor();
        this.threadPool=Executors.newFixedThreadPool(threadCount);
        this.auctionService=new AuctionService(new ItemService());
        this.requestHandler= new RequestHandler();
        this.connectionManager= new ConnectionManager();
    }


    public boolean isRunning(){
        return running;
    }
    public int getPort(){
        return port;
    }
    public int getConnectedClientCount(){
        return connectionManager.getClientCount();
    }


    private void acceptClient(Socket clientSocket) {
        ClientHandler clientHandler = new ClientHandler(clientSocket, requestHandler, connectionManager);
        threadPool.submit(clientHandler);
    }
     public void start() {
        if (running) {
            throw new IllegalStateException("Server đang chạy");
        }

        try {
            this.serverSocket = new ServerSocket(port);
            this.running = true;

            startAuctionScheduler();
            System.out.println("Server đang lắng nghe tại cổng" + port);

            while (running) {
                Socket clientSocket = serverSocket.accept();
                acceptClient(clientSocket);
            }
        } catch (IOException e) {
            if (running) {
                throw new RuntimeException("không thể khởi động server", e);
            }
        } finally {
            stop();
        }
    }
     public void stop() {
        if (!running && (serverSocket == null || serverSocket.isClosed())) {
            return;
        }

        running = false;
        closeServerSocket();
        scheduler.shutdown();
        threadPool.shutdown();
        System.out.println("Server đã dừng");
    }

    private void startAuctionScheduler() {
        scheduler.scheduleAtFixedRate(() -> {
            try {
                auctionService.refreshAuctionsStatus();
            } catch (RuntimeException e) {
                System.err.println("Lỗi khi cập nhật trạng thái auction: " + e.getMessage());
            }
        }, 0, AUCTION_REFRESH_INTERVAL_SECONDS, TimeUnit.SECONDS);
    }

    private void closeServerSocket() {
        if (serverSocket == null || serverSocket.isClosed()) {
            return;
        }
        try {
            serverSocket.close();
        } catch (IOException e) {
            System.err.println("không thể đóng server socket: " + e.getMessage());
        }
    }


}
