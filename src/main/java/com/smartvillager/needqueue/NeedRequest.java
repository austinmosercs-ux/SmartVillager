package com.smartvillager.needqueue;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.UUIDUtil;
import net.minecraft.resources.Identifier;

import java.util.Optional;
import java.util.UUID;

/**
 * A single request in the village NeedQueue.
 *
 * Immutable after construction. Status is tracked by placement in
 * VillageNeedQueue: the open list holds OPEN requests; the inProgress list
 * holds ACCEPTED requests. A request that has been completed or cancelled
 * is simply removed.
 *
 * Fields:
 *   id         — stable UUID; used to accept, complete, or cancel by reference
 *   type       — NeedType identifier (e.g. smartvillager:need/escort)
 *   priority   — how urgently this request needs filling
 *   poster     — UUID of the posting villager, or NeedQueue.LIBRARIAN_POSTER
 *   itemData   — item identifier for resource-typed requests (NEED_MATERIALS,
 *                NEED_TOOLS, NEED_RESTOCK, WANT_TO_BUY); empty for NEED_ESCORT
 *                and NEED_HEALING
 *   postedTick — server game tick when posted; used for expiry
 *   acceptor   — empty = OPEN; non-empty = ACCEPTED by this villager UUID
 */
public final class NeedRequest {

    public static final Codec<NeedRequest> CODEC = RecordCodecBuilder.create(i -> i.group(
        UUIDUtil.STRING_CODEC
            .fieldOf("id")
            .forGetter(NeedRequest::getId),
        Identifier.CODEC
            .fieldOf("type")
            .forGetter(NeedRequest::getType),
        NeedPriority.CODEC
            .fieldOf("priority")
            .forGetter(NeedRequest::getPriority),
        UUIDUtil.STRING_CODEC
            .fieldOf("poster")
            .forGetter(NeedRequest::getPoster),
        Identifier.CODEC
            .optionalFieldOf("item_data")
            .forGetter(NeedRequest::getItemData),
        Codec.LONG
            .fieldOf("posted_tick")
            .forGetter(NeedRequest::getPostedTick),
        UUIDUtil.STRING_CODEC
            .optionalFieldOf("acceptor")
            .forGetter(NeedRequest::getAcceptor)
    ).apply(i, NeedRequest::new));

    private final UUID id;
    private final Identifier type;
    private final NeedPriority priority;
    private final UUID poster;
    private final Optional<Identifier> itemData;
    private final long postedTick;
    private final Optional<UUID> acceptor;

    public NeedRequest(UUID id, Identifier type, NeedPriority priority, UUID poster,
                       Optional<Identifier> itemData, long postedTick, Optional<UUID> acceptor) {
        this.id = id;
        this.type = type;
        this.priority = priority;
        this.poster = poster;
        this.itemData = itemData;
        this.postedTick = postedTick;
        this.acceptor = acceptor;
    }

    /** Create a new OPEN request. Use NeedQueue.postRequest() to also enqueue it. */
    public static NeedRequest post(Identifier type, NeedPriority priority, UUID poster,
                                   Optional<Identifier> itemData, long currentTick) {
        return new NeedRequest(UUID.randomUUID(), type, priority, poster,
                               itemData, currentTick, Optional.empty());
    }

    /** Return a copy of this request marked as accepted by the given villager. */
    public NeedRequest withAcceptor(UUID acceptorUuid) {
        return new NeedRequest(id, type, priority, poster, itemData, postedTick,
                               Optional.of(acceptorUuid));
    }

    public UUID getId()                       { return id; }
    public Identifier getType()               { return type; }
    public NeedPriority getPriority()         { return priority; }
    public UUID getPoster()                   { return poster; }
    public Optional<Identifier> getItemData() { return itemData; }
    public long getPostedTick()               { return postedTick; }
    public Optional<UUID> getAcceptor()       { return acceptor; }

    public boolean isOpen()     { return acceptor.isEmpty(); }
    public boolean isAccepted() { return acceptor.isPresent(); }
}
