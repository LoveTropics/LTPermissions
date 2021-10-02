package com.lovetropics.perms.override.command;

import com.google.common.collect.HashMultimap;
import com.google.common.collect.Multimap;
import com.lovetropics.perms.LTPermissions;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.tree.CommandNode;
import com.mojang.brigadier.tree.RootCommandNode;

import java.lang.reflect.Field;
import java.util.Arrays;
import java.util.Collection;
import java.util.function.BiConsumer;
import java.util.function.Predicate;

public final class CommandRequirementHooks<S> {
    private static final int MAX_CHAIN_LENGTH = 12;

    private final RequirementOverride<S> override;
    private final Field requirementField;

    private CommandRequirementHooks(RequirementOverride<S> override, Field requirementField) {
        this.override = override;
        this.requirementField = requirementField;
    }

    public static <S> CommandRequirementHooks<S> tryCreate(RequirementOverride<S> override) throws ReflectiveOperationException {
        Field requirementField = CommandNode.class.getDeclaredField("requirement");
        requirementField.setAccessible(true);
        return new CommandRequirementHooks<>(override, requirementField);
    }

    @SuppressWarnings("unchecked")
    public void applyTo(CommandDispatcher<S> dispatcher) throws ReflectiveOperationException {
        Collection<CommandNode<S>> nodes = dispatcher.getRoot().getChildren();

        Multimap<CommandNode<S>, Predicate<S>> overrides = HashMultimap.create();
        BiConsumer<CommandNode<S>, Predicate<S>> override = overrides::put;

        for (CommandNode<S> node : nodes) {
            this.collectRecursive(new CommandNode[] { node }, override);
        }

        for (CommandNode<S> node : overrides.keySet()) {
            Collection<Predicate<S>> requirements = overrides.get(node);

            Predicate<S> requirement = this.anyRequirement(requirements.toArray(new Predicate[0]));
            this.requirementField.set(node, requirement);
        }
    }

    private void collectRecursive(CommandNode<S>[] nodes, BiConsumer<CommandNode<S>, Predicate<S>> override) {
        if (nodes.length >= MAX_CHAIN_LENGTH) {
            StringBuilder chain = new StringBuilder();
            for (CommandNode<S> node : nodes) {
                chain.append(node.getName()).append(" ");
            }

            LTPermissions.LOGGER.warn("Aborting hooking long command chain with {} nodes: {}", MAX_CHAIN_LENGTH, chain.toString());

            return;
        }

        CommandNode<S> tail = nodes[nodes.length - 1];
        Collection<CommandNode<S>> children = tail.getChildren();

        Predicate<S> requirement = this.createRequirementFor(nodes);
        override.accept(tail, requirement);

        CommandNode<S> redirect = tail.getRedirect();
        if (redirect != null && children.isEmpty() && this.canRedirectTo(nodes, redirect)) {
            CommandNode<S>[] redirectNodes = Arrays.copyOf(nodes, nodes.length);
            redirectNodes[redirectNodes.length - 1] = redirect;

            Predicate<S> redirectRequirement = this.createRequirementFor(redirectNodes);

            // set our override on the redirect, and set the redirect override on us
            override.accept(tail, redirectRequirement);
            override.accept(redirect, requirement);

            // from here, we instead process from the redirect
            children = redirect.getChildren();
        }

        for (CommandNode<S> child : children) {
            if (this.isChildRecursive(nodes, child)) {
                continue;
            }

            CommandNode<S>[] childNodes = Arrays.copyOf(nodes, nodes.length + 1);
            childNodes[childNodes.length - 1] = child;
            this.collectRecursive(childNodes, override);
        }
    }

    private boolean canRedirectTo(CommandNode<S>[] nodes, CommandNode<S> node) {
        // we don't want to redirect back to every other command
        if (node instanceof RootCommandNode) {
            return false;
        }

        // we don't want to redirect back to ourself
        return !this.isChildRecursive(nodes, node);
    }

    private boolean isChildRecursive(CommandNode<S>[] nodes, CommandNode<S> child) {
        for (CommandNode<S> node : nodes) {
            if (node == child) {
                return true;
            }
        }
        return false;
    }

    private Predicate<S> createRequirementFor(CommandNode<S>[] nodes) {
        Predicate<S> chainRequirements = this.requirementForChain(nodes);
        return this.override.apply(nodes, chainRequirements);
    }

    @SuppressWarnings("unchecked")
    private Predicate<S> requirementForChain(CommandNode<S>[] nodes) {
        Predicate<S>[] requirementTree = new Predicate[nodes.length];
        for (int i = 0; i < nodes.length; i++) {
            CommandNode<S> node = nodes[i];
            requirementTree[i] = node.getRequirement();
        }

        return this.allRequirements(requirementTree);
    }

    private Predicate<S> anyRequirement(Predicate<S>[] requirements) {
        if (requirements.length == 0) {
            return s -> false;
        } else if (requirements.length == 1) {
            return requirements[0];
        }

        return s -> {
            for (Predicate<S> requirement : requirements) {
                if (requirement.test(s)) {
                    return true;
                }
            }
            return false;
        };
    }

    private Predicate<S> allRequirements(Predicate<S>[] requirements) {
        if (requirements.length == 0) {
            return s -> true;
        } else if (requirements.length == 1) {
            return requirements[0];
        }

        return s -> {
            for (Predicate<S> requirement : requirements) {
                if (!requirement.test(s)) {
                    return false;
                }
            }
            return true;
        };
    }

    public interface RequirementOverride<S> {
        Predicate<S> apply(CommandNode<S>[] nodes, Predicate<S> parent);
    }
}
