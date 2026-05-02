package server.repository;

import common.models.auction.BidTransaction;

import java.util.List;
import java.util.Optional;

public interface BidTransactionRepository {
    void save(BidTransaction bid);

    Optional<BidTransaction> findById(String id);

    List<BidTransaction> findAll();

    List<BidTransaction> findByAuctionId(String auctionId);

    List<BidTransaction> findByBidderId(String bidderId);

    BidTransaction findHighestBidByAuctionId(String auctionId);

    void update(BidTransaction bid);

    void delete(String id);
}
