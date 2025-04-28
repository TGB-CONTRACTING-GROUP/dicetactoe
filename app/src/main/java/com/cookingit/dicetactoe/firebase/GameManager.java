package com.cookingit.dicetactoe.firebase;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class GameManager {
    public Map<String, String> board;
    public String currentPlayer;
    public String status;
    public Object dice;  // Can be either Map or List
    public String currentCombo;
    public Map<String, String> players;

    public GameManager() {} // Required for Firebase

    public Map<String, String> getPlayers() {
        Map<String, String> formattedPlayers = new HashMap<>();
        if (players != null) {
            int playerIndex = 1;
            for (Map.Entry<String, String> entry : players.entrySet()) {
                formattedPlayers.put("player" + playerIndex, entry.getValue());
                playerIndex++;
                if (playerIndex > 2) break;
            }
        }
        return formattedPlayers;
    }

    public void setPlayers(Map<String, String> players) {
        this.players = new HashMap<>();
        int playerIndex = 1;
        for (Map.Entry<String, String> entry : players.entrySet()) {
            this.players.put("player" + playerIndex, entry.getValue());
            playerIndex++;
            if (playerIndex > 2) break;
        }
    }

    // Helper method to handle both dice formats
    public List<Integer> getDiceAsList() {
        if (dice instanceof List) {
            return (List<Integer>) dice;
        } else if (dice instanceof Map) {
            Map<String, Integer> diceMap = (Map<String, Integer>) dice;
            List<Integer> diceList = new ArrayList<>();
            for (int i = 0; i < 5; i++) {
                String key = String.valueOf(i);
                Integer value = diceMap.containsKey(key) ? diceMap.get(key) : 0;
                diceList.add(value);
            }
            return diceList;
        }
        return Arrays.asList(0, 0, 0, 0, 0); // Default
    }
}