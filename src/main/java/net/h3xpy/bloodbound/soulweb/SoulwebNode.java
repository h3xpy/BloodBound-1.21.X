package net.h3xpy.bloodbound.soulweb;

import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;

/**
 * One buyable node. Nodes live on a branch (a spoke radiating from the centre) at a given depth;
 * a node can only be bought once its parent — the node one step closer to the centre on the same
 * branch — has been bought.
 */
public class SoulwebNode {
    private final int branch;
    private final int depth;
    private final int cost;
    private final NodeReward reward;
    /** Layout angle in radians, so client and server agree on where the node sits. */
    private final float angle;
    /** Layout radius, normalised to 0..1 of the web's drawing area. */
    private final float radius;
    private boolean purchased;

    public SoulwebNode(int branch, int depth, int cost, NodeReward reward, float angle, float radius) {
        this.branch = branch;
        this.depth = depth;
        this.cost = cost;
        this.reward = reward;
        this.angle = angle;
        this.radius = radius;
    }

    public int branch() {
        return branch;
    }

    public int depth() {
        return depth;
    }

    public int cost() {
        return cost;
    }

    public NodeReward reward() {
        return reward;
    }

    public float angle() {
        return angle;
    }

    public float radius() {
        return radius;
    }

    public boolean isPurchased() {
        return purchased;
    }

    public void setPurchased(boolean purchased) {
        this.purchased = purchased;
    }

    public CompoundTag save(HolderLookup.Provider provider) {
        CompoundTag tag = new CompoundTag();
        tag.putInt("branch", branch);
        tag.putInt("depth", depth);
        tag.putInt("cost", cost);
        tag.putFloat("angle", angle);
        tag.putFloat("radius", radius);
        tag.putBoolean("purchased", purchased);
        tag.put("reward", reward.save(provider));
        return tag;
    }

    public static SoulwebNode load(HolderLookup.Provider provider, CompoundTag tag) {
        SoulwebNode node = new SoulwebNode(
                tag.getInt("branch"),
                tag.getInt("depth"),
                tag.getInt("cost"),
                NodeReward.load(provider, tag.getCompound("reward")),
                tag.getFloat("angle"),
                tag.getFloat("radius"));
        node.purchased = tag.getBoolean("purchased");
        return node;
    }
}
