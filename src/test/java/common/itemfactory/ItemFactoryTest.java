package common.itemfactory;

import common.models.item.Art;
import common.models.item.Electronics;
import common.models.item.Item;
import common.models.item.Vehicle;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class ItemFactoryTest {

    @BeforeAll
    static void loadCreators() throws Exception {
        Class.forName("common.itemfactory.ArtCreator");
        Class.forName("common.itemfactory.ElectronicsCreator");
        Class.forName("common.itemfactory.VehicleCreator");
    }

    @Test
    void singletonAndCreateArtElectronicsVehicle() {
        assertSame(ItemFactory.getInstance(), ItemFactory.getInstance());

        Item art = ItemFactory.createItem("art", 1, "Painting", "desc", 100, "s1", "Picasso");
        assertInstanceOf(Art.class, art);
        assertEquals("Painting", art.getName());

        Item elect = ItemFactory.createItem("electronics", 2, "Laptop", "desc", 200, "s1", 0);
        assertInstanceOf(Electronics.class, elect);

        Item vehicle = ItemFactory.createItem("vehicle", 3, "Car", "desc", 300, "s1", 50000);
        assertInstanceOf(Vehicle.class, vehicle);
    }

    @Test
    void caseInsensitiveType() {
        assertDoesNotThrow(() -> ItemFactory.createItem("ART", 1, "x", "x", 1, "s1", "a"));
        assertDoesNotThrow(() -> ItemFactory.createItem("Art", 1, "x", "x", 1, "s1", "a"));
    }

    @Test
    void unsupportedTypeThrows() {
        assertThrows(Exception.class, () -> ItemFactory.createItem("unknown", 1, "x", "x", 1, "s1", null));
    }
}
