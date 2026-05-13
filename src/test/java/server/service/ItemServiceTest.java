package server.service;

import common.models.item.Art;
import common.models.item.Electronics;
import common.models.item.Item;
import common.models.item.Vehicle;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import server.repository.ItemDAO;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

class ItemServiceTest {
    private InMemoryItemDAO itemDAO;
    private ItemService itemService;

    @BeforeEach
    void setUp() {
        itemDAO = new InMemoryItemDAO();
        itemService = new ItemService(itemDAO);
    }

    @Test
    void createItemRejectsNullItem() {
        assertThrows(IllegalArgumentException.class, () -> itemService.createItem(null));
    }

    @Test
    void createItemRejectsIdAtZeroBoundary() {
        Item item = new Art(0, "Oil Painting", "Landscape painting", 10.0, "seller-1", "Nguyen Van A");

        assertThrows(IllegalArgumentException.class, () -> itemService.createItem(item));
    }

    @Test
    void createItemAcceptsIdAtOneBoundary() {
        Item item = new Art(1, "Oil Painting", "Landscape painting", 10.0, "seller-1", "Nguyen Van A");

        Item created = itemService.createItem(item);

        assertSame(item, created);
        assertTrue(itemDAO.findById(1).isPresent());
    }

    @Test
    void createItemRejectsBlankName() {
        Item item = new Art(2, "   ", "Landscape painting", 10.0, "seller-1", "Nguyen Van A");

        assertThrows(IllegalArgumentException.class, () -> itemService.createItem(item));
    }

    @Test
    void createItemRejectsBlankDescription() {
        Item item = new Art(3, "Oil Painting", "   ", 10.0, "seller-1", "Nguyen Van A");

        assertThrows(IllegalArgumentException.class, () -> itemService.createItem(item));
    }

    @Test
    void createItemRejectsBlankSellerId() {
        Item item = new Art(4, "Oil Painting", "Landscape painting", 10.0, "   ", "Nguyen Van A");

        assertThrows(IllegalArgumentException.class, () -> itemService.createItem(item));
    }

    @Test
    void createItemRejectsStartingPriceAtZeroBoundary() {
        Item item = new Art(5, "Oil Painting", "Landscape painting", 0.0, "seller-1", "Nguyen Van A");

        assertThrows(IllegalArgumentException.class, () -> itemService.createItem(item));
    }

    @Test
    void createItemAcceptsSmallPositiveStartingPrice() {
        Item item = new Art(6, "Oil Painting", "Landscape painting", 0.01, "seller-1", "Nguyen Van A");

        Item created = itemService.createItem(item);

        assertSame(item, created);
        assertTrue(itemDAO.findById(6).isPresent());
    }

    @Test
    void createItemRejectsBlankArtistForArt() {
        Item item = new Art(7, "Oil Painting", "Landscape painting", 10.0, "seller-1", "   ");

        assertThrows(IllegalArgumentException.class, () -> itemService.createItem(item));
    }

    @Test
    void createItemAcceptsArtAtValidBoundary() {
        Item item = new Art(8, "Oil Painting", "Landscape painting", 10.0, "seller-1", "Nguyen Van A");

        Item created = itemService.createItem(item);

        assertSame(item, created);
        assertTrue(itemDAO.findById(8).isPresent());
    }

    @Test
    void createItemRejectsNegativeWarrantyPeriod() {
        Item item = new Electronics(9, "Laptop", "Gaming laptop", 10.0, "seller-2", -1);

        assertThrows(IllegalArgumentException.class, () -> itemService.createItem(item));
    }

    @Test
    void createItemAcceptsZeroWarrantyPeriod() {
        Item item = new Electronics(10, "Laptop", "Gaming laptop", 10.0, "seller-2", 0);

        Item created = itemService.createItem(item);

        assertSame(item, created);
        assertEquals(0, ((Electronics) itemDAO.findById(10).orElseThrow()).getWarrantyPeriod());
    }

    @Test
    void createItemRejectsNegativeMileage() {
        Item item = new Vehicle(11, "Motorbike", "Used motorbike", 10.0, "seller-3", -1);

        assertThrows(IllegalArgumentException.class, () -> itemService.createItem(item));
    }

    @Test
    void createItemAcceptsZeroMileage() {
        Item item = new Vehicle(12, "Motorbike", "Used motorbike", 10.0, "seller-3", 0);

        Item created = itemService.createItem(item);

        assertSame(item, created);
        assertEquals(0, ((Vehicle) itemDAO.findById(12).orElseThrow()).getMileage());
    }

    @Test
    void createItemRejectsUnsupportedType() {
        Item item = new Item(13, "Unknown", "Unsupported item", 10.0, "seller-1") {
            @Override
            public String getInfo() {
                return "unsupported";
            }
        };

        assertThrows(IllegalArgumentException.class, () -> itemService.createItem(item));
    }

    private static final class InMemoryItemDAO extends ItemDAO {
        private final Map<Integer, Item> items = new HashMap<>();

        @Override
        public void save(Item item) {
            items.put(item.getId(), item);
        }

        @Override
        public Optional<Item> findById(int id) {
            return Optional.ofNullable(items.get(id));
        }

        @Override
        public List<Item> findAll() {
            return List.copyOf(items.values());
        }

        @Override
        public void update(Item item) {
            items.put(item.getId(), item);
        }

        @Override
        public void delete(int id) {
            items.remove(id);
        }
    }
}
