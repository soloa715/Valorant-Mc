package com.valorantmc.managers;

import com.valorantmc.ValorantMC;
import com.valorantmc.agents.Agent;
import com.valorantmc.agents.impl.*;

import java.util.*;

public class AgentManager {

    private final ValorantMC plugin;
    private final Map<String, Agent> agentRegistry = new LinkedHashMap<>();

    public AgentManager(ValorantMC plugin) {
        this.plugin = plugin;
        registerAll();
    }

    private void registerAll() {
        register(new Jett());
        register(new Sage());
        register(new Reyna());
        register(new Phoenix());
        register(new Sova());
        register(new Raze());
        register(new Killjoy());
        register(new Cypher());
        register(new Omen());
        register(new Viper());
        register(new Brimstone());
        register(new Breach());
    }

    private void register(Agent agent) {
        agentRegistry.put(agent.getName().toLowerCase(), agent);
    }

    public Agent getAgent(String name) {
        return agentRegistry.get(name.toLowerCase());
    }

    public Collection<Agent> getAllAgents() {
        return agentRegistry.values();
    }

    public int getAgentCount() {
        return agentRegistry.size();
    }

    /** Create a fresh instance of an agent (each player gets their own) */
    public Agent createInstance(String name) {
        return switch (name.toLowerCase()) {
            case "jett"      -> new Jett();
            case "sage"      -> new Sage();
            case "reyna"     -> new Reyna();
            case "phoenix"   -> new Phoenix();
            case "sova"      -> new Sova();
            case "raze"      -> new Raze();
            case "killjoy"   -> new Killjoy();
            case "cypher"    -> new Cypher();
            case "omen"      -> new Omen();
            case "viper"     -> new Viper();
            case "brimstone" -> new Brimstone();
            case "breach"    -> new Breach();
            default          -> null;
        };
    }

    public List<String> getAgentNames() {
        return new ArrayList<>(agentRegistry.keySet());
    }

    public Agent getRandomAgent() {
        List<String> names = getAgentNames();
        if (names.isEmpty()) return null;
        return createInstance(names.get(new Random().nextInt(names.size())));
    }
}
