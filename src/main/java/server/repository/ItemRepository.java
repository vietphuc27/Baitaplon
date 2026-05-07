package server.repository;

import common.models.item.Item;

import java.util.List;
import java.util.Optional;

public interface ItemRepository {
    void save(Item item);

    Optional<Item> findById(int id);

    List<Item> findAll();

    List<Item> findBySellerId(String sellerId);

    List<Item> findByNameContaining(String keyword);

    void update(Item item);

    void delete(int id);
}
