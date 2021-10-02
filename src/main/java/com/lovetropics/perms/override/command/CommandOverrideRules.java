package com.lovetropics.perms.override.command;

import com.lovetropics.perms.PermissionResult;
import com.mojang.serialization.Codec;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

public final class CommandOverrideRules {
    private static final Codec<Pattern[]> PATTERN_CODEC = Codec.STRING.xmap(
            key -> {
                String[] patternStrings = key.split(" ");
                return Arrays.stream(patternStrings).map(Pattern::compile).toArray(Pattern[]::new);
            },
            patterns -> {
                return Arrays.stream(patterns).map(Pattern::pattern).collect(Collectors.joining(" "));
            }
    );

    public static final Codec<CommandOverrideRules> CODEC = Codec.unboundedMap(PATTERN_CODEC, PermissionResult.CODEC)
            .xmap(map -> {
                Builder rules = CommandOverrideRules.builder();
                map.forEach(rules::add);
                return rules.build();
            }, rules -> {
                return Arrays.stream(rules.rules).collect(Collectors.toMap(rule -> rule.patterns, rule -> rule.result));
            });

    private final Rule[] rules;

    CommandOverrideRules(Rule[] commands) {
        this.rules = commands;
    }

    public static Builder builder() {
        return new Builder();
    }

    public PermissionResult test(MatchableCommand command) {
        for (Rule rule : this.rules) {
            PermissionResult result = rule.test(command);
            if (result.isDefinitive()) {
                return result;
            }
        }
        return PermissionResult.PASS;
    }

    @Override
    public String toString() {
        return Arrays.toString(this.rules);
    }

    public static class Builder {
        private final List<Rule> rules = new ArrayList<>();

        Builder() {
        }

        public Builder add(Pattern[] patterns, PermissionResult result) {
            this.rules.add(new Rule(patterns, result));
            return this;
        }

        public CommandOverrideRules build() {
            this.rules.sort(Comparator.comparingInt(Rule::size).reversed());
            Rule[] rules = this.rules.toArray(new Rule[0]);
            return new CommandOverrideRules(rules);
        }
    }

    private static final class Rule {
        private final Pattern[] patterns;
        private final PermissionResult result;

        Rule(Pattern[] patterns, PermissionResult result) {
            this.patterns = patterns;
            this.result = result;
        }

        PermissionResult test(MatchableCommand command) {
            if (this.result.isAllowed()) {
                return command.matchesAllow(this.patterns) ? this.result : PermissionResult.PASS;
            } else if (this.result.isDenied()) {
                return command.matchesDeny(this.patterns) ? this.result : PermissionResult.PASS;
            }
            return PermissionResult.PASS;
        }

        int size() {
            return this.patterns.length;
        }

        @Override
        public String toString() {
            return "\"" + Arrays.toString(this.patterns) + "\"=" + this.result;
        }
    }
}
