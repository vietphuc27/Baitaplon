package server.manager;

import common.models.auction.AutoBidAgent;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * JUnit Tests cho AutoBidAgent
 * Sử dụng Equivalence Partitioning (EP) và Boundary Value Analysis (BVA)
 */
@DisplayName("AutoBidAgent Tests - EP & BVA")
public class AutoBidAgentTest {

    private AutoBidAgent agent;
    private static final int AGENT_ID = 1;
    private static final int BIDDER_ID = 100;
    private static final int AUCTION_ID = 10;

    @BeforeEach
    void setUp() {
        agent = new AutoBidAgent(AGENT_ID, BIDDER_ID, AUCTION_ID, 1000.0, 50.0);
    }

    // ═════════════════════════════════════════════════════════════
    // TEST: calculateProposedBid(currentHighestBid)
    // ═════════════════════════════════════════════════════════════

    @DisplayName("EP1: Proposed bid < maxBid - should return proposed bid")
    @Test
    void testCalculateProposedBid_BidLessThanMax() {
        // Setup: currentHighestBid = 800, proposed = 850, maxBid = 1000
        double proposed = agent.calculateProposedBid(800.0);
        assertEquals(850.0, proposed, "Proposed bid should be 850");
    }

    @DisplayName("EP2: Proposed bid = maxBid - should return proposed bid")
    @Test
    void testCalculateProposedBid_BidEqualMax() {
        // Setup: currentHighestBid = 950, proposed = 1000, maxBid = 1000
        double proposed = agent.calculateProposedBid(950.0);
        assertEquals(1000.0, proposed, "Proposed bid should be 1000");
    }

    @DisplayName("EP3: Proposed bid > maxBid - should return -1")
    @Test
    void testCalculateProposedBid_BidGreaterThanMax() {
        // Setup: currentHighestBid = 1000, proposed = 1050, maxBid = 1000
        double proposed = agent.calculateProposedBid(1000.0);
        assertEquals(-1.0, proposed, "Proposed bid should be -1 when exceeding maxBid");
    }

    // ─── BVA: Boundary values for calculateProposedBid ───

    @DisplayName("BVA1: currentHighestBid = 0 (minimum valid)")
    @Test
    void testCalculateProposedBid_BoundaryZero() {
        double proposed = agent.calculateProposedBid(0.0);
        assertEquals(50.0, proposed, "Proposed bid should be increment (50)");
    }

    @DisplayName("BVA2: currentHighestBid just below (maxBid - increment)")
    @Test
    void testCalculateProposedBid_BoundaryJustBelow() {
        // maxBid = 1000, increment = 50
        // currentHighestBid = 949.99, proposed = 999.99
        double proposed = agent.calculateProposedBid(949.99);
        assertEquals(999.99, proposed, "Proposed bid should be just below maxBid");
    }

    @DisplayName("BVA3: currentHighestBid = (maxBid - increment) exactly")
    @Test
    void testCalculateProposedBid_BoundaryExact() {
        // maxBid = 1000, increment = 50
        // currentHighestBid = 950, proposed = 1000
        double proposed = agent.calculateProposedBid(950.0);
        assertEquals(1000.0, proposed, "Proposed bid should equal maxBid");
    }

    @DisplayName("BVA4: currentHighestBid just above (maxBid - increment)")
    @Test
    void testCalculateProposedBid_BoundaryJustAbove() {
        // maxBid = 1000, increment = 50
        // currentHighestBid = 950.01, proposed = 1000.01 > 1000
        double proposed = agent.calculateProposedBid(950.01);
        assertEquals(-1.0, proposed, "Should return -1 when proposed exceeds maxBid");
    }

    @DisplayName("BVA5: currentHighestBid = maxBid")
    @Test
    void testCalculateProposedBid_BoundaryAtMax() {
        double proposed = agent.calculateProposedBid(1000.0);
        assertEquals(-1.0, proposed, "Should return -1 when equal to maxBid");
    }

    // ═════════════════════════════════════════════════════════════
    // TEST: canBid(currentHighestBid)
    // ═════════════════════════════════════════════════════════════

    @DisplayName("EP4: Agent can bid - currentHighestBid + increment < maxBid")
    @Test
    void testCanBid_CanBid() {
        boolean result = agent.canBid(800.0);
        assertTrue(result, "Agent should be able to bid");
    }

    @DisplayName("EP5: Agent cannot bid - proposed > maxBid")
    @Test
    void testCanBid_CannotBid() {
        boolean result = agent.canBid(1000.0);
        assertFalse(result, "Agent should not be able to bid");
    }

    @DisplayName("EP6: Agent inactive - canBid should return false")
    @Test
    void testCanBid_AgentInactive() {
        agent.setActive(false);
        boolean result = agent.canBid(800.0);
        assertFalse(result, "Inactive agent should not be able to bid");
    }

    // ─── BVA: Boundary values for canBid ───

    @DisplayName("BVA6: currentHighestBid = 0")
    @Test
    void testCanBid_BoundaryZero() {
        boolean result = agent.canBid(0.0);
        assertTrue(result, "Agent should be able to bid at zero");
    }

    @DisplayName("BVA7: currentHighestBid = (maxBid - increment)")
    @Test
    void testCanBid_BoundaryAtLimit() {
        // maxBid = 1000, increment = 50
        // currentHighestBid = 950, proposed = 1000 <= maxBid
        boolean result = agent.canBid(950.0);
        assertTrue(result, "Agent should be able to bid exactly at limit");
    }

    @DisplayName("BVA8: currentHighestBid = (maxBid - increment) + 0.01")
    @Test
    void testCanBid_BoundaryExceedsLimit() {
        // maxBid = 1000, increment = 50
        // currentHighestBid = 950.01, proposed = 1000.01 > maxBid
        boolean result = agent.canBid(950.01);
        assertFalse(result, "Agent should not be able to bid beyond limit");
    }

    // ═════════════════════════════════════════════════════════════
    // TEST: compareTo(other) - for PriorityQueue sorting
    // ═════════════════════════════════════════════════════════════

    @DisplayName("EP7: Compare agents with different maxBid - higher maxBid has priority")
    @Test
    void testCompareTo_DifferentMaxBid() {
        AutoBidAgent agent1 = new AutoBidAgent(1, 100, 10, 1000.0, 50.0);
        AutoBidAgent agent2 = new AutoBidAgent(2, 101, 10, 800.0, 50.0);

        int result = agent1.compareTo(agent2);
        assertTrue(result < 0, "Agent with higher maxBid should come first");
    }

    @DisplayName("EP8: Compare agents with same maxBid - FIFO order")
    @Test
    void testCompareTo_SameMaxBid() throws InterruptedException {
        AutoBidAgent agent1 = new AutoBidAgent(1, 100, 10, 1000.0, 50.0);
        Thread.sleep(10); // Ensure different creation time
        AutoBidAgent agent2 = new AutoBidAgent(2, 101, 10, 1000.0, 50.0);

        int result = agent1.compareTo(agent2);
        assertTrue(result < 0, "Earlier created agent should have priority");
    }

    // ─── BVA: Boundary values for compareTo ───

    @DisplayName("BVA9: Compare maxBid values at boundary")
    @Test
    void testCompareTo_BoundaryMaxBid() {
        AutoBidAgent agent1 = new AutoBidAgent(1, 100, 10, 1000.0, 50.0);
        AutoBidAgent agent2 = new AutoBidAgent(2, 101, 10, 999.99, 50.0);

        int result = agent1.compareTo(agent2);
        assertTrue(result < 0, "Agent with slightly higher maxBid should have priority");
    }

    // ═════════════════════════════════════════════════════════════
    // TEST: Agent state management
    // ═════════════════════════════════════════════════════════════

    @DisplayName("EP9: Agent initial state - should be active")
    @Test
    void testAgentInitialState() {
        assertTrue(agent.isActive(), "New agent should be active by default");
    }

    @DisplayName("EP10: Toggle agent state")
    @Test
    void testToggleAgentState() {
        assertTrue(agent.isActive());
        agent.setActive(false);
        assertFalse(agent.isActive());
        agent.setActive(true);
        assertTrue(agent.isActive());
    }

    // ─── BVA: Edge case states ───

    @DisplayName("BVA10: Active agent with minimum increment")
    @Test
    void testMinimumIncrement() {
        AutoBidAgent minAgent = new AutoBidAgent(1, 100, 10, 100.0, 0.01);
        boolean result = minAgent.canBid(99.99);
        assertTrue(result, "Should handle very small increments");
    }

    @DisplayName("BVA11: Active agent with large maxBid")
    @Test
    void testLargeMaxBid() {
        AutoBidAgent largeAgent = new AutoBidAgent(1, 100, 10, 1_000_000.0, 50.0);
        // currentHighestBid = 999_999, proposed = 1_000_049 > 1_000_000
        boolean result = largeAgent.canBid(999_949.0);
        assertTrue(result, "Should handle large maxBid values");
    }

    // ═════════════════════════════════════════════════════════════
    // TEST: Constructor and getters
    // ═════════════════════════════════════════════════════════════

    @DisplayName("EP11: Agent attributes set correctly")
    @Test
    void testAgentAttributes() {
        assertEquals(AGENT_ID, agent.getAgentId());
        assertEquals(BIDDER_ID, agent.getBidderId());
        assertEquals(AUCTION_ID, agent.getAuctionId());
        assertEquals(1000.0, agent.getMaxBid());
        assertEquals(50.0, agent.getIncrement());
        assertNotNull(agent.getCreatedAt());
    }
}
