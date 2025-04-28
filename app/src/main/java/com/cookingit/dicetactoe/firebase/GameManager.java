package com.cookingit.dicetactoe.firebase;

import android.util.Log;

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
    public Map<String, Object> playerTimestamps;
    public String leftPlayer;
    public Object createdAt;
    public int playerXScore;
    public int playerOScore;

    public GameManager() {} // Required for Firebase

    public Map<String, String> getPlayers() {
        if (players == null) {
            return new HashMap<>();
        }
        return players;
    }

    public void setPlayers(Map<String, String> players) {
        this.players = players;
    }

    public Map<String, Object> getPlayerTimestamps() {
        if (playerTimestamps == null) {
            return new HashMap<>();
        }
        return playerTimestamps;
    }

    public void setPlayerTimestamps(Map<String, Object> playerTimestamps) {
        this.playerTimestamps = playerTimestamps;
    }

    public String getLeftPlayer() {
        return leftPlayer;
    }

    public void setLeftPlayer(String leftPlayer) {
        this.leftPlayer = leftPlayer;
    }

    // Helper method to handle both dice formats
    public List<Integer> getDiceAsList() {
        try {
            if (dice == null) {
                return Arrays.asList(0, 0, 0, 0, 0); // Default
            }

            if (dice instanceof List) {
                List<?> diceList = (List<?>) dice;
                List<Integer> result = new ArrayList<>();
                for (Object item : diceList) {
                    if (item instanceof Long) {
                        result.add(((Long) item).intValue());
                    } else if (item instanceof Integer) {
                        result.add((Integer) item);
                    } else if (item instanceof Double) {
                        result.add(((Double) item).intValue());
                    } else {
                        result.add(0);
                    }
                }
                return result;
            } else if (dice instanceof Map) {
                Map<?, ?> diceMap = (Map<?, ?>) dice;
                List<Integer> diceList = new ArrayList<>();
                for (int i = 0; i < 5; i++) {
                    String key = String.valueOf(i);
                    Object value = diceMap.containsKey(key) ? diceMap.get(key) : 0;

                    // Handle different numeric types from Firebase
                    if (value instanceof Long) {
                        diceList.add(((Long) value).intValue());
                    } else if (value instanceof Integer) {
                        diceList.add((Integer) value);
                    } else if (value instanceof Double) {
                        diceList.add(((Double) value).intValue());
                    } else {
                        diceList.add(0);
                    }
                }
                return diceList;
            }
        } catch (Exception e) {
            Log.e("GameManager", "Error parsing dice data", e);
        }
        return Arrays.asList(0, 0, 0, 0, 0); // Default
    }

    // Helper method to handle both dice formats
//    public List<Integer> getDiceAsList() {
//        if (dice instanceof List) {
//            return (List<Integer>) dice;
//        } else if (dice instanceof Map) {
//            Map<String, Object> diceMap = (Map<String, Object>) dice;
//            List<Integer> diceList = new ArrayList<>();
//            for (int i = 0; i < 5; i++) {
//                String key = String.valueOf(i);
//                Object value = diceMap.containsKey(key) ? diceMap.get(key) : 0;
//
//                // Handle different numeric types from Firebase
//                if (value instanceof Long) {
//                    diceList.add(((Long) value).intValue());
//                } else if (value instanceof Integer) {
//                    diceList.add((Integer) value);
//                } else if (value instanceof Double) {
//                    diceList.add(((Double) value).intValue());
//                } else {
//                    diceList.add(0);
//                }
//            }
//            return diceList;
//        }
//        return Arrays.asList(0, 0, 0, 0, 0); // Default
//    }

}









//package com.cookingit.dicetactoe.firebase;
//
//import java.util.ArrayList;
//import java.util.Arrays;
//import java.util.HashMap;
//import java.util.List;
//import java.util.Map;
//
//public class GameManager {
//    public Map<String, String> board;
//    public String currentPlayer;
//    public String status;
//    public Object dice;  // Can be either Map or List
//    public String currentCombo;
//    public Map<String, String> players;
//
//    public GameManager() {} // Required for Firebase
//
//    public Map<String, String> getPlayers() {
//        Map<String, String> formattedPlayers = new HashMap<>();
//        if (players != null) {
//            int playerIndex = 1;
//            for (Map.Entry<String, String> entry : players.entrySet()) {
//                formattedPlayers.put("player" + playerIndex, entry.getValue());
//                playerIndex++;
//                if (playerIndex > 2) break;
//            }
//        }
//        return formattedPlayers;
//    }
//
//    public void setPlayers(Map<String, String> players) {
//        this.players = new HashMap<>();
//        int playerIndex = 1;
//        for (Map.Entry<String, String> entry : players.entrySet()) {
//            this.players.put("player" + playerIndex, entry.getValue());
//            playerIndex++;
//            if (playerIndex > 2) break;
//        }
//    }
//
//    // Helper method to handle both dice formats
//    public List<Integer> getDiceAsList() {
//        if (dice instanceof List) {
//            return (List<Integer>) dice;
//        } else if (dice instanceof Map) {
//            Map<String, Integer> diceMap = (Map<String, Integer>) dice;
//            List<Integer> diceList = new ArrayList<>();
//            for (int i = 0; i < 5; i++) {
//                String key = String.valueOf(i);
//                Integer value = diceMap.containsKey(key) ? diceMap.get(key) : 0;
//                diceList.add(value);
//            }
//            return diceList;
//        }
//        return Arrays.asList(0, 0, 0, 0, 0); // Default
//    }
//}