package com.cookingit.dicetactoe;

import com.cookingit.dicetactoe.firebase.GameManager;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class GameEngine {
    public enum GameState { ROLLING, PLACING, GAME_OVER }

    private String[][] board = new String[3][3];
    private String currentPlayer = "X";
    private String winner = null;
    private int[] dice = new int[5];
    private boolean diceRolled = false;
    private int rollsLeft = 3;
    private GameState gameState = GameState.ROLLING;
    private List<int[]> validPositions = new ArrayList<>();
    private String currentCombo = "";
    private boolean hintsVisible = true;
    private boolean[] keptDice = new boolean[5];
    private boolean waitingForConfirmation = false;

    private final Map<String, String> diceCombinations = new HashMap<String, String>() {{
        put("five_of_a_kind", "Any square");
        put("four_of_a_kind", "Any square");
        put("full_house", "Any corner or center square");
        put("straight", "Any square in middle row or middle column");
        put("three_of_a_kind", "Any square except the center");
        put("two_pair", "Any corner square");
        put("one_pair", "Any edge square (non-corner)");
        put("all_different", "Only the center square");
    }};

    // Position definitions
    private final List<int[]> corners = List.of(
            new int[]{0, 0}, new int[]{0, 2},
            new int[]{2, 0}, new int[]{2, 2}
    );

    private final List<int[]> edges = List.of(
            new int[]{0, 1}, new int[]{1, 0},
            new int[]{1, 2}, new int[]{2, 1}
    );

    private final List<int[]> center = List.of(new int[]{1, 1});

    public void rollDice() {
        if (!diceRolled) {
            // First roll
            for (int i = 0; i < 5; i++) {
                dice[i] = (int) (Math.random() * 6) + 1;
            }
            diceRolled = true;
        } else {
            // Subsequent rolls
            for (int i = 0; i < 5; i++) {
                if (!isDieKept(i)) {
                    dice[i] = (int) (Math.random() * 6) + 1;
                }
            }
        }
        rollsLeft--;
        currentCombo = getDiceCombination();

        if (rollsLeft == 0) {
            validPositions = getValidPositions();
            if (validPositions.isEmpty()) {
                showNoMovesDialog();
            } else {
                gameState = GameState.PLACING;
            }
        }
    }

    private String getDiceCombination() {
        Map<Integer, Integer> counts = new HashMap<>();
        for (int die : dice) {
            // Replace getOrDefault for API <24 compatibility
            Integer count = counts.get(die);
            if (count == null) {
                counts.put(die, 1);
            } else {
                counts.put(die, count + 1);
            }
        }

        if (counts.containsValue(5)) return "five_of_a_kind";
        if (counts.containsValue(4)) return "four_of_a_kind";

        // Check for full house
        boolean hasThree = false, hasTwo = false;
        for (int count : counts.values()) {
            if (count == 3) hasThree = true;
            if (count == 2) hasTwo = true;
        }
        if (hasThree && hasTwo) return "full_house";

        // Check for straight (existing code works)
        List<Integer> sorted = new ArrayList<>(counts.keySet());
        Collections.sort(sorted);
        int consecutive = 1;
        for (int i = 1; i < sorted.size(); i++) {
            if (sorted.get(i) == sorted.get(i-1) + 1) {
                if (++consecutive >= 4) return "straight";
            } else {
                consecutive = 1;
            }
        }

        if (counts.containsValue(3)) return "three_of_a_kind";

        // Replace stream filter for API <24 compatibility
        int pairCount = 0;
        for (int count : counts.values()) {
            if (count == 2) pairCount++;
        }
        if (pairCount >= 2) return "two_pair";

        if (counts.containsValue(2)) return "one_pair";
        return "all_different";
    }

    // private List<int[]> getValidPositions()
    public List<int[]> getValidPositions() {
        List<int[]> positions = new ArrayList<>();
        switch (currentCombo) {
            case "five_of_a_kind":
            case "four_of_a_kind":
                for (int i = 0; i < 3; i++) {
                    for (int j = 0; j < 3; j++) {
                        if (board[i][j] == null) positions.add(new int[]{i, j});
                    }
                }
                break;
            case "full_house":
                positions.addAll(checkAvailable(corners));
                positions.addAll(checkAvailable(center));
                break;
            case "straight":
                positions.addAll(checkAvailable(List.of(
                        new int[]{1, 0}, new int[]{1, 1}, new int[]{1, 2},
                        new int[]{0, 1}, new int[]{2, 1}
                )));
                break;
            case "three_of_a_kind":
                positions.addAll(checkAvailable(corners));
                positions.addAll(checkAvailable(edges));
                break;
            case "two_pair":
                positions.addAll(checkAvailable(corners));
                break;
            case "one_pair":
                positions.addAll(checkAvailable(edges));
                break;
            case "all_different":
                positions.addAll(checkAvailable(center));
                break;
        }
        return positions;
    }

    private List<int[]> checkAvailable(List<int[]> positions) {
        List<int[]> available = new ArrayList<>();
        for (int[] pos : positions) {
            if (board[pos[0]][pos[1]] == null) {
                available.add(pos);
            }
        }
        return available;
    }

    public void makeMove(int row, int col) {
        if (isValidMove(row, col)) {
            board[row][col] = currentPlayer;
            checkWinner();
            switchPlayer();
        }
    }

    private void checkWinner() {
        // Check rows and columns
        for (int i = 0; i < 3; i++) {
            if (checkLine(board[i][0], board[i][1], board[i][2])) return;
            if (checkLine(board[0][i], board[1][i], board[2][i])) return;
        }
        // Check diagonals
        if (checkLine(board[0][0], board[1][1], board[2][2])) return;
        if (checkLine(board[0][2], board[1][1], board[2][0])) return;

        // Check draw
        boolean isFull = true;
        for (String[] row : board) {
            for (String cell : row) {
                if (cell == null) {
                    isFull = false;
                    break;
                }
            }
        }
        if (isFull) winner = "Draw";
    }

    private boolean checkLine(String a, String b, String c) {
        if (a != null && a.equals(b) && a.equals(c)) {
            winner = a;
            return true;
        }
        return false;
    }

    // Update existing methods
    private void switchPlayer() {
        currentPlayer = currentPlayer.equals("X") ? "O" : "X";
        diceRolled = false;
        rollsLeft = 3;
        gameState = GameState.ROLLING;
        validPositions.clear();
        currentCombo = "";
        Arrays.fill(keptDice, false); // Reset dice keeping status
    }

    // Getters and setters
    public String getCurrentPlayer() { return currentPlayer; }
    public int getRollsLeft() { return rollsLeft; }
    public String getCurrentCombination() { return currentCombo; }
    public String getPlacementRule() { return diceCombinations.get(currentCombo); }
    public boolean hintsEnabled() { return hintsVisible; }
    public void toggleHints() { hintsVisible = !hintsVisible; }

    // Retrieves the value of a cell from the game board
    public String getCellValue(int row, int col) {
        return board[row][col]; // Returns "X", "O", or null
    }
    public int[] getDiceValues() { return dice; }
    //public boolean isDieKept(int index) { /* Implement dice keeping logic */ }
    // Implement dice keeping methods
    public boolean isDieKept(int index) {
        if (index < 0 || index >= 5) return false;
        return keptDice[index];
    }
    //public void setDieKeptStatus(int index, boolean kept) { /* Implement */ }
    public void setDieKeptStatus(int index, boolean kept) {
        if (index >= 0 && index < 5) {
            keptDice[index] = kept;
        }
    }

    //public boolean isValidMove(int row, int col) { /* Implement validation */ }
    // Implement move validation
    public boolean isValidMove(int row, int col) {
        // Check game state and boundaries
        if (gameState != GameState.PLACING) return false;
        if (row < 0 || row >= 3 || col < 0 || col >= 3) return false;

        // Check cell availability and valid positions
        return board[row][col] == null && isValidPosition(row, col);
    }
    private boolean isValidPosition(int row, int col) {
        for (int[] pos : validPositions) {
            if (pos[0] == row && pos[1] == col) return true;
        }
        return false;
    }
    private void showNoMovesDialog() {
        waitingForConfirmation = true;
        // Implement dialog callback
    }

    // Skips rolls when the "Skip Rolls" button is clicked
    public void skipRolls() {
        rollsLeft = 0;  // Set rolls left to 0
        validPositions = getValidPositions();
        if (validPositions.isEmpty()) {
            showNoMovesDialog();
        } else {
            gameState = GameState.PLACING;
        }
    }

    public boolean hasDiceRolled() {
        return diceRolled;
    }

    public void newGame() {
        board = new String[3][3];
        currentPlayer = "X";
        winner = null;
        dice = new int[5];
        diceRolled = false;
        rollsLeft = 3;
        gameState = GameState.ROLLING;
        validPositions.clear();
        currentCombo = "";
        Arrays.fill(keptDice, false); // Initialize dice keeping status
    }

    // Modify existing methods to return game state
    /*public GameState getGameState() {
        GameState state = new GameState();
        state.board = this.board;
        state.currentPlayer = this.currentPlayer;
        state.dice = this.dice;
        state.currentCombo = this.currentCombo;
        return state;
    }*/

    // ********** Firebase **********
    // Add to GameEngine class
    public void syncWithRemote(GameManager remoteState) {
        this.board = remoteState.board;
        this.currentPlayer = remoteState.currentPlayer;
        this.dice = remoteState.dice;
        this.currentCombo = remoteState.currentCombo;
        // Add other necessary field updates
    }
    /*public void newGame() {
        board = new String[3][3];
        currentPlayer = "X";
        winner = null;
        dice = new int[5];
        diceRolled = false;
        rollsLeft = 3;
        gameState = GameState.ROLLING;
        validPositions.clear();
        currentCombo = "";
    }*/
}
