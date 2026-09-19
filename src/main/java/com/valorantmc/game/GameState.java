package com.valorantmc.game;

public enum GameState {
    WAITING,       // Lobby, waiting for players
    AGENT_SELECT,  // Players choose their agents
    BUY_PHASE,     // Buy phase before round
    ROUND_ACTIVE,  // Round is in progress
    ROUND_END,     // Brief pause between rounds
    GAME_OVER      // Game finished
}
