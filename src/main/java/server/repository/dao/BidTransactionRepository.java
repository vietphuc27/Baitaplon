package server.repository.dao;

import common.models.auction.BidTransaction;

import java.util.List;
import java.util.Optional;

public interface BidTransactionRepository {
    void save(BidTransaction bid);

    Optional<BidTransaction> findById(int id);

    List<BidTransaction> findAll();

    List<BidTransaction> findByAuctionId(int auctionId);

    List<BidTransaction> findByBidderId(int bidderId);

    BidTransaction findHighestBidByAuctionId(int auctionId);

    void update(BidTransaction bid);

    void delete(int id);
}
