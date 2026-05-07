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

        return auctionService.createAuction(
                request.sellerId(),
                item.getId(),
                LocalDateTime.now(),
                request.endTime()
        );
    }

    public Auction endAuction(String sellerId, String auctionId) {
        return auctionService.endAuctionBySeller(
                requireText(sellerId, "sellerId"),
                parsePositiveInt(auctionId, "auctionId")
        );
    }

    private Item buildItem(int itemId, CreateAuctionRequest request) {
        String description = buildDescription(request);

        return switch (request.itemType()) {
            case "Tác phẩm nghệ thuật" -> new Art(
                    itemId,
                    request.itemName(),
                    description,
                    request.startPrice(),
                    request.sellerId(),
                    requireText(request.artist(), "artist")
            );
            case "Điện tử" -> new Electronics(
                    itemId,
                    request.itemName(),
                    description,
                    request.startPrice(),
                    request.sellerId(),
                    0
            );
            case "Phương tiện" -> new Vehicle(
                    itemId,
                    request.itemName(),
                    description,
                    request.startPrice(),
                    request.sellerId(),
                    parseNonNegativeInt(request.mileage(), "mileage")
            );
            default -> throw new IllegalArgumentException("Loại sản phẩm không được hỗ trợ");
        };
    }

    private String buildDescription(CreateAuctionRequest request) {
        StringBuilder description = new StringBuilder(requireText(request.description(), "description"));

        if ("Tác phẩm nghệ thuật".equals(request.itemType())) {
            appendDetail(description, "Nghệ sĩ", request.artist());
            appendDetail(description, "Năm sáng tác", request.artYear());
            appendDetail(description, "Chất liệu", request.material());
        } else if ("Điện tử".equals(request.itemType())) {
            appendDetail(description, "Thương hiệu", request.brand());
            appendDetail(description, "Model", request.model());
            appendDetail(description, "Tình trạng", request.condition());
        } else if ("Phương tiện".equals(request.itemType())) {
            appendDetail(description, "Hãng xe", request.vehicleBrand());
            appendDetail(description, "Số km đã đi", request.mileage());
            appendDetail(description, "Năm sản xuất", request.vehicleYear());
        }

        return description.toString();
    }

    private void appendDetail(StringBuilder description, String label, String value) {
        if (value != null && !value.trim().isEmpty()) {
            description.append("\n").append(label).append(": ").append(value.trim());
        }
    }

    private void validateRequest(CreateAuctionRequest request) {
        if (request == null) {
            throw new IllegalArgumentException("Thông tin tạo auction không được để trống");
        }

        requireText(request.sellerId(), "sellerId");
        requireText(request.itemName(), "itemName");
        requireText(request.itemType(), "itemType");
        requireText(request.description(), "description");

        if (request.startPrice() <= 0) {
            throw new IllegalArgumentException("Giá khởi điểm phải lớn hơn 0");
        }
        if (request.endTime() == null) {
            throw new IllegalArgumentException("Thời gian kết thúc không được để trống");
        }
        if (!LocalDateTime.now().isBefore(request.endTime())) {
            throw new IllegalArgumentException("Thời gian kết thúc phải sau thời điểm hiện tại");
        }
    }

    private String requireText(String value, String fieldName) {
        if (value == null || value.trim().isEmpty()) {
            throw new IllegalArgumentException(fieldName + " không được để trống");
        }
        return value.trim();
    }

    private int parsePositiveInt(String value, String fieldName) {
        try {
            int number = Integer.parseInt(requireText(value, fieldName));
            if (number <= 0) {
                throw new IllegalArgumentException(fieldName + " phải lớn hơn 0");
            }
            return number;
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException(fieldName + " phải là số nguyên");
        }
    }

    private int parseNonNegativeInt(String value, String fieldName) {
        try {
            int number = Integer.parseInt(requireText(value, fieldName));
            if (number < 0) {
                throw new IllegalArgumentException(fieldName + " phải lớn hơn hoặc bằng 0");
            }
            return number;
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException(fieldName + " phải là số nguyên");
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
    ) {
    }
}
