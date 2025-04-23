package com.cookingit.dicetactoe.firebase;

import java.util.List;
import java.util.Map;

public class GameManager {
    public Map<String, String> board;
    //public List<List<String>> board;
    //public String[][] board;
    public String currentPlayer;
    public String status;
    public List<Integer> dice;
    //public int[] dice;
    public String currentCombo;
    public Map<String, String> players;

    public GameManager() {} // Required for Firebase
}
