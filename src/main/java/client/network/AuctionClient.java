package client.network;

import common.models.auction.Auction;
import common.models.item.Art;
import common.models.item.Electronics;
import common.models.item.Item;
import common.models.item.Vehicle;
import server.service.AuctionService;
import server.service.ItemService;

import java.time.LocalDateTime;
import java.util.concurrent.ThreadLocalRandom;

public class AuctionClient {
    private final ItemService itemService;
    private final AuctionService auctionService;

    public AuctionClient() {
        this.itemService = new ItemService();
        this.auctionService = new AuctionService(itemService);
    }

    public Auction createAuction(CreateAuctionRequest request) {
        validateRequest(request);
        int itemId = generateItemId();
        Item item = buildItem(itemId, request);
        itemService.createItem(item);
        return auctionService.createAuction(request.sellerId(), item.getId(), LocalDateTime.now(), request.endTime());
    }

    public Auction endAuction(String sellerId, String auctionId) {
        return auctionService.endAuctionBySeller(requireText(sellerId, "sellerId"), parsePositiveInt(auctionId, "auctionId"));
    }

    private Item buildItem(int itemId, CreateAuctionRequest request) {
        String description = buildDescription(request);
        return switch (normalizeType(request.itemType())) {
            case "art" -> new Art(itemId, request.itemName(), description, request.startPrice(), request.sellerId(), requireText(request.artist(), "artist"));
            case "electronics" -> new Electronics(itemId, request.itemName(), description, request.startPrice(), request.sellerId(), 0);
            case "vehicle" -> new Vehicle(itemId, request.itemName(), description, request.startPrice(), request.sellerId(), parseNonNegativeInt(request.mileage(), "mileage"));
            default -> throw new IllegalArgumentException("Loai san pham khong duoc ho tro");
        };
    }

    private String buildDescription(CreateAuctionRequest request) {
        StringBuilder description = new StringBuilder(requireText(request.description(), "description"));
        String type = normalizeType(request.itemType());
        if ("art".equals(type)) {
            appendDetail(description, "Nghe si", request.artist());
            appendDetail(description, "Nam sang tac", request.artYear());
            appendDetail(description, "Chat lieu", request.material());
        } else if ("electronics".equals(type)) {
            appendDetail(description, "Thuong hieu", request.brand());
            appendDetail(description, "Model", request.model());
            appendDetail(description, "Tinh trang", request.condition());
        } else if ("vehicle".equals(type)) {
            appendDetail(description, "Hang xe", request.vehicleBrand());
            appendDetail(description, "So km da di", request.mileage());
            appendDetail(description, "Nam san xuat", request.vehicleYear());
        }
        return description.toString();
    }

    private void appendDetail(StringBuilder description, String label, String value) {
        if (value != null && !value.trim().isEmpty()) {
            description.append("\n").append(label).append(": ").append(value.trim());
        }
    }

    private void validateRequest(CreateAuctionRequest request) {
        if (request == null) throw new IllegalArgumentException("Thong tin tao auction khong duoc de trong");
        requireText(request.sellerId(), "sellerId");
        requireText(request.itemName(), "itemName");
        requireText(request.itemType(), "itemType");
        requireText(request.description(), "description");
        if (request.startPrice() <= 0) throw new IllegalArgumentException("Gia khoi diem phai lon hon 0");
        if (request.endTime() == null) throw new IllegalArgumentException("Thoi gian ket thuc khong duoc de trong");
        if (!LocalDateTime.now().isBefore(request.endTime())) throw new IllegalArgumentException("Thoi gian ket thuc phai sau hien tai");
    }

    private String normalizeType(String rawType) {
        String type = requireText(rawType, "itemType").toLowerCase();
        if (type.contains("ngh") || type.contains("art")) return "art";
        if (type.contains("dien") || type.contains("điện") || type.contains("electronic")) return "electronics";
        if (type.contains("phuong") || type.contains("phương") || type.contains("vehicle")) return "vehicle";
        return type;
    }

    private String requireText(String value, String fieldName) {
        if (value == null || value.trim().isEmpty()) throw new IllegalArgumentException(fieldName + " khong duoc de trong");
        return value.trim();
    }

    private int parsePositiveInt(String value, String fieldName) {
        try {
            int number = Integer.parseInt(requireText(value, fieldName));
            if (number <= 0) throw new IllegalArgumentException(fieldName + " phai lon hon 0");
            return number;
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException(fieldName + " phai la so nguyen");
        }
    }

    private int parseNonNegativeInt(String value, String fieldName) {
        try {
            int number = Integer.parseInt(requireText(value, fieldName));
            if (number < 0) throw new IllegalArgumentException(fieldName + " phai lon hon hoac bang 0");
            return number;
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException(fieldName + " phai la so nguyen");
        }
    }

    private int generateItemId() {
        int id;
        do {
            id = ThreadLocalRandom.current().nextInt(100000, 999999);
        } while (itemService.findById(id).isPresent());
        return id;
    }

    public record CreateAuctionRequest(
            String sellerId,
            String itemName,
            String itemType,
            double startPrice,
            String description,
            LocalDateTime endTime,
            String artist,
            String artYear,
            String material,
            String brand,
            String model,
            String condition,
            String vehicleBrand,
            String mileage,
            String vehicleYear
    ) {}
}
