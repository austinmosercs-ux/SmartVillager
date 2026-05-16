package com.smartvillager.needqueue;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.resources.Identifier;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Per-village request queue.
 *
 * Two lists:
 *   open       — requests with no acceptor; polled by villagers during idle state
 *   inProgress — accepted requests being actively fulfilled
 *
 * Both lists are persisted so in-progress requests (e.g. an ongoing escort job)
 * survive server restarts. Embedded in SmartVillage.CODEC as an optionalFieldOf
 * so saves written before this branch loads cleanly (queue starts empty).
 *
 * All access is server-thread-only; no synchronization needed.
 */
public final class VillageNeedQueue {

    public static final Codec<VillageNeedQueue> CODEC = RecordCodecBuilder.create(i -> i.group(
        NeedRequest.CODEC.listOf()
            .optionalFieldOf("open", List.of())
            .forGetter(q -> List.copyOf(q.open)),
        NeedRequest.CODEC.listOf()
            .optionalFieldOf("in_progress", List.of())
            .forGetter(q -> List.copyOf(q.inProgress))
    ).apply(i, (openList, inProgressList) ->
        new VillageNeedQueue(new ArrayList<>(openList), new ArrayList<>(inProgressList))
    ));

    private final List<NeedRequest> open;
    private final List<NeedRequest> inProgress;

    public VillageNeedQueue() {
        this.open = new ArrayList<>();
        this.inProgress = new ArrayList<>();
    }

    private VillageNeedQueue(List<NeedRequest> open, List<NeedRequest> inProgress) {
        this.open = open;
        this.inProgress = inProgress;
    }

    // -------------------------------------------------------------------------
    // Posting
    // -------------------------------------------------------------------------

    /**
     * Enqueue a request. Silently deduplicates: if an open request of the same
     * type and itemData already exists, the new request is dropped. This prevents
     * repeated Librarian shortage scans from flooding the queue with duplicates.
     */
    public void post(NeedRequest request) {
        boolean duplicate = open.stream().anyMatch(r ->
            r.getType().equals(request.getType())
                && r.getItemData().equals(request.getItemData())
        );
        if (!duplicate) {
            open.add(request);
        }
    }

    // -------------------------------------------------------------------------
    // Acceptance
    // -------------------------------------------------------------------------

    /**
     * Accept the highest-priority open request of the given type on behalf of a
     * villager. Moves the request from open → inProgress and returns it.
     * Returns empty if no matching open request exists.
     */
    public Optional<NeedRequest> accept(Identifier type, UUID acceptorUuid) {
        NeedRequest best = null;
        for (NeedRequest r : open) {
            if (!r.getType().equals(type)) continue;
            if (best == null || r.getPriority().order() > best.getPriority().order()) {
                best = r;
            }
        }
        if (best == null) return Optional.empty();
        open.remove(best);
        NeedRequest accepted = best.withAcceptor(acceptorUuid);
        inProgress.add(accepted);
        return Optional.of(accepted);
    }

    // -------------------------------------------------------------------------
    // Completion and cancellation
    // -------------------------------------------------------------------------

    /** Mark an in-progress request as fulfilled and remove it. Returns true if found. */
    public boolean complete(UUID requestId) {
        return inProgress.removeIf(r -> r.getId().equals(requestId));
    }

    /** Cancel a request from either list. Returns true if found. */
    public boolean cancel(UUID requestId) {
        if (inProgress.removeIf(r -> r.getId().equals(requestId))) return true;
        return open.removeIf(r -> r.getId().equals(requestId));
    }

    /**
     * Cancel all open (unaccepted) requests with the given type and itemData.
     * Used by NeedQueue when a LibrarianCoordinator shortage resolves.
     */
    public void cancelByTypeAndItem(Identifier type, Optional<Identifier> itemData) {
        open.removeIf(r -> r.getType().equals(type) && r.getItemData().equals(itemData));
    }

    // -------------------------------------------------------------------------
    // Expiry
    // -------------------------------------------------------------------------

    /**
     * Remove open requests older than maxAgeTicks that still have no acceptor.
     * In-progress requests are never expired — an acceptor is responsible for
     * calling complete() or cancel() when done.
     *
     * @return the number of requests removed
     */
    public int expireOlderThan(long currentTick, long maxAgeTicks) {
        int before = open.size();
        open.removeIf(r -> (currentTick - r.getPostedTick()) > maxAgeTicks);
        return before - open.size();
    }

    // -------------------------------------------------------------------------
    // Queries
    // -------------------------------------------------------------------------

    /** True if there is an unaccepted open request matching both type and itemData. */
    public boolean hasOpenRequest(Identifier type, Optional<Identifier> itemData) {
        return open.stream().anyMatch(r ->
            r.getType().equals(type) && r.getItemData().equals(itemData));
    }

    /** All open requests of the given type, unmodifiable. */
    public List<NeedRequest> peekOpen(Identifier type) {
        return open.stream().filter(r -> r.getType().equals(type)).toList();
    }

    /**
     * Returns the highest-priority open request of the given type without removing it.
     * Returns empty if no matching open request exists.
     */
    public Optional<NeedRequest> findHighestPriorityOpen(Identifier type) {
        NeedRequest best = null;
        for (NeedRequest r : open) {
            if (!r.getType().equals(type)) continue;
            if (best == null || r.getPriority().order() > best.getPriority().order()) {
                best = r;
            }
        }
        return Optional.ofNullable(best);
    }

    /**
     * Cancels all open and in-progress requests from the given poster of the given type.
     * Used when a patient is healed and their request is no longer relevant.
     */
    public void cancelByPosterAndType(UUID poster, Identifier type) {
        open.removeIf(r -> r.getType().equals(type) && r.getPoster().equals(poster));
        inProgress.removeIf(r -> r.getType().equals(type) && r.getPoster().equals(poster));
    }

    public List<NeedRequest> allOpen() {
        return Collections.unmodifiableList(open);
    }

    public List<NeedRequest> allInProgress() {
        return Collections.unmodifiableList(inProgress);
    }
}
