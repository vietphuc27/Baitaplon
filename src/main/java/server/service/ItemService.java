package server.service;

import common.models.item.Art;
import common.models.item.Electronics;
import common.models.item.Item;
import common.models.item.Vehicle;
import server.repository.dao.ItemDAO;

import java.util.List;
import java.util.Optional;

public class ItemService {
    private final ItemDAO itemDAO;

    public ItemService() {
        this(new ItemDAO());
    }

    public ItemService(ItemDAO itemDAO) {
        this.itemDAO = itemDAO;
    }

    public Item createItem(Item item) {
        validateNewItem(item);
        itemDAO.save(item);
        return item;
    }

    public Item updateItem(Item item) {
        validateExistingItem(item);
        Item existing = itemDAO.findById(item.getId())
                .orElseThrow(() -> new IllegalArgumentException("Khong tim thay san pham"));

        if (!existing.getSellerId().equals(item.getSellerId())) {
            throw new IllegalArgumentException("Nguoi ban khong duoc phep sua san pham nay");
        }

        itemDAO.update(item);
        return item;
    }

    public void deleteItem(int id, String sellerId) {
        int normalizedId = requirePositiveId(id, "id");
        String normalizedSellerId = requireText(sellerId, "sellerId");

        Item existing = itemDAO.findById(normalizedId)
                .orElseThrow(() -> new IllegalArgumentException("Khong tim thay san pham"));

        if (!existing.getSellerId().equals(normalizedSellerId)) {
            throw new IllegalArgumentException("Nguoi ban khong duoc phep xoa san pham nay");
        }

        itemDAO.delete(normalizedId);
    }

    public Optional<Item> findById(int id) {
        return itemDAO.findById(requirePositiveId(id, "id"));
    }

    public List<Item> findAll() {
        return itemDAO.findAll();
    }

    public List<Item> findBySellerId(String sellerId) {
        return itemDAO.findBySellerId(requireText(sellerId, "sellerId"));
    }

    public List<Item> searchByName(String keyword) {
        return itemDAO.findByNameContaining(requireText(keyword, "keyword"));
    }

    private void validateNewItem(Item item) {
        if (item == null) {
            throw new IllegalArgumentException("San pham khong duoc de trong");
        }

        requirePositiveId(item.getId(), "id");
        validateCommonFields(item);
        validateTypeSpecificFields(item);
    }

    private void validateExistingItem(Item item) {
        if (item == null) {
            throw new IllegalArgumentException("San pham khong duoc de trong");
        }

        requirePositiveId(item.getId(), "id");
        validateCommonFields(item);
        validateTypeSpecificFields(item);
    }

    private void validateCommonFields(Item item) {
        requireText(item.getName(), "name");
        requireText(item.getDescription(), "description");
        requireText(item.getSellerId(), "sellerId");

        if (item.getStartingPrice() <= 0) {
            throw new IllegalArgumentException("Gia khoi diem phai lon hon 0");
        }
    }

    private void validateTypeSpecificFields(Item item) {
        if (item instanceof Electronics electronics) {
            if (electronics.getWarrantyPeriod() < 0) {
                throw new IllegalArgumentException("Thoi gian bao hanh phai lon hon hoac bang 0");
            }
            return;
        }

        if (item instanceof Vehicle vehicle) {
            if (vehicle.getMileage() < 0) {
                throw new IllegalArgumentException("So km da di phai lon hon hoac bang 0");
            }
            return;
        }

        if (item instanceof Art art) {
            requireText(art.getArtist(), "artist");
            return;
        }

        throw new IllegalArgumentException("Loai san pham khong duoc ho tro");
    }

    private String requireText(String value, String fieldName) {
        if (value == null || value.trim().isEmpty()) {
            throw new IllegalArgumentException(fieldName + " khong duoc de trong");
        }
        return value.trim();
    }

    private int requirePositiveId(int value, String fieldName) {
        if (value <= 0) {
            throw new IllegalArgumentException(fieldName + " phai lon hon 0");
        }
        return value;
    }
}
