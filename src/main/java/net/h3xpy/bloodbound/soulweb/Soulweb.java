package net.h3xpy.bloodbound.soulweb;

import java.util.ArrayList;
import java.util.List;

import javax.annotation.Nullable;

import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;

/**
 * A generated web of nodes laid out as spokes around a centre.
 * <p>
 * Buying anything commits the player to that spoke: every other branch is locked off for the rest
 * of the web. Once the committed branch is fully bought there is nothing left to buy, and the web
 * is rerolled with fresh contents.
 */
public class Soulweb {
    /** No branch committed to yet — everything on depth 1 is still open. */
    public static final int NO_BRANCH = -1;

    private final List<SoulwebNode> nodes;
    private final int branchCount;
    private int chosenBranch;
    private int level;

    public Soulweb(List<SoulwebNode> nodes, int branchCount, int level) {
        this.nodes = new ArrayList<>(nodes);
        this.branchCount = branchCount;
        this.level = level;
        this.chosenBranch = NO_BRANCH;
    }

    public List<SoulwebNode> nodes() {
        return nodes;
    }

    public int branchCount() {
        return branchCount;
    }

    public int chosenBranch() {
        return chosenBranch;
    }

    public int level() {
        return level;
    }

    @Nullable
    public SoulwebNode node(int index) {
        return index >= 0 && index < nodes.size() ? nodes.get(index) : null;
    }

    /**
     * A node is reachable when it is not bought yet, sits on the committed branch (or no branch is
     * committed), and its parent one step closer to the centre has already been bought.
     */
    public boolean isUnlockable(int index) {
        SoulwebNode node = node(index);
        if (node == null || node.isPurchased()) {
            return false;
        }
        if (chosenBranch != NO_BRANCH && node.branch() != chosenBranch) {
            return false;
        }
        return node.depth() == 1 || isPurchased(node.branch(), node.depth() - 1);
    }

    /** True when the node sits on a branch the player locked themselves out of. */
    public boolean isBlocked(int index) {
        SoulwebNode node = node(index);
        return node != null && chosenBranch != NO_BRANCH && node.branch() != chosenBranch;
    }

    private boolean isPurchased(int branch, int depth) {
        for (SoulwebNode node : nodes) {
            if (node.branch() == branch && node.depth() == depth) {
                return node.isPurchased();
            }
        }
        return false;
    }

    /** Marks the node as bought and locks every other branch. */
    public void markPurchased(int index) {
        SoulwebNode node = node(index);
        if (node == null) {
            return;
        }
        node.setPurchased(true);
        chosenBranch = node.branch();
    }

    /** True once nothing on the web can be bought any more, which triggers a reroll. */
    public boolean isExhausted() {
        for (int i = 0; i < nodes.size(); i++) {
            if (isUnlockable(i)) {
                return false;
            }
        }
        return true;
    }

    public CompoundTag save(HolderLookup.Provider provider) {
        CompoundTag tag = new CompoundTag();
        tag.putInt("branchCount", branchCount);
        tag.putInt("chosenBranch", chosenBranch);
        tag.putInt("level", level);
        ListTag list = new ListTag();
        for (SoulwebNode node : nodes) {
            list.add(node.save(provider));
        }
        tag.put("nodes", list);
        return tag;
    }

    public static Soulweb load(HolderLookup.Provider provider, CompoundTag tag) {
        List<SoulwebNode> nodes = new ArrayList<>();
        ListTag list = tag.getList("nodes", Tag.TAG_COMPOUND);
        for (int i = 0; i < list.size(); i++) {
            nodes.add(SoulwebNode.load(provider, list.getCompound(i)));
        }
        Soulweb web = new Soulweb(nodes, tag.getInt("branchCount"), tag.getInt("level"));
        web.chosenBranch = tag.getInt("chosenBranch");
        return web;
    }
}
