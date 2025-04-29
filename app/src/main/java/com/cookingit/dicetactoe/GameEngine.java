package com.cookingit.dicetactoe;

import android.util.Log;

import com.cookingit.dicetactoe.firebase.GameManager;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class GameEngine {
    public enum GameState { ROLLING, PLACING, GAME_OVER }

    private List<List<String>> board;
    //private String[][] board = new String[3][3];
    private String currentPlayer = "X";
    private String winner = null;
    private List<Integer> dice = new ArrayList<>(Arrays.asList(0, 0, 0, 0, 0));
    //private int[] dice = new int[5];
    private boolean diceRolled = false;
    private int rollsLeft = 3;
    private GameState gameState = GameState.ROLLING;
    private List<int[]> validPositions = new ArrayList<>();
    private String currentCombo = "";
    private boolean hintsVisible = true;
    private final boolean[] keptDice = new boolean[5];
    private int playerXScore = 0;
    private int playerOScore = 0;

    public GameEngine() {
        initializeBoard();
    }

    private void initializeBoard() {
        board = new ArrayList<>();
        for (int i = 0; i < 3; i++) {
            List<String> row = new ArrayList<>();
            for (int j = 0; j < 3; j++) {
                row.add(null);
            }
            board.add(row);
        }
    }

    public void setPlayerXScore(int score) {
        this.playerXScore = score;
    }

    public void setPlayerOScore(int score) {
        this.playerOScore = score;
    }

    private Map<String, String> boardToMap() {
        Map<String, String> boardMap = new HashMap<>();
        for (int i = 0; i < 3; i++) {
            for (int j = 0; j < 3; j++) {
                String value = board.get(i).get(j);
                if (value != null) { // Only store non-null values to save space
                    boardMap.put(i + "_" + j, value);
                }
            }
        }
        return boardMap;
    }

    private List<List<String>> mapToBoard(Map<String, String> boardMap) {
        List<List<String>> newBoard = new ArrayList<>();
        for (int i = 0; i < 3; i++) {
            List<String> row = new ArrayList<>();
            for (int j = 0; j < 3; j++) {
                row.add(boardMap != null ? boardMap.get(i + "_" + j) : null);
            }
            newBoard.add(row);
        }
        return newBoard;
    }

    private final Map<String, String> diceCombinations = new HashMap<String, String>() {{
        put("five_of_a_kind", "Any square");
        put("four_of_a_kind", "Any square");
        put("full_house", "Any corner or center square");
        put("straight", "Any middle row or column square");
        put("three_of_a_kind", "Any square except the center");
        put("two_pair", "Any corner square");
        put("one_pair", "Any edge square (non-corner)");
        put("all_different", "Only the center square");
    }};

    private final List<int[]> corners = List.of(
            new int[]{0, 0}, new int[]{0, 2},
            new int[]{2, 0}, new int[]{2, 2}
    );

    private final List<int[]> edges = List.of(
            new int[]{0, 1}, new int[]{1, 0},
            new int[]{1, 2}, new int[]{2, 1}
    );

    private final List<int[]> center = List.of(new int[]{1, 1});

    public boolean rollDice() {
        if (rollsLeft <= 0) {
            return false;
        }

        if (!diceRolled) {
            for (int i = 0; i < 5; i++) {
                dice.set(i, (int) (Math.random() * 6) + 1);
            }
            diceRolled = true;
        } else {
            for (int i = 0; i < 5; i++) {
                if (!isDieKept(i)) {
                    dice.set(i, (int) (Math.random() * 6) + 1);
                }
            }
        }
        rollsLeft--;
        currentCombo = getDiceCombination();

        if (rollsLeft == 0) {
            validPositions = getValidPositions();
            if (validPositions.isEmpty()) {
                return true;
            } else {
                gameState = GameState.PLACING;
            }
        }
        return false;
    }

    private String getDiceCombination() {
        Map<Integer, Integer> counts = new HashMap<>();
        for (Number die : dice) {
            int dieValue = die.intValue(); // Safely convert to int, whether Long or Integer
            Integer count = counts.get(dieValue);
            if (count == null) {
                counts.put(dieValue, 1);
            } else {
                counts.put(dieValue, count + 1);
            }
        }

        if (counts.containsValue(5)) return "five_of_a_kind";
        if (counts.containsValue(4)) return "four_of_a_kind";

        boolean hasThree = false, hasTwo = false;
        for (int count : counts.values()) {
            if (count == 3) hasThree = true;
            if (count == 2) hasTwo = true;
        }
        if (hasThree && hasTwo) return "full_house";

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

        int pairCount = 0;
        for (int count : counts.values()) {
            if (count == 2) pairCount++;
        }
        if (pairCount >= 2) return "two_pair";

        if (counts.containsValue(2)) return "one_pair";
        return "all_different";
    }

    public List<int[]> getValidPositions() {
        List<int[]> positions = new ArrayList<>();
        switch (currentCombo) {
            case "five_of_a_kind":
            case "four_of_a_kind":
                for (int i = 0; i < 3; i++) {
                    for (int j = 0; j < 3; j++) {
                        if (board.get(i).get(j) == null) positions.add(new int[]{i, j});
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
            if (board.get(pos[0]).get(pos[1]) == null) {
                available.add(pos);
            }
        }
        return available;
    }

    public void makeMove(int row, int col) {
        if (row == -1 && col == -1) {
            switchPlayer();
        } else if (isValidMove(row, col)) {
            board.get(row).set(col, currentPlayer);
            checkWinner();
            if (winner == null) {
                switchPlayer();
            }
        }
    }

    private void checkWinner() {
        for (int i = 0; i < 3; i++) {
            if (checkLine(board.get(i).get(0), board.get(i).get(1), board.get(i).get(2))) {
                gameState = GameState.GAME_OVER;
                incrementScore(winner);
                return;
            }
            if (checkLine(board.get(0).get(i), board.get(1).get(i), board.get(2).get(i))) {
                gameState = GameState.GAME_OVER;
                incrementScore(winner);
                return;
            }
        }
        if (checkLine(board.get(0).get(0), board.get(1).get(1), board.get(2).get(2))) {
            gameState = GameState.GAME_OVER;
            incrementScore(winner);
            return;
        }
        if (checkLine(board.get(0).get(2), board.get(1).get(1), board.get(2).get(0))) {
            gameState = GameState.GAME_OVER;
            incrementScore(winner);
            return;
        }

        boolean isFull = true;
        for (List<String> row : board) {
            for (String cell : row) {
                if (cell == null) {
                    isFull = false;
                    break;
                }
            }
        }
        if (isFull) {
            winner = "Draw";
            gameState = GameState.GAME_OVER;
        }
    }

    public void setWinner(String winner) {
        this.winner = winner;
    }

    private boolean checkLine(String a, String b, String c) {
        if (a != null && a.equals(b) && a.equals(c)) {
            winner = a;
            return true;
        }
        return false;
    }

    private void switchPlayer() {
        currentPlayer = currentPlayer.equals("X") ? "O" : "X";
        diceRolled = false;
        rollsLeft = 3;
        gameState = GameState.ROLLING;
        validPositions.clear();
        currentCombo = "";
        Arrays.fill(keptDice, false);
        dice = new ArrayList<>(Arrays.asList(0, 0, 0, 0, 0)); // Reset dice
    }

    public void syncWithRemote(GameManager remoteState) {
        if (remoteState.board == null) {
            remoteState.board = new HashMap<>();
            Log.w("GameEngine", "Remote board was null, initialized to empty map");
        }
        List<List<String>> newBoard = mapToBoard(remoteState.board);
        if (!isValidBoard(newBoard)) {
            Log.e("GameEngine", "Invalid board structure, resetting to empty board");
            newBoard = initializeEmptyBoard();
        }
        this.board = newBoard;

        // Get current dice values and kept status
        List<Integer> currentDiceValues = new ArrayList<>(this.dice);
        boolean[] currentKeptStatus = Arrays.copyOf(this.keptDice, this.keptDice.length);

        // Use the helper method from GameManager to get dice as a list
        List<Integer> remoteDice = remoteState.getDiceAsList();
        if (remoteDice != null && remoteDice.size() == 5) {
            // Only update dice values if the remote has actual values
            boolean hasNonZeroValues = false;
            for (Integer value : remoteDice) {
                if (value > 0) {
                    hasNonZeroValues = true;
                    break;
                }
            }

            if (hasNonZeroValues) {
                // Check if it's the same player's turn
                if (this.currentPlayer.equals(remoteState.currentPlayer)) {
                    // Preserve kept dice values
                    List<Integer> newDiceValues = new ArrayList<>(remoteDice);
                    for (int i = 0; i < 5; i++) {
                        if (currentKeptStatus[i] && i < currentDiceValues.size() && i < newDiceValues.size()) {
                            // Keep the current value for kept dice
                            newDiceValues.set(i, currentDiceValues.get(i));
                        }
                    }
                    this.dice = newDiceValues;
                } else {
                    // Different player's turn, reset kept status and use remote dice
                    this.dice = new ArrayList<>(remoteDice);
                    Arrays.fill(keptDice, false);
                }
            } else {
                // Remote has all zeros, use those and reset kept status
                this.dice = new ArrayList<>(remoteDice);
                Arrays.fill(keptDice, false);
            }
        } else {
            // Invalid remote dice, reset to defaults
            this.dice = new ArrayList<>(Arrays.asList(0, 0, 0, 0, 0));
            Arrays.fill(keptDice, false);
        }

        this.currentPlayer = remoteState.currentPlayer != null ? remoteState.currentPlayer : "X";
        this.currentCombo = remoteState.currentCombo != null ? remoteState.currentCombo : "";

        if (remoteState.status != null) {
            if ("game_over".equals(remoteState.status)) {
                this.gameState = GameState.GAME_OVER;
            } else if ("playing".equals(remoteState.status)) {
                this.gameState = this.rollsLeft > 0 ? GameState.ROLLING : GameState.PLACING;
            }
        }

        this.winner = checkWinnerAfterSync();
        validPositions = getValidPositions();
    }

    private List<List<String>> initializeEmptyBoard() {
        List<List<String>> newBoard = new ArrayList<>();
        for (int i = 0; i < 3; i++) {
            List<String> row = new ArrayList<>();
            for (int j = 0; j < 3; j++) {
                row.add(null);
            }
            newBoard.add(row);
        }
        return newBoard;
    }

    public Map<String, String> getBoardAsMap() {
        return boardToMap();
    }

    // Helper method to validate the board structure
    private boolean isValidBoard(List<List<String>> board) {
        if (board == null || board.size() != 3) {
            Log.e("GameEngine", "Invalid board: null or incorrect size");
            return false;
        }
        for (List<String> row : board) {
            if (row == null || row.size() != 3) {
                Log.e("GameEngine", "Invalid board: row is null or incorrect size");
                return false;
            }
        }
        return true;
    }

    private String checkWinnerAfterSync() {
        for (int i = 0; i < 3; i++) {
            if (checkLine(board.get(i).get(0), board.get(i).get(1), board.get(i).get(2))) return winner;
            if (checkLine(board.get(0).get(i), board.get(1).get(i), board.get(2).get(i))) return winner;
        }
        if (checkLine(board.get(0).get(0), board.get(1).get(1), board.get(2).get(2))) return winner;
        if (checkLine(board.get(0).get(2), board.get(1).get(1), board.get(2).get(0))) return winner;

        boolean isFull = true;
        for (List<String> row : board) {
            for (String cell : row) {
                if (cell == null) {
                    isFull = false;
                    break;
                }
            }
        }
        return isFull ? "Draw" : null;
    }

    public String getCurrentPlayer() { return currentPlayer; }
    public int getRollsLeft() { return rollsLeft; }
    public String getCurrentCombination() { return currentCombo; }
    public String getPlacementRule() { return diceCombinations.get(currentCombo); }
    public boolean hintsEnabled() { return hintsVisible; }
    public void toggleHints() { hintsVisible = !hintsVisible; }
    public String getCellValue(int row, int col) { return board.get(row).get(col); }
    public List<Integer> getDiceValues() { // Changed return type
        return new ArrayList<>(dice);
    }

    public boolean isDieKept(int index) {
        if (index < 0 || index >= 5) return false;
        return keptDice[index];
    }
    public void setDieKeptStatus(int index, boolean kept) {
        if (index >= 0 && index < 5) {
            keptDice[index] = kept;
        }
    }

    public boolean isValidMove(int row, int col) {
        if (gameState != GameState.PLACING) return false;
        if (row < 0 || row >= 3 || col < 0 || col >= 3) return false;
        return board.get(row).get(col) == null && isValidPosition(row, col);
    }

    private boolean isValidPosition(int row, int col) {
        for (int[] pos : validPositions) {
            if (pos[0] == row && pos[1] == col) return true;
        }
        return false;
    }
    public boolean skipRolls() {
        rollsLeft = 0;
        validPositions = getValidPositions();
        if (validPositions.isEmpty()) {
            return true;
        } else {
            gameState = GameState.PLACING;
        }
        return false;
    }
    public boolean hasDiceRolled() { return diceRolled; }
    public int getPlayerXScore() { return playerXScore; }
    public int getPlayerOScore() { return playerOScore; }
    public void incrementScore(String player) {
        if (player.equals("X")) playerXScore++;
        else if (player.equals("O")) playerOScore++;
    }

    public void newGame() {
        initializeBoard();
        currentPlayer = "X";
        winner = null;
        dice = new ArrayList<>(Arrays.asList(0, 0, 0, 0, 0));
        diceRolled = false;
        rollsLeft = 3;
        gameState = GameState.ROLLING;
        validPositions.clear();
        currentCombo = "";
        Arrays.fill(keptDice, false);

        // Don't reset scores here - that would reset the running score
        // If you want to reset scores, uncomment these lines:
        // playerXScore = 0;
        // playerOScore = 0;
    }

    public String getWinner() { return winner; }
    public GameState getGameState() { return gameState; }
}