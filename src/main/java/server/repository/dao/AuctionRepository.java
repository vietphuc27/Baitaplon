package server.repository.dao;

import common.models.auction.Auction;
import common.models.auction.AuctionStatus;

import java.util.List;
import java.util.Optional;

public interface AuctionRepository {
    void save(Auction auction);

    Optional<Auction> findById(int id);

    List<Auction> findAll();

    List<Auction> findByStatus(AuctionStatus status);

    List<Auction> findBySellerId(String sellerId);

    List<Auction> findExpiredButNotClosed();

    void update(Auction auction);

    void delete(int id);
}
